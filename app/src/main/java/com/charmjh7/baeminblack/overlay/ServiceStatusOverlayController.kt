package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger

/** Owns only the status window. Listing badges and detail controls have independent lifetimes. */
class ServiceStatusOverlayController(private val context: Context, private val logger: DiagnosticLogger,
    private val onResult: (Boolean, String) -> Unit) {
    data class Action(val label: String, val enabled: Boolean = true, val run: () -> Unit)
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var expanded = false
    private var signature = ""
    private var latestRender: (() -> Unit)? = null
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    fun show(label: String, description: String, minimized: Boolean, viewport: Rect, trace: String,
        actions: List<Action>, minimize: () -> Unit) {
        latestRender = { render(label, description, minimized, viewport, trace, actions, minimize) }
        render(label, description, minimized, viewport, trace, actions, minimize)
    }
    private fun render(label: String, description: String, minimized: Boolean, viewport: Rect, trace: String, actions: List<Action>, minimize: () -> Unit) {
        val key = "$label|$description|$minimized|$expanded|$viewport|$trace|${actions.map { it.label to it.enabled }}"
        if (key == signature && root?.isAttachedToWindow == true) return
        val layout = root ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.rgb(247,244,239)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.DKGRAY) }
        }.also { root = it }
        layout.removeAllViews()
        fun text(value: String, action: (() -> Unit)? = null, enabled: Boolean = true) {
            layout.addView(TextView(context).apply {
                text = value; textSize = 12f; setTextColor(if (enabled) Color.rgb(35,34,38) else Color.GRAY)
                setPadding(dp(10), dp(8), dp(10), dp(8)); minHeight = dp(44); gravity = Gravity.CENTER_VERTICAL
                isEnabled = enabled; contentDescription = value
                if (action != null) setOnClickListener { logger.event("status_overlay", "BUTTON_CLICK", trace = trace, details = mapOf("action" to value.take(40))); action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        text(if (expanded) "배민 블랙 · 닫기" else if (minimized) "블랙" else "블랙 · $label", {
            expanded = !expanded; signature = ""; latestRender?.invoke()
        })
        if (expanded) {
            text(description.take(180))
            actions.forEach { text(it.label, it.run, it.enabled) }
            text(if (minimized) "기본 크기" else "최소화", { expanded = false; minimize() })
        }
        val width = minOf(dp(if (expanded) 220 else if (minimized) 48 else 108), viewport.width() - dp(12)).coerceAtLeast(1)
        val params = WindowManager.LayoutParams(width, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; x = viewport.right - width - dp(6)
            y = viewport.top + dp(60); title = "배민 블랙 작동 상태"
        }
        try {
            if (layout.isAttachedToWindow) { wm.updateViewLayout(layout, params); logger.event("status_overlay", "UPDATED", trace = trace) }
            else { logger.event("status_overlay", "ADD_VIEW_CALLED", trace = trace); wm.addView(layout, params); logger.event("status_overlay", "ADD_VIEW_SUCCESS", trace = trace) }
            signature = key; onResult(true, "")
        } catch (e: RuntimeException) {
            logger.event("status_overlay", "ADD_VIEW_FAILED", false, "WINDOW_MANAGER_EXCEPTION", trace, error = e); onResult(false, "WINDOW_MANAGER_EXCEPTION")
        }
    }
    fun hide(reason: String) {
        root?.let { if (it.isAttachedToWindow) try { wm.removeViewImmediate(it); logger.event("status_overlay", "REMOVED", reason = reason) }
            catch (e: RuntimeException) { logger.event("status_overlay", "REMOVE_FAILED", false, "WINDOW_MANAGER_EXCEPTION", error = e) } }
        root = null; signature = ""; expanded = false; latestRender = null
    }
}
