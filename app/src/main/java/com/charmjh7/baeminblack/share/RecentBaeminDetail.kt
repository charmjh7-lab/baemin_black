package com.charmjh7.baeminblack.share

import android.os.SystemClock
import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.matching.ShopMatcher

/** Screen/overlay disposal does not own this short-lived, single-consumer share handoff. */
object RecentBaeminDetail {
    data class Capture(val identity: ShopIdentity, val elapsedMs: Long, val screenSession: String,
        val source: String, val windowId: Int, val traceId: String = "")
    data class Consumption(val capture: Capture?, val reason: String, val ageMs: Long?)
    @Volatile private var current: Capture? = null
    @Volatile private var leftAt: Long? = null
    private const val MAX_AGE_MS = 120_000L
    private const val SHARE_HANDOFF_MS = 90_000L
    @Synchronized fun remember(identity: ShopIdentity, screenSession: String = "", source: String = "ACCESSIBILITY_DETAIL", windowId: Int = -1, traceId: String = "") {
        if (identity.name.isNotBlank()) {
            current = Capture(ShopMatcher.canonical(identity), SystemClock.elapsedRealtime(), screenSession, source, windowId, traceId); leftAt = null
        }
    }
    @Synchronized fun clear() { current = null; leftAt = null }
    /** Mark departure once; repeated background events must not prolong the handoff. */
    @Synchronized fun leave() { if (current != null && leftAt == null) leftAt = SystemClock.elapsedRealtime() }
    @Synchronized fun peekFresh(screenSession: String): ShopIdentity? {
        val capture = current ?: return null
        return capture.identity.takeIf { capture.screenSession == screenSession && SystemClock.elapsedRealtime() - capture.elapsedMs in 0..MAX_AGE_MS }
    }
    @Synchronized fun consume(): Consumption {
        val capture = current ?: return Consumption(null, "RECENT_DETAIL_MISSING", null)
        current = null
        val now = SystemClock.elapsedRealtime(); val age = now - capture.elapsedMs; val departure = leftAt; leftAt = null
        if (age !in 0..MAX_AGE_MS || (departure != null && now - departure !in 0..SHARE_HANDOFF_MS))
            return Consumption(null, "RECENT_DETAIL_EXPIRED", age)
        return Consumption(capture, "RECENT_DETAIL_AVAILABLE", age)
    }
    fun consumeFresh(): ShopIdentity? = consume().capture?.identity
}
