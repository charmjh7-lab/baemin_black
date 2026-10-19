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
    @Test fun detailedPageAcceptsOneMarkerWithStrongTitle() {
        val nodes = listOf(
            ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true),
            ScreenNode(1,0,"맛집","com.sampleapp:id/shop_name",Rect(50,200,700,260),false,true,true),
            ScreenNode(2,0,"메뉴","",Rect(50,300,200,350),true,false,true),
            ScreenNode(3,0,"리뷰","",Rect(300,300,450,350),true,false,true)
        )
        assertTrue(ShopScreenDetector().detect(NodeSnapshot(nodes,Rect(0,0,1080,2000),1),emptyList()) is ShopScreen.Detail)
        assertTrue(ShopScreenDetector().detect(NodeSnapshot(nodes.dropLast(1),Rect(0,0,1080,2000),1),emptyList()) is ShopScreen.Detail)
    }
    @Test fun unknownNonHeadingWithoutResourceIdUsesContext() {
        val nodes = listOf(
            ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true),
            ScreenNode(1,0,"새로운 치킨 강남점","",Rect(40,200,800,270),false,false,true),
            ScreenNode(2,0,"리뷰 100개 · 배달 30분","",Rect(40,280,800,320),false,false,true),
            ScreenNode(3,0,"메뉴","",Rect(40,330,250,390),true,false,true),
            ScreenNode(4,0,"12,000원","",Rect(40,450,300,500),false,false,true))
        val screen = ShopScreenDetector().analyze(NodeSnapshot(nodes,Rect(0,0,1080,2000),1),emptyList())
        assertTrue(screen.screen is ShopScreen.Detail)
        assertEquals("강남점", (screen.screen as ShopScreen.Detail).shop.identity.branch)
    }
    @Test fun nonClickableCardWithMetadataIsRecognized() {
        val nodes = listOf(
            ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true),
            ScreenNode(1,0,"","",Rect(20,200,1060,500),false,false,true),
            ScreenNode(2,1,"새 가게","",Rect(60,240,600,300),false,false,true),
            ScreenNode(3,1,"배달 30분","",Rect(60,330,500,380),false,false,true))
        assertTrue(ShopScreenDetector().detect(NodeSnapshot(nodes,Rect(0,0,1080,2000),1),emptyList()) is ShopScreen.Listing)
    }
    @Test fun truncationAndAmbiguityHaveSpecificReasons() {
        val root = ScreenNode(0,-1,"","",Rect(0,0,1080,2000),false,false,true)
        val nodes = listOf(root,
            ScreenNode(1,0,"치킨 강남점","shop_name",Rect(40,150,800,210),false,true,true),
            ScreenNode(2,0,"치킨 서초점","shop_name",Rect(40,220,800,280),false,true,true),
            ScreenNode(3,0,"메뉴","",Rect(40,330,250,390),true,false,true))
        val detector = ShopScreenDetector(); val snapshot = NodeSnapshot(nodes,root.bounds,1)
        assertEquals("SNAPSHOT_TRUNCATED", detector.analyze(snapshot.copy(truncated=true),emptyList()).reason)
        assertEquals("AMBIGUOUS_SHOP_NAME", detector.analyze(snapshot,emptyList()).reason)
        assertEquals("NO_VISIBLE_NODES", detector.analyze(snapshot.copy(nodes=emptyList()),emptyList()).reason)
    }
}
