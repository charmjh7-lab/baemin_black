package com.charmjh7.baeminblack.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DiagnosticExporter(private val context: Context, private val logger: DiagnosticLogger) {
    suspend fun export(uri: Uri, includeSnapshots: Boolean) = logger.serialized {
        val events = logger.eventsForExport()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val manifest = buildJsonObject {
            put("appId", context.packageName); put("versionName", info.versionName); put("versionCode", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
            put("android", Build.VERSION.SDK_INT); put("model", Build.MODEL); put("build", Build.DISPLAY)
            put("firstEvent", events.firstOrNull()?.time); put("lastEvent", events.lastOrNull()?.time)
            put("retentionDays", 7); put("maxBytes", 10 * 1024 * 1024); put("includesSnapshots", includeSnapshots)
            put("detailedUntilElapsed", logger.detailedUntil.value); put("memoContentIncluded", false)
        }
        val summary = buildJsonObject {
            put("events", events.size)
            put("stages", JsonObject(events.groupingBy { "${it.stage}/${it.status}/${it.success}" }.eachCount().mapValues { JsonPrimitive(it.value) }))
            put("reasons", JsonObject(events.filter { it.reason.isNotBlank() }.groupingBy { it.reason }.eachCount().mapValues { JsonPrimitive(it.value) }))
        }
        val temp = File.createTempFile("diagnostics-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
                entry("manifest.json", manifest.toString()); entry("summary.json", summary.toString())
                entry("events.jsonl", events.joinToString("\n", postfix = if (events.isEmpty()) "" else "\n") { Json.encodeToString(it) })
                entry("errors.jsonl", events.filter { !it.success || it.stack.isNotBlank() }.joinToString("\n") { Json.encodeToString(it) })
                if (includeSnapshots) File(logger.directory, "snapshots").listFiles().orEmpty().filter { it.extension == "json" }.forEach { entry("snapshots/${it.name}", it.readText()) }
            }
            ZipFile(temp).use { zip ->
                listOf("manifest.json", "events.jsonl", "summary.json", "errors.jsonl").forEach { check(zip.getEntry(it) != null) }
                zip.entries().asSequence().forEach { e -> zip.getInputStream(e).use { it.readBytes() } }
            }
            context.contentResolver.openOutputStream(uri, "wt").use { out -> checkNotNull(out); temp.inputStream().use { it.copyTo(out) } }
            // Reopen the actual document, not only the staging ZIP.
            val verify = File.createTempFile("diagnostic-check-", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri).use { input -> checkNotNull(input); verify.outputStream().use { input.copyTo(it) } }
                ZipFile(verify).use { zip -> check(zip.getEntry("manifest.json") != null); zip.entries().asSequence().forEach { e -> zip.getInputStream(e).use { it.readBytes() } } }
            } finally { verify.delete() }
        } finally { temp.delete() }
    }
}
