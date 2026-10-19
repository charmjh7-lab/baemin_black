package com.charmjh7.baeminblack

import android.graphics.Rect
import com.charmjh7.baeminblack.accessibility.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class DetailStructureRegressionTest {
    private fun page(): NodeSnapshot = NodeSnapshot(listOf(
        ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true),
        ScreenNode(1,0,"처음 보는 치킨 강남점","",Rect(40,180,850,250),false,true,true),
        ScreenNode(2,0,"","",Rect(0,400,1080,1900),false,false,true,scrollable=true),
        ScreenNode(3,2,"대표 음식","",Rect(40,450,600,480),false,false,true),
        ScreenNode(4,2,"다른 음식","",Rect(40,850,600,880),false,false,true)
    ),Rect(0,0,1080,2000),1)
    @Test fun absentMenuReviewStringsCanStillHaveDetailStructure() {
        val report = ShopScreenDetector().analyze(page(),emptyList())
        assertTrue(report.screen is ShopScreen.Detail)
        assertEquals("0",report.metrics["detailClues"])
        val shop = (report.screen as ShopScreen.Detail).shop
        val binding = com.charmjh7.baeminblack.accessibility.BaeminAccessibilityService.Binding(shop,1,7,"trace",null)
        assertTrue(ActionTarget(binding.shop.identity,binding.window,binding.token).accepts(shop.identity,1,7))
        assertTrue(report.metrics.keys.any { it.startsWith("candidate.") })
    }
    @Test fun unregisteredNonHeadingTitleCanUseGeometryAndHierarchy() {
        val original = page()
        val page = original.copy(nodes=original.nodes.map { if (it.index==1) it.copy(heading=false) else it })
        assertTrue(ShopScreenDetector().detect(page,emptyList()) is ShopScreen.Detail)
    }
    @Test fun aSingleCandidateDoesNotMakeSearchOrBareHeaderADetail() {
        val original = page()
        val search = original.copy(nodes = original.nodes + ScreenNode(5,0,"검색","",Rect(40,60,850,120),true,false,true,className="android.widget.EditText"))
        assertEquals(ShopScreen.Unknown,ShopScreenDetector().detect(search,emptyList()))
        assertEquals(ShopScreen.Unknown,ShopScreenDetector().detect(original.copy(nodes=original.nodes.take(2)),emptyList()))
    }
    @Test fun competingTitlesAreRejected() {
        val original = page()
        val ambiguous = original.copy(nodes=original.nodes + ScreenNode(5,0,"다른 치킨 서초점","",Rect(40,270,850,340),false,true,true))
        assertEquals(ShopScreen.Unknown,ShopScreenDetector().detect(ambiguous,emptyList()))
    }
}
