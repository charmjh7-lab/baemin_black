package com.charmjh7.baeminblack

import android.graphics.Rect
import com.charmjh7.baeminblack.accessibility.*
import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.overlay.BadgePlacement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class OverlayPolicyTest {
    @Test fun placementTriesTitleThenTopRightThenRemainingSpace() {
        val viewport = Rect(0,0,500,1000)
        val shop = DetectedShop(ShopIdentity("가게"),Rect(20,100,350,130),Rect(0,80,480,300))
        assertEquals(Rect(354,100,376,122),BadgePlacement.choose(shop,viewport,emptyList(),22,4).area)
        val fullTitle = shop.copy(titleBounds = Rect(20,120,470,150))
        assertEquals(Rect(454,84,476,106),BadgePlacement.choose(fullTitle,viewport,emptyList(),22,4).area)
        val occupied = fullTitle.copy(occupiedBounds = listOf(Rect(450,80,480,155)))
        assertNotNull(BadgePlacement.choose(occupied,viewport,emptyList(),22,4).area)
        assertTrue(BadgePlacement.choose(occupied,viewport,emptyList(),22,4).rejected.isNotEmpty())
    }
    @Test fun noSpaceAndOcclusionReturnReasons() {
        val card = Rect(0,100,480,300)
        val shop = DetectedShop(ShopIdentity("가게"),Rect(10,120,470,150),card,listOf(card))
        val result = BadgePlacement.choose(shop,Rect(0,0,500,1000),emptyList(),22,4)
        assertNull(result.area); assertTrue(result.rejected.any { it.contains("OCCUPIED") })
        val clipped = BadgePlacement.choose(shop,Rect(0,150,500,1000),emptyList(),22,4)
        assertTrue(clipped.rejected.all { it.contains("OFFSCREEN") })
    }
    @Test fun continuousContentCannotExtendMaximumDeadline() {
        val scheduler = AnalysisScheduler()
        assertEquals(85L,scheduler.deadline(0))
        assertEquals(165L,scheduler.deadline(80))
        assertEquals(245L,scheduler.deadline(160))
        assertEquals(250L,scheduler.deadline(240))
        assertEquals(250L,scheduler.deadline(1000))
        scheduler.started(); assertEquals(1085L,scheduler.deadline(1000))
    }
    @Test fun scrollAndTransitionInvalidateOldResults() {
        val state = ScrollBadgeState(); val original = state.generation
        state.scroll(100); assertFalse(state.mayShow(original,280))
        val scrolled = state.generation
        assertFalse(state.mayShow(scrolled,279)); assertTrue(state.mayShow(scrolled,280))
        state.invalidate(); assertFalse(state.mayShow(scrolled,1000))
    }
}
