package com.charmjh7.baeminblack.diagnostics

import android.content.Context
import android.os.SystemClock
import com.charmjh7.baeminblack.accessibility.NodeSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Serializable data class DiagnosticEvent(
    val time: String = ZonedDateTime.now().toString(), val elapsedRealtime: Long = SystemClock.elapsedRealtime(),
    val eventId: String = UUID.randomUUID().toString(), val sessionId: String, val traceId: String,
    val kind: String, val stage: String, val status: String, val success: Boolean,
    val reason: String = "", val durationMs: Long = 0, val details: Map<String, String> = emptyMap(), val stack: String = ""
)
internal fun DiagnosticEvent.occurrences(): Int = if (kind == "AGGREGATE") (details["repeatCount"] ?: details["count"])?.toIntOrNull() ?: 1 else 1
internal fun List<DiagnosticEvent>.stageCounts() = groupBy { "${it.stage}/${it.status}/${it.success}" }.mapValues { (_, events) -> events.sumOf { it.occurrences() } }
internal fun List<DiagnosticEvent>.reasonCounts() = filter { it.reason.isNotBlank() }.groupBy { it.reason }.mapValues { (_, events) -> events.sumOf { it.occurrences() } }
data class DiagnosticView(val events: List<DiagnosticEvent> = emptyList(), val bytes: Long = 0,
    val counts: Map<String, Int> = emptyMap(), val reasons: Map<String, Int> = emptyMap(), val snapshots: List<String> = emptyList())

