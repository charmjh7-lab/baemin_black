package com.charmjh7.baeminblack.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.charmjh7.baeminblack.MainActivity
import com.charmjh7.baeminblack.accessibility.DetectedShop
import com.charmjh7.baeminblack.data.*

class DetailOverlayController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var current: TextView? = null
    fun show(detected: DetectedShop, record: ShopWithNotes?, settings: OverlaySettings, viewport: Rect, occluded: List<Rect>) {
        hide()
        val blocked = record?.shop?.status == ShopStatus.BLOCKED
        val warning = blocked && settings.warnings
        if (!warning && !settings.memo) return
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val width = minOf(dp(if (warning) 250 else 88), viewport.width() - dp(24)); val height = dp(44)
        if (width < dp(64)) return
        val startY = maxOf(viewport.top + dp(56), detected.titleBounds.bottom + dp(8))
        val position = (0..3).map { offset ->
            val top = startY + offset * dp(48)
            Rect(viewport.right - width - dp(12), top, viewport.right - dp(12), top + height)
        }.firstOrNull { area -> viewport.contains(area) && area.bottom < viewport.top + viewport.height() * 0.6 && (occluded + detected.occupiedBounds).none { Rect.intersects(it, area) } } ?: return
        val text = TextView(context).apply {
            text = if (warning) "⛔ 블랙리스트 · 기록 ${record?.notes?.size ?: 0}건" else if (!record?.notes.isNullOrEmpty()) "메모 ●" else "메모 +"
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(if (warning) Color.rgb(135, 17, 35) else Color.rgb(35, 34, 38))
            background = GradientDrawable().apply { setColor(if (warning) Color.rgb(255, 235, 237) else Color.rgb(247, 244, 239)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.rgb(200, 190, 190)) }
            contentDescription = if (warning) "개인 블랙리스트 기록 열기" else "가게 메모 열기"
            setOnClickListener {
                val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (record != null) intent.putExtra("shop_id", record.shop.id)
                else { intent.putExtra("shop_name", detected.identity.name); intent.putExtra("shop_branch", detected.identity.branch); intent.putExtra("shop_no", detected.identity.shopNo) }
                runCatching { context.startActivity(intent) }; hide()
            }
        }
        val params = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; x = position.left; y = position.top; title = "배민 블랙 가게 기록"
        }
        try { wm.addView(text, params); current = text } catch (_: RuntimeException) { }
    }
    fun hide() { current?.let { if (it.isAttachedToWindow) runCatching { wm.removeViewImmediate(it) } }; current = null }
}
