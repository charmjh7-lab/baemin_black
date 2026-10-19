package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.charmjh7.baeminblack.accessibility.DetectedShop
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger

class DetailOverlayController(private val context: Context, private val logger: DiagnosticLogger,
    metadata: () -> Map<String, String> = { emptyMap() },
    private val onResult: (Boolean, String) -> Unit) {
    private val host = OverlayWindowHost(context, "detail_overlay", logger, metadata, onResult)
    private var current: LinearLayout? = null
    private var key = ""
    fun show(detected: DetectedShop, record: ShopWithNotes?, settings: OverlaySettings, viewport: Rect, occluded: List<Rect>, trace: String,
        block: () -> Unit, memo: () -> Unit) {
        val blocked = record?.shop?.status == ShopStatus.BLOCKED
        if (!settings.memo && !(blocked && settings.warnings)) { hide("SETTING_DISABLED"); logger.event("detail_overlay", "SKIPPED", reason = "SETTING_DISABLED", trace = trace); return }
        val newKey = "$trace|${record?.shop?.status}|${settings.memo}|${settings.warnings}"
        if (newKey == key && host.registered) return
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
        val width = minOf(dp(200), viewport.width() - dp(24)); val height = dp(44)
        val candidates = (0..4).map { offset -> val top = maxOf(viewport.top + dp(112), detected.titleBounds.bottom + dp(8)) + offset * dp(48)
            Rect(viewport.right - width - dp(12), top, viewport.right - dp(12), top + height) }
        val position = candidates.firstOrNull { area -> viewport.contains(area) && area.bottom < viewport.top + viewport.height() * .65 &&
            (occluded + detected.occupiedBounds).none { Rect.intersects(it, area) } }
        logger.event("detail_overlay", "POSITION_RESULT", position != null, if (position == null) "NO_SAFE_SPACE" else "", trace,
            mapOf("candidates" to candidates.size.toString(), "selected" to position?.toShortString().orEmpty()))
        if (position == null) { onResult(false, "NO_SAFE_SPACE"); return }
        val panel = (current ?: LinearLayout(context)).apply {
            removeAllViews()
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(if (blocked) Color.rgb(255,235,237) else Color.rgb(247,244,239)); cornerRadius = dp(10).toFloat() }
        }
        fun action(label: String, run: () -> Unit) { panel.addView(TextView(context).apply {
            text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.rgb(35,34,38)); contentDescription = label
            setOnClickListener { logger.event("detail_overlay", "BUTTON_CLICK", trace = trace, details = mapOf("action" to label)); run() }
        }, LinearLayout.LayoutParams(0, -1, 1f)) }
        action(if (blocked) "블랙 해제" else "블랙 등록", block)
        if (settings.memo) action("메모 작성", memo)
        val params = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; x = position.left; y = position.top; title = "배민 블랙 빠른 기록"
        }
        current = panel
        if (host.present(panel, params, trace)) key = newKey
    }
    fun hide(reason: String = "HIDDEN", trace: String = "") {
        if (!(if (trace.isBlank()) host.hide(reason) else host.hide(reason, trace))) return
        current = null; key = ""
    }
}
