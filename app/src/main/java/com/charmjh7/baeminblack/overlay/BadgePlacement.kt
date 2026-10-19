package com.charmjh7.baeminblack.overlay

import android.graphics.Rect
import com.charmjh7.baeminblack.accessibility.DetectedShop

data class Placement(val area: Rect?, val rejected: List<String>, val candidates: Int)
object BadgePlacement {
    fun choose(shop: DetectedShop, viewport: Rect, occluded: List<Rect>, size: Int, gap: Int): Placement {
        val card = shop.cardBounds; val title = shop.titleBounds
        val candidates = mutableListOf(Rect(title.right + gap, title.top, title.right + gap + size, title.top + size),
            Rect(card.right - gap - size, card.top + gap, card.right - gap, card.top + gap + size))
        // A bounded grid samples remaining card space without moving an existing badge during scrolling.
        for (y in listOf(card.top + gap, title.bottom + gap, card.bottom - gap - size))
            for (x in listOf(card.right - gap - size, card.left + gap)) candidates += Rect(x, y, x + size, y + size)
        val rejected = mutableListOf<String>()
        for ((i, area) in candidates.withIndex()) {
            val reason = when {
                !viewport.contains(card) || !viewport.contains(area) -> "OFFSCREEN"
                !card.contains(area) -> "OUTSIDE_CARD"
                occluded.any { Rect.intersects(it, area) } -> "OCCLUDED_WINDOW"
                Rect.intersects(title, area) || shop.occupiedBounds.any { Rect.intersects(it, area) } -> "ORIGINAL_UI_OCCUPIED"
                else -> return Placement(area, rejected, candidates.size)
            }
            rejected += "$i:$reason"
        }
        return Placement(null, rejected, candidates.size)
    }
}