/** Single bounded disk writer. Control messages provide an export/delete barrier. Never writes screen or memo text. */
class DiagnosticLogger(context: Context, private val maxBytes: Long = 10L * 1024 * 1024, private val retentionMs: Long = 7L * 86400000) {
    val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
    val sessionId = UUID.randomUUID().toString()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(256)
    private val dropped = AtomicLong()
    private val viewState = MutableStateFlow(DiagnosticView())
    val view = viewState.asStateFlow()
    val detailedUntil = MutableStateFlow(0L)
    @Volatile private var textConsent = false
    private var timeout: Job? = null
    private var flushJob: Job? = null
    private var lastSnapshot = 0L
    private data class Repeated(val first: DiagnosticEvent, var last: DiagnosticEvent, var count: Int)
    private val repeats = linkedMapOf<String, Repeated>()
    val storageError = MutableStateFlow<String?>(null)
    init { scope.launch {
        try { prune(); refreshNow() } catch (e: Exception) { storageError.value = e.javaClass.simpleName }
        for (task in queue) {
            try { task() } catch (e: Exception) { storageError.value = e.javaClass.simpleName }
        }
    } }
    fun trace(): String = UUID.randomUUID().toString()
    fun event(stage: String, status: String, success: Boolean = true, reason: String = "", trace: String = sessionId,
              details: Map<String, String> = emptyMap(), duration: Long = 0, error: Throwable? = null, kind: String = stage) {
        val item = DiagnosticEvent(sessionId = sessionId, traceId = trace, kind = kind, stage = stage, status = status,
            success = success, reason = reason, durationMs = duration, details = details,
            stack = error?.let { it.javaClass.name + "\n" + it.stackTrace.take(24).joinToString("\n") }.orEmpty())
        if (!queue.trySend {
            val lost = dropped.getAndSet(0)
            if (lost > 0) append(DiagnosticEvent(sessionId = sessionId, traceId = trace, kind = "AGGREGATE", stage = "logging",
                status = "EVENTS_COALESCED", success = true, details = mapOf("count" to lost.toString())))
            if (flushJob?.isActive != true) flushJob = scope.launch { delay(1000); queue.send { flushRepeats(); flushJob = null } }
            val key = "${item.stage}|${item.status}|${item.reason}|${item.traceId}|${item.details}"
            val previous = repeats[key]
            if (previous != null && item.elapsedRealtime - previous.first.elapsedRealtime < 1000) {
                previous.count++; previous.last = item
            } else {
                flushRepeats(expiredOnly = true); append(item); repeats[key] = Repeated(item, item, 0)
            }
        }.isSuccess) dropped.incrementAndGet()
    }
    private fun flushRepeats(expiredOnly: Boolean = false) {
        val selected = repeats.filterValues { !expiredOnly || SystemClock.elapsedRealtime() - it.first.elapsedRealtime >= 1000 }.keys.toList()
        selected.mapNotNull { repeats.remove(it) }.filter { it.count > 0 }.forEach { repeat ->
            append(repeat.last.copy(eventId = UUID.randomUUID().toString(), kind = "AGGREGATE",
                details = repeat.last.details + mapOf("repeatCount" to repeat.count.toString(), "firstTime" to repeat.first.time,
                    "lastTime" to repeat.last.time, "firstElapsed" to repeat.first.elapsedRealtime.toString(), "lastElapsed" to repeat.last.elapsedRealtime.toString())))
        }
    }
    private fun append(event: DiagnosticEvent) {
        val files = eventFiles()
        val current = files.lastOrNull()?.takeIf { it.length() < 256 * 1024 }
            ?: File(directory, "events-${System.currentTimeMillis()}-${UUID.randomUUID()}.jsonl")
        current.appendText(json.encodeToString(event) + "\n")
        prune()
    }
    fun startDetailed(includeText: Boolean) {
        textConsent = includeText; detailedUntil.value = SystemClock.elapsedRealtime() + 300000
        event("snapshot", "DETAILED_STARTED", details = mapOf("textConsent" to includeText.toString(), "durationSeconds" to "300"))
        timeout?.cancel(); timeout = scope.launch { delay(300000); stopDetailed() }
    }
    fun stopDetailed() { detailedUntil.value = 0; textConsent = false; timeout?.cancel(); event("snapshot", "DETAILED_STOPPED") }
    fun snapshot(snapshot: NodeSnapshot, trace: String, reason: String, packageName: String) {
        val now = SystemClock.elapsedRealtime()
        if (packageName != "com.sampleapp" || now >= detailedUntil.value || now - lastSnapshot < 3000) return
        lastSnapshot = now
        val include = textConsent
        val nodes = snapshot.nodes.map { n -> SnapshotNode(n.index, n.parent, n.resourceId, n.text.isNotBlank(), n.text.length,
            if (include) n.text.take(300) else null, n.bounds.toShortString(), n.clickable, n.visible, n.heading) }
        val data = SnapshotData(trace, reason, snapshot.windowId, include, nodes)
        if (!queue.trySend {
            File(directory, "snapshots").mkdirs()
            File(directory, "snapshots/${System.currentTimeMillis()}-$trace.json").writeText(json.encodeToString(data)); prune()
        }.isSuccess) dropped.incrementAndGet()
    }
    private fun eventFiles() = directory.listFiles().orEmpty().filter { it.name.endsWith(".jsonl") }.sortedBy { it.name }
    private fun files() = directory.walkTopDown().filter { it.isFile }.toList()
    private fun prune() {
        val cutoff = System.currentTimeMillis() - retentionMs
        files().filter { it.lastModified() < cutoff }.forEach { it.delete() }
        val remaining = files().sortedBy { it.lastModified() }; var bytes = remaining.sumOf { it.length() }
        for (f in remaining) { if (bytes <= maxBytes) break; val size = f.length(); if (f.delete()) bytes -= size }
    }
    private fun readEvents() = eventFiles().flatMap { file -> file.useLines { lines -> lines.mapNotNull {
        runCatching { json.decodeFromString<DiagnosticEvent>(it) }.getOrNull()
    }.toList() } }
    private fun refreshNow() {
        val events = readEvents()
        viewState.value = DiagnosticView(events.takeLast(2000), files().sumOf { it.length() },
            events.stageCounts(), events.reasonCounts(),
            File(directory, "snapshots").listFiles().orEmpty().map { it.name }.sortedDescending())
    }
    suspend fun <T> serialized(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        queue.send { try { result.complete(block()) } catch (e: Throwable) { result.completeExceptionally(e) } }
        return result.await()
    }
    suspend fun refresh() = serialized { flushRepeats(); prune(); refreshNow() }
    suspend fun clear() = serialized { repeats.clear(); files().forEach { check(it.delete()) }; viewState.value = DiagnosticView() }
    suspend fun deleteSnapshots() = serialized { File(directory, "snapshots").listFiles().orEmpty().forEach { check(it.delete()) }; refreshNow() }
    suspend fun snapshotText(name: String): String = serialized {
        require(name == File(name).name); File(directory, "snapshots/$name").readText()
    }
    suspend fun traceEvents(id: String): List<DiagnosticEvent> = serialized { flushRepeats(); readEvents().filter { it.traceId == id }.sortedBy { it.elapsedRealtime } }
    internal fun eventsForExport(): List<DiagnosticEvent> { flushRepeats(); return readEvents() }
}
@Serializable private data class SnapshotNode(val index: Int, val parent: Int, val resourceId: String, val hasText: Boolean,
    val textLength: Int, val text: String?, val bounds: String, val clickable: Boolean, val visible: Boolean, val heading: Boolean)
@Serializable private data class SnapshotData(val traceId: String, val reason: String, val windowId: Int, val containsText: Boolean, val nodes: List<SnapshotNode>)
