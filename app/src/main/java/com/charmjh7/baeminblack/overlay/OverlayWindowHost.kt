package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger

/** Registration starts before addView returns; attachment is a separate, asynchronous Android state. */
class OverlayWindowHost(context: Context, private val kind: String, private val logger: DiagnosticLogger?,
    private val metadata: () -> Map<String, String> = { emptyMap() },
    private val result: (Boolean, String) -> Unit = { _, _ -> }) {
    enum class Registration { UNREGISTERED, ADDING, REGISTERED, REMOVING }
    var registration = Registration.UNREGISTERED; private set
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var latestTrace = ""
    val registered get() = registration != Registration.UNREGISTERED
    private fun details(operation: String, candidate: View? = view) = metadata() + mapOf(
        "overlayKind" to kind, "operation" to operation, "registration" to registration.name,
        "attached" to (candidate?.isAttachedToWindow == true).toString())
    fun present(candidate: View, params: WindowManager.LayoutParams, trace: String): Boolean {
        latestTrace = trace
        if (registered && view !== candidate && !hide("REPLACE_VIEW", trace)) return false
        if (registration == Registration.ADDING || registration == Registration.REMOVING) {
            logger?.event(kind, "UPDATE_DEFERRED", reason = "REGISTRATION_PENDING", trace = trace, details = details("updateViewLayout")); return false
        }
        val operation = if (registered) "updateViewLayout" else "addView"
        try {
            if (registered) {
                logger?.event(kind, "UPDATE_VIEW_CALLED", trace = trace, details = details(operation))
                wm.updateViewLayout(candidate, params)
                logger?.event(kind, "UPDATED", trace = trace, details = details(operation))
            } else {
                view = candidate; registration = Registration.ADDING
                logger?.event(kind, "ADD_VIEW_CALLED", trace = trace, details = details(operation))
                wm.addView(candidate, params); registration = Registration.REGISTERED
                logger?.event(kind, "ADD_VIEW_SUCCESS", trace = trace, details = details(operation))
            }
            result(true, ""); return true
        } catch (e: RuntimeException) {
            logger?.event(kind, if (operation == "addView") "ADD_VIEW_FAILED" else "UPDATE_VIEW_FAILED", false,
                "WINDOW_MANAGER_EXCEPTION", trace, details(operation, candidate), error = e)
            // Roll back even when Android has registered the view but has not attached it yet.
            hide(if (operation == "addView") "ADD_ROLLBACK" else "UPDATE_ROLLBACK", trace)
            result(false, "WINDOW_MANAGER_EXCEPTION"); return false
        }
    }
    fun hide(reason: String, trace: String = latestTrace): Boolean {
        val current = view ?: return true
        if (!registered) { view = null; return true }
        if (registration == Registration.REMOVING) return false
        registration = Registration.REMOVING
        logger?.event(kind, "REMOVE_VIEW_CALLED", reason = reason, trace = trace, details = details("removeViewImmediate"))
        try { wm.removeViewImmediate(current) }
        catch (e: IllegalArgumentException) {
            // Android already removed this window. Clear our registration so re-entry can add a new one.
            logger?.event(kind, "ALREADY_REMOVED", reason = "VIEW_NOT_REGISTERED", trace = trace, details = details("removeViewImmediate"), error = e)
        } catch (e: RuntimeException) {
            registration = Registration.REGISTERED
            logger?.event(kind, "REMOVE_FAILED", false, "WINDOW_MANAGER_EXCEPTION", trace, details("removeViewImmediate"), error = e)
            result(false, "WINDOW_MANAGER_EXCEPTION"); return false
        }
        registration = Registration.UNREGISTERED; view = null
        logger?.event(kind, "REMOVED", reason = reason, trace = trace, details = details("removeViewImmediate", current))
        result(true, "HIDDEN"); return true
    }
}
