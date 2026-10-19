package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.share.RecentBaeminDetail
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentBaeminDetailTest {
    @Test fun consumesRecentDetailOnlyOnce() {
        RecentBaeminDetail.clear()
        assertNull(RecentBaeminDetail.consumeFresh())
        val shop = ShopIdentity("교촌치킨", "강남점", "12345")
        RecentBaeminDetail.remember(shop)
        assertEquals(shop, RecentBaeminDetail.consumeFresh())
        assertNull(RecentBaeminDetail.consumeFresh())
    }

    @Test fun clearPreventsOldDetailFromBeingUsed() {
        RecentBaeminDetail.remember(ShopIdentity("옛 가게"))
        RecentBaeminDetail.clear()
        assertNull(RecentBaeminDetail.consumeFresh())
        RecentBaeminDetail.remember(ShopIdentity(""))
        assertNull(RecentBaeminDetail.consumeFresh())
    }
}
