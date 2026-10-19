package com.charmjh7.baeminblack

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.accessibility.*
import com.charmjh7.baeminblack.diagnostics.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class DiagnosticsTest {
    private lateinit var context: Context
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); File(context.filesDir,"diagnostics").deleteRecursively() }
    @Test fun traceReasonPersistenceAndSafeExport() = runBlocking {
        val logger = DiagnosticLogger(context)
        logger.event("analysis", "START", trace = "trace-one")
        logger.event("analysis", "FAILED", false, "NO_SHOP_NAME_CANDIDATE", "trace-one", error = IllegalStateException("private memo must not appear"))
        logger.refresh()
        val events = logger.view.value.events
        assertEquals(2, events.size); assertTrue(events.all { it.traceId == "trace-one" && it.time.isNotBlank() && it.sessionId.isNotBlank() && it.eventId.isNotBlank() })
        assertFalse(events.last().stack.contains("private memo"))
        val restarted = DiagnosticLogger(context); restarted.refresh()
        assertEquals(2, restarted.view.value.events.size)
        val zip = File(context.cacheDir, "diagnostic-test.zip")
        DiagnosticExporter(context, logger).export(Uri.fromFile(zip), false)
        ZipFile(zip).use { file ->
            assertNotNull(file.getEntry("manifest.json")); assertNotNull(file.getEntry("summary.json")); assertNotNull(file.getEntry("errors.jsonl"))
            assertTrue(file.entries().asSequence().none { it.name.startsWith("snapshots/") })
            assertTrue(file.getInputStream(file.getEntry("events.jsonl")).reader().readText().contains("NO_SHOP_NAME_CANDIDATE"))
        }
    }
    @Test fun expiredAndOversizeLogsArePruned() = runBlocking {
        val directory = File(context.filesDir,"diagnostics").apply { mkdirs() }
        File(directory,"old.jsonl").apply { writeText("old"); setLastModified(System.currentTimeMillis() - 9L * 86400000) }
        File(directory,"oversize.jsonl").writeText("x".repeat(3000))
        val logger = DiagnosticLogger(context, maxBytes = 1000)
        logger.refresh(); assertTrue(logger.view.value.bytes <= 1000)
        assertFalse(File(directory,"old.jsonl").exists())
    }
    @Test fun repeatsAreAggregatedAndDetailedModeStops() = runBlocking {
        val logger = DiagnosticLogger(context)
        repeat(30) { logger.event("scheduler", "MERGED", trace = "one") }
        logger.refresh()
        assertTrue(logger.view.value.events.size < 30)
        assertTrue(logger.view.value.events.any { it.kind == "AGGREGATE" && it.details["repeatCount"] == "29" })
        logger.startDetailed(false); assertTrue(logger.detailedUntil.value > 0)
        logger.stopDetailed(); assertEquals(0L, logger.detailedUntil.value)
    }
    @Test fun detailedSnapshotsAreBaeminOnlyAndTextNeedsConsent() = runBlocking {
        val logger = DiagnosticLogger(context)
        val snapshot = NodeSnapshot(listOf(ScreenNode(0,-1,"sensitive address","title",Rect(0,0,50,20),false,true,true)),Rect(0,0,500,1000),1)
        logger.startDetailed(false)
        // Advance past initial snapshot rate limit.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(4))
        logger.snapshot(snapshot, "one", "NO_SHOP_NAME_CANDIDATE", "other.app")
        logger.refresh(); assertTrue(logger.view.value.snapshots.isEmpty())
        logger.snapshot(snapshot, "two", "NO_SHOP_NAME_CANDIDATE", "com.sampleapp")
        logger.refresh(); val name = logger.view.value.snapshots.single()
        assertFalse(logger.snapshotText(name).contains("sensitive address"))
        logger.deleteSnapshots(); assertTrue(logger.view.value.snapshots.isEmpty())
    }
    @Test fun statusDimensionsDoNotOverwriteEachOther() {
        val state = ServiceStatus()
        state.change { it.copy(service = StageStatus("SERVICE_CONNECTED"), screen = StageStatus("SHOP_RECOGNIZED", trace = "s")) }
        state.change { it.copy(overlay = StageStatus("OVERLAY_FAILED"), error = "WINDOW_MANAGER_EXCEPTION") }
        assertEquals("SHOP_RECOGNIZED", state.flow.value.screen.value)
        state.disconnect("UNBOUND"); assertEquals("SERVICE_DISABLED", state.flow.value.service.value)
        assertEquals("HIDDEN", state.flow.value.overlay.value)
    }
}
