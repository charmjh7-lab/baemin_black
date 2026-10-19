package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.graphics.*
import android.view.*
import com.charmjh7.baeminblack.accessibility.DetectedShop

class BadgeOverlayController(private val context: Context, private val logger: com.charmjh7.baeminblack.diagnostics.DiagnosticLogger? = null,
    private val result: (Boolean, String) -> Unit = { _, _ -> }) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val pool = mutableListOf<BadgeView>()
    private val size = (22 * context.resources.displayMetrics.density).toInt()
    private var shown = emptyList<Rect>()
    fun update(shops: List<DetectedShop>, viewport: Rect, occluded: List<Rect>, trace: String = "") {
        val positions = shops.mapNotNull { shop ->
            logger?.event("badge", "POSITION_REQUEST", trace = trace)
            val placement = BadgePlacement.choose(shop, viewport, occluded, size, (4 * context.resources.displayMetrics.density).toInt())
            logger?.event("badge", "POSITION_RESULT", placement.area != null, if (placement.area == null) "NO_SAFE_SPACE" else "", trace,
                mapOf("candidates" to placement.candidates.toString(), "rejected" to placement.rejected.joinToString(","), "selected" to placement.area?.toShortString().orEmpty()))
            placement.area
        }.distinctBy { it.toShortString() }.take(30)
        if (positions == shown && pool.count { it.isAttachedToWindow } == positions.size) { logger?.event("badge", "MAINTAINED", trace = trace); return }
        hide("POSITIONS_CHANGED", trace)
        positions.forEachIndexed { index, rect ->
            val view = pool.getOrNull(index) ?: BadgeView(context).also(pool::add)
            val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top; title = "배민 블랙 목록 배지"
            }
            try {
                logger?.event("badge", "ADD_VIEW_CALLED", trace = trace); wm.addView(view, params)
                logger?.event("badge", "ADD_VIEW_SUCCESS", trace = trace); result(true, "")
            } catch (e: RuntimeException) { logger?.event("badge", "ADD_VIEW_FAILED", false, "WINDOW_MANAGER_EXCEPTION", trace, error = e); result(false, "WINDOW_MANAGER_EXCEPTION") }
        }
        shown = positions
    }
    fun hide(reason: String = "HIDDEN", trace: String = "") {
        pool.forEach { if (it.isAttachedToWindow) try { wm.removeViewImmediate(it); logger?.event("badge", "REMOVED", reason = reason, trace = trace) }
            catch (e: RuntimeException) { logger?.event("badge", "REMOVE_FAILED", false, "WINDOW_MANAGER_EXCEPTION", trace, error = e) } }
        shown = emptyList()
    }
    fun destroy() { hide(); pool.clear() }
    private class BadgeView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false }
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f; val r = width * 0.39f
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(c, c, r + 2, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = width * 0.13f; paint.strokeCap = Paint.Cap.ROUND; paint.color = Color.rgb(190, 36, 50)
            canvas.drawCircle(c, c, r, paint)
            val d = r * 0.7f; canvas.drawLine(c - d, c + d, c + d, c - d, paint)
        }
    }
}
