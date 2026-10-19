package com.charmjh7.baeminblack

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger
import com.charmjh7.baeminblack.overlay.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class WindowRegistrationTest {
    private class Windows(val app: Context) {
        var adds = 0; var updates = 0; var removes = 0; var failNext = false
        val registered: MutableSet<View> = Collections.newSetFromMap(IdentityHashMap())
        val wm = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            val view = args?.firstOrNull() as? View
            when (method.name) {
                "addView" -> { adds++; if (failNext) { failNext = false; throw IllegalStateException("View has already been added") }; check(registered.add(view!!)) { "Duplicate add" } }
                "updateViewLayout" -> { updates++; check(view in registered) }
                "removeViewImmediate" -> { removes++; if (!registered.remove(view)) throw IllegalArgumentException("View not attached to window manager") }
            }; null
        } as WindowManager
        val context = object : ContextWrapper(app) { override fun getSystemService(name: String): Any? = if (name == WINDOW_SERVICE) wm else super.getSystemService(name) }
    }
    @Test fun statusUpdatesBeforeAttachmentAndRemovesRegisteredUnattachedView() {
        val app = ApplicationProvider.getApplicationContext<Context>(); val w = Windows(app); val logger = DiagnosticLogger(app)
        val status = ServiceStatusOverlayController(w.context, logger) { _, _ -> }
        repeat(20) { status.show("상태$it", "정보", false, Rect(0,0,1080,2000), "trace", emptyList()) {} }
        assertFalse(w.registered.single().isAttachedToWindow)
        assertEquals(1, w.adds); assertEquals(19, w.updates)
        status.hide("TARGET_INACTIVE"); assertTrue(w.registered.isEmpty()); assertEquals(1,w.removes)
        status.show("복귀", "정보", false, Rect(0,0,1080,2000), "new", emptyList()) {}
        assertEquals(2, w.adds); assertEquals(1, w.registered.size)
    }
    @Test fun failureLogsMessageOperationAndRecovers() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>(); val w = Windows(app); val logger = DiagnosticLogger(app); w.failNext = true
        val host = OverlayWindowHost(w.context,"status_overlay",logger,{ mapOf("foregroundPackage" to "com.sampleapp", "foregroundWindow" to "4") }) { _, _ -> }
        val view = View(w.context); val params = WindowManager.LayoutParams()
        assertFalse(host.present(view, params, "wm-trace"))
        assertFalse(host.registered)
        assertTrue(host.present(view, params, "wm-trace2")); assertEquals(1, w.registered.size)
        logger.refresh()
        val error = logger.view.value.events.first { it.traceId == "wm-trace" && it.status == "ADD_VIEW_FAILED" }
        assertEquals("java.lang.IllegalStateException",error.exceptionClass)
        assertEquals("View has already been added",error.exceptionMessage)
        assertEquals("addView",error.details["operation"]); assertEquals("ADDING",error.details["registration"])
        assertEquals("false",error.details["attached"]); assertEquals("4",error.details["foregroundWindow"])
        assertTrue(error.stack.isNotBlank())
    }
    @Test fun detailAndBadgeHostsAlsoRespectRegistrationBeforeAttach() {
        val app = ApplicationProvider.getApplicationContext<Context>(); val w = Windows(app); val logger = DiagnosticLogger(app)
        for (kind in listOf("detail_overlay","badge")) {
            val host = OverlayWindowHost(w.context,kind,logger)
            val view = View(w.context); val params = WindowManager.LayoutParams()
            assertTrue(host.present(view,params,kind)); assertTrue(host.present(view,params,kind))
            assertTrue(host.hide("TARGET_INACTIVE")); assertFalse(host.registered)
        }
        assertEquals(2,w.adds); assertEquals(2,w.updates); assertTrue(w.registered.isEmpty())
    }
}
