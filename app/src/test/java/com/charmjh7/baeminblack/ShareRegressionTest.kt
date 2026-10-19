package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.share.*
import com.charmjh7.baeminblack.matching.*
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34], application=android.app.Application::class)
class ShareRegressionTest {
    @Test fun titleTextAndUrlSourcesAreMergedWithoutDiscardingFields() {
        val text = ShareLinkParser.parse("[배달의민족] 교촌치킨 강남점\n배달의민족\nhttps://baemin.go.link/a?shopNo=12")
        assertEquals(ShopIdentity("교촌치킨","강남점","12"),text.identity)
        val title = ShareLinkParser.parse("https://baemin.go.link/a?shopNo=12", "", "교촌치킨 강남점")
        assertEquals(text.identity,title.identity); assertEquals("EXTRA_TITLE",title.sources["name"])
        assertTrue(ShareLinkParser.parse("https://baemin.go.link/a").identity.name.isBlank())
        val noText = ShareLinkParser.parse("", "교촌치킨 강남점")
        assertEquals("교촌치킨",noText.identity.name)
    }
    @Test fun departureDoesNotDeleteHandoffAndItIsConsumedOnce() {
        RecentBaeminDetail.clear()
        val shop = ShopIdentity("교촌치킨","강남점","1")
        RecentBaeminDetail.remember(shop,"session","ACCESSIBILITY_DETAIL",4,"parent-trace")
        RecentBaeminDetail.leave()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(45))
        val captured = RecentBaeminDetail.consume()
        assertEquals(shop,captured.capture?.identity); assertEquals("parent-trace",captured.capture?.traceId)
        assertEquals("RECENT_DETAIL_AVAILABLE",captured.reason)
        assertNull(RecentBaeminDetail.consume().capture)
    }
    @Test fun expiryAndNewShopReplacementProtectAgainstStaleHandoff() {
        RecentBaeminDetail.clear()
        RecentBaeminDetail.remember(ShopIdentity("이전 가게"),"old")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(121))
        assertEquals("RECENT_DETAIL_EXPIRED",RecentBaeminDetail.consume().reason)
        RecentBaeminDetail.remember(ShopIdentity("이전 가게"),"old")
        RecentBaeminDetail.remember(ShopIdentity("새 가게"),"new")
        assertNull(RecentBaeminDetail.peekFresh("old"))
        assertEquals("새 가게",RecentBaeminDetail.consume().capture?.identity?.name)
    }
    @Test fun recentInformationFillsBlankFieldsButRejectsDifferentStore() {
        val recent = ShopIdentity("교촌 치킨","강남점","1")
        val (merged, conflicts) = ShareIdentityMerger.merge(listOf(IdentityEvidence(ShopIdentity("",shopNo="1"),"URL"),IdentityEvidence(recent,"RECENT")))
        assertEquals(recent,merged); assertTrue(conflicts.isEmpty())
        val (wrong, reasons) = ShareIdentityMerger.merge(listOf(IdentityEvidence(ShopIdentity("다른 가게",shopNo="2"),"URL"),IdentityEvidence(recent,"RECENT")))
        assertEquals("다른 가게",wrong.name); assertNull(wrong.shopNo); assertTrue(reasons.isNotEmpty())
        val branchConflict = ShareIdentityMerger.merge(listOf(IdentityEvidence(ShopIdentity("교촌치킨","서초점"),"TITLE"),IdentityEvidence(recent,"RECENT")))
        assertEquals("서초점",branchConflict.first.branch); assertNull(branchConflict.first.shopNo)
    }
    @Test fun parserDiagnosticsExcludeRawPayloadByDefault() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val logger = DiagnosticLogger(context)
        val name = "비공개실제가게"
        val draft = ShareImportHandler().receive("$name\nhttps://baemin.go.link/a", logger=logger,trace="share-regression",resolvePublicLinks=false)
        assertEquals(name,draft.identity.name)
        logger.refresh(); val events = logger.view.value.events.filter { it.traceId=="share-regression" }
        assertTrue(events.any { it.status=="SHARE_PAYLOAD_PARSED" })
        assertTrue(events.any { it.status=="SHARE_URL_FOUND" })
        assertTrue(events.all { !it.details.toString().contains(name) })
    }
}
