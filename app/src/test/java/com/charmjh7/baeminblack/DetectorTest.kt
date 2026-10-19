package com.charmjh7.baeminblack

import android.graphics.Rect
import com.charmjh7.baeminblack.accessibility.*
import com.charmjh7.baeminblack.data.Shop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class DetectorTest {
    @Test fun visibleCardUsesParentBoundsAndRejectsTruncatedOrClippedTrees() {
        val nodes = listOf(
            ScreenNode(0,-1,"","", Rect(0,0,1080,2000),false,false,true),
            ScreenNode(1,0,"","", Rect(20,200,1060,500),true,false,true),
            ScreenNode(2,1,"치킨 강남점","com.sampleapp:id/shop_name",Rect(60,240,600,300),false,false,true),
            ScreenNode(3,1,"배달 30분","",Rect(60,330,500,380),false,false,true)
        )
        val detector = ShopScreenDetector(); val snapshot = NodeSnapshot(nodes,Rect(0,0,1080,2000),1)
        val screen = detector.detect(snapshot,listOf(Shop(name="치킨",branch="강남점"))) as ShopScreen.Listing
        assertEquals(Rect(20,200,1060,500),screen.shops.single().cardBounds)
        assertEquals("강남점",screen.shops.single().identity.branch)
        assertEquals(ShopScreen.Unknown, detector.detect(snapshot.copy(truncated=true),emptyList()))
        assertEquals(ShopScreen.Unknown, detector.detect(snapshot.copy(viewport=Rect(0,250,1080,2000)),emptyList()))
    }
    @Test fun detailedPageNeedsOneUnambiguousHeadingAndDetailMarkers() {
        val nodes = listOf(
            ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true),
            ScreenNode(1,0,"맛집","com.sampleapp:id/shop_name",Rect(50,200,700,260),false,true,true),
            ScreenNode(2,0,"메뉴","",Rect(50,300,200,350),true,false,true),
            ScreenNode(3,0,"리뷰","",Rect(300,300,450,350),true,false,true)
        )
        assertTrue(ShopScreenDetector().detect(NodeSnapshot(nodes,Rect(0,0,1080,2000),1),emptyList()) is ShopScreen.Detail)
        assertEquals(ShopScreen.Unknown,ShopScreenDetector().detect(NodeSnapshot(nodes.dropLast(1),Rect(0,0,1080,2000),1),emptyList()))
    }
}
