package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.matching.*
import org.junit.Assert.*
import org.junit.Test

class IdentityRegressionTest {
    @Test fun completeTitleAndSeparatedBranchShareCanonicalComparison() {
        val shop = Shop(name="교촌 치킨",branch="강남점",status=ShopStatus.BLOCKED)
        for (title in listOf("교촌치킨 강남점","교촌 치킨 · 강남점","교촌치킨(강남점)","교촌치킨강남점"))
            assertEquals(title,shop,ShopMatcher.match(ShopIdentity(title),listOf(shop)))
        val fullSaved = shop.copy(name="교촌치킨 강남점",branch="")
        assertEquals(fullSaved,ShopMatcher.match(ShopIdentity("교촌 치킨","강남점"),listOf(fullSaved)))
    }
    @Test fun aliasesAndBranchAndIdConflictsStayStrict() {
        val a = Shop(name="가게",branch="강남점",shopNo="1",aliases=listOf("별칭 · 강남점"))
        val b = Shop(name="가게",branch="서초점",shopNo="2")
        assertEquals(a,ShopMatcher.match(ShopIdentity("별칭 강남점"),listOf(a,b)))
        assertNull(ShopMatcher.match(ShopIdentity("가게"),listOf(a,b)))
        assertNull(ShopMatcher.match(ShopIdentity("가게","다른점"),listOf(a,b)))
        assertNull(ShopMatcher.match(ShopIdentity("가게","강남점","2-no"),listOf(a,b)))
        assertNull(ShopMatcher.match(ShopIdentity("가"),listOf(a,b)))
        assertEquals("SHOP_ID_MISMATCH",ShopMatcher.explain(ShopIdentity("가게","강남점","3"),listOf(a,b)).reason)
        assertTrue(ShopMatcher.explain(ShopIdentity("가게","강남점"),listOf(a,b)).details["comparisons"]!!.contains("branch="))
    }
}
