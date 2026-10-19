package com.charmjh7.baeminblack

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.diagnostics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=android.app.Application::class)
class DiagnosticRegressionTest {
    @Test fun exceptionMessagesCausesAndParentTraceArePreserved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val log = DiagnosticLogger(context)
        log.event("status_overlay","ADD_VIEW_FAILED",false,"WINDOW_MANAGER_EXCEPTION","wm",
            error=IllegalStateException("View has already been added",IllegalArgumentException("test cause")),parentTrace="screen")
        log.refresh(); val event=log.view.value.events.last { it.traceId=="wm" }
        assertEquals("View has already been added",event.exceptionMessage)
        assertEquals("java.lang.IllegalArgumentException",event.causeClass)
        assertEquals("test cause",event.causeMessage); assertEquals("screen",event.parentTraceId)
    }
    @Test fun oldJsonlStillLoadsAndRawDiagnosticPayloadRequiresSeparateExportChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir=File(context.filesDir,"diagnostics").apply { mkdirs() }
        File(dir,"events-0001.jsonl").writeText("""{"sessionId":"old","traceId":"old-trace","kind":"analysis","stage":"analysis","status":"STARTED","success":true}
""")
        val log=DiagnosticLogger(context); log.refresh()
        assertTrue(log.view.value.events.any { it.traceId=="old-trace" && it.exceptionMessage.isEmpty() })
        log.startDetailed(false); log.sensitive("share_payload","private",mapOf("text" to "PRIVATE_PAYLOAD")); log.refresh()
        assertTrue(log.view.value.snapshots.isEmpty())
        log.startDetailed(true); log.sensitive("share_payload","private",mapOf("text" to "PRIVATE_PAYLOAD")); log.refresh()
        assertEquals(1,log.view.value.snapshots.size)
        val zip=File(context.cacheDir,"safe-diagnostic.zip")
        DiagnosticExporter(context,log).export(Uri.fromFile(zip),false)
        ZipFile(zip).use { file ->
            assertTrue(file.entries().asSequence().none { it.name.startsWith("snapshots/") })
            assertFalse(file.getInputStream(file.getEntry("events.jsonl")).reader().readText().contains("PRIVATE_PAYLOAD"))
        }
        log.stopDetailed()
    }
}
