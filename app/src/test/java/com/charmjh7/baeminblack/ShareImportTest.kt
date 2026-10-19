package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.share.ShareLinkParser
import org.junit.Assert.*
import org.junit.Test

class ShareImportTest {
    @Test fun labeledAndPlainShareTextPopulateShopNameAndBranch() {
        val labeled = ShareLinkParser.parse("상호명: 맛있는국밥\nhttps://baemin.go.link/abc")
        assertEquals("맛있는국밥", labeled.identity.name)

        val plain = ShareLinkParser.parse("교촌치킨 강남점\nhttps://baemin.go.link/abc")
        assertEquals("교촌치킨", plain.identity.name)
        assertEquals("강남점", plain.identity.branch)

        val sentence = ShareLinkParser.parse("교촌치킨 강남점을 공유해요.\nhttps://baemin.go.link/abc")
        assertEquals("교촌치킨", sentence.identity.name)
        assertEquals("강남점", sentence.identity.branch)
    }

    @Test fun sharedTitleAndShopNameQueryPopulateName() {
        val subject = ShareLinkParser.parse("https://baemin.go.link/abc", "푸라닭 종로점")
        assertEquals("푸라닭", subject.identity.name)
        assertEquals("종로점", subject.identity.branch)

        val fromQuery = ShareLinkParser.parse("https://www.baemin.com/store?shopName=%EC%9D%BC%ED%92%88%EC%B9%98%ED%82%A8&shopNo=987")
        assertEquals("일품치킨", fromQuery.identity.name)
        assertEquals("987", fromQuery.identity.shopNo)
    }

    @Test fun publicPreviewOpenGraphTitleCanProvideStoreName() {
        val html = """<html><head><meta property="og:title" content="소풍&amp;피자 홍대점 - 배달의민족"><title>배달의민족</title></head></html>"""
        assertEquals("소풍&피자 홍대점", ShareLinkParser.nameFromHtml(html))
        val twitter = """<meta content='맛있는초밥 - 배민' name='twitter:title'>"""
        assertEquals("맛있는초밥", ShareLinkParser.nameFromHtml(twitter))
    }

    @Test fun genericBaeminPreviewAndMessagesNeverBecomeStoreName() {
        assertNull(ShareLinkParser.nameFromHtml("""<meta property="og:title" content="배달의민족 - 무료배달 배민클럽 내가 찾던 맛집 배민엔 다 있어요">"""))
        assertEquals("", ShareLinkParser.parse("https://baemin.go.link/abc").identity.name)
        assertEquals("", ShareLinkParser.parse("배민에서 확인해 보세요!\nhttps://baemin.go.link/abc").identity.name)
        assertNull(ShareLinkParser.nameFromUrl("https://baemin.com.attacker.test/?shopName=%EC%83%81%ED%98%B8"))
    }
}
