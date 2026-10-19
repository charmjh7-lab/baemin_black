package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.graphics.*
import android.view.*
import com.charmjh7.baeminblack.accessibility.DetectedShop

class BadgeOverlayController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val pool = mutableListOf<BadgeView>()
    private val size = (22 * context.resources.displayMetrics.density).toInt()
    fun update(shops: List<DetectedShop>, viewport: Rect, occluded: List<Rect>) {
        val positions = shops.mapNotNull { s ->
            // Place in free card space, never over the name. Fully contained cards only.
            val gap = (4 * context.resources.displayMetrics.density).toInt()
            val area = Rect(s.titleBounds.right + gap, s.titleBounds.top, s.titleBounds.right + gap + size, s.titleBounds.top + size)
            if (!viewport.contains(s.cardBounds) || !s.cardBounds.contains(area) || occluded.any { Rect.intersects(it, s.cardBounds) } || s.occupiedBounds.any { Rect.intersects(it, area) }) null else area
        }.distinctBy { it.toShortString() }.take(30)
        hide()
        positions.forEachIndexed { index, rect ->
            val view = pool.getOrNull(index) ?: BadgeView(context).also(pool::add)
            val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top
                title = "배민 블랙 목록 배지"; if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try { wm.addView(view, params) } catch (_: RuntimeException) { /* Window disappearing: next event retries. */ }
        }
    }
    fun hide() { pool.forEach { if (it.isAttachedToWindow) runCatching { wm.removeViewImmediate(it) } } }
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
