package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.matching.*
import com.charmjh7.baeminblack.share.ShareLinkParser
import org.junit.Assert.*
import org.junit.Test

class IdentityTest {
    @Test fun parseSupportedParametersAndNestedDeepLink() {
        for (key in listOf("shopDetail_shopNo", "shopNo", "shopNumber")) {
            assertEquals("12345", ShareLinkParser.parse("가게명: 맛있는집\nhttps://www.baemin.com/shopDetail?$key=12345").identity.shopNo)
        }
        assertEquals("78", ShareLinkParser.number("https://baemin.go.link/x?deep_link_value=https%3A%2F%2Fwww.baemin.com%2FshopDetail%3FshopNo%3D78"))
    }
    @Test fun unknownAndUnsafeLinksNeverBecomeIdentity() {
        listOf("http://www.baemin.com/shopDetail?shopNo=1", "https://baemin.com.attacker.test/?shopNo=1", "https://baemin.com@attacker.test/?shopNo=1", "https://www.baemin.com:8443/?shopNo=1").forEach { assertFalse(ShareLinkParser.allowed(it)) }
        assertNull(ShareLinkParser.parse("https://baemin.go.link/unknown").identity.shopNo)
        assertNull(ShareLinkParser.number("https://www.baemin.com/shopDetail?shopNo=1&shopNumber=2"))
        assertNull(ShareLinkParser.parse("https://www.baemin.com/?shopNo=1 https://www.baemin.com/?shopNo=2").identity.shopNo)
        assertEquals("이름과 링크 원문", ShareLinkParser.parse("이름과 링크 원문").original)
    }
    @Test fun spacesPreserveDifferentNames() {
        assertEquals("my 치킨", ShopMatcher.normalize("  MY ·   치킨 "))
        assertNotEquals(ShopMatcher.normalize("가나 다"), ShopMatcher.normalize("가 나다"))
    }
    @Test fun exactNameBranchAndAliasesOnly() {
        val a = Shop(name = "MY 치킨", branch = "강남점", aliases = listOf("내치킨 강남"), shopNo = "10")
        val b = Shop(name = "MY 치킨", branch = "서초점", shopNo = "20")
        assertEquals(a, ShopMatcher.match(ShopIdentity(" My · 치킨 ", "강남점"), listOf(a, b)))
        assertEquals(a, ShopMatcher.match(ShopIdentity("MY 치킨 강남점"), listOf(a, b)))
        assertEquals(a, ShopMatcher.match(ShopIdentity("내치킨 강남"), listOf(a, b)))
        assertEquals(a, ShopMatcher.match(ShopIdentity("changed name", shopNo = "10"), listOf(a, b)))
        assertNull(ShopMatcher.match(ShopIdentity("MY 치킨"), listOf(a, b)))
        assertNull(ShopMatcher.match(ShopIdentity("MY 치킨", "강남점", "999"), listOf(a, b)))
        assertNull(ShopMatcher.match(ShopIdentity("MY"), listOf(a)))
        assertNull(ShopMatcher.match(ShopIdentity("내치킨 강남"), listOf(a, b.copy(aliases = a.aliases))))
        assertNull(ShopMatcher.match(ShopIdentity("MY 치킨", "강남점"), listOf(a, a.copy(id = "duplicate"))))
    }
}
