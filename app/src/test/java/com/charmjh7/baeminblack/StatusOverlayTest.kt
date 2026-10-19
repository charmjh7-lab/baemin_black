package com.charmjh7.baeminblack

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger
import com.charmjh7.baeminblack.overlay.ServiceStatusOverlayController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class StatusOverlayTest {
    @Test fun windowManagerExceptionIsVisibleAndLogged() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val logger = DiagnosticLogger(app)
        val wm = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, _ ->
            if (method.name == "addView") throw WindowManager.BadTokenException("diagnostic test")
            null
        } as WindowManager
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? = if (name == WINDOW_SERVICE) wm else super.getSystemService(name)
        }
        var success: Boolean? = null; var reason = ""
        val status = ServiceStatusOverlayController(context, logger) { ok, why -> success = ok; reason = why }
        status.show("대기", "가게 정보 인식 안 됨", false, Rect(0,0,1080,2000), "failure-test", emptyList()) {}
        logger.refresh()
        assertEquals(false, success); assertEquals("WINDOW_MANAGER_EXCEPTION", reason)
        assertTrue(logger.view.value.events.any { it.traceId == "failure-test" && it.status == "ADD_VIEW_CALLED" })
        assertTrue(logger.view.value.events.any { it.status == "ADD_VIEW_FAILED" && it.stack.contains("BadTokenException") })
    }
}
