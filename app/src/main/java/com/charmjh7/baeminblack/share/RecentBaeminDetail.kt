package com.charmjh7.baeminblack.share

import android.os.SystemClock
import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.matching.ShopMatcher

/** Screen/overlay disposal does not own this short-lived, single-consumer share handoff. */
object RecentBaeminDetail {
    data class Capture(val identity: ShopIdentity, val elapsedMs: Long, val screenSession: String,
        val source: String, val windowId: Int, val traceId: String = "")
    data class Consumption(val capture: Capture?, val reason: String, val ageMs: Long?, val departureKind: String = "ACTIVE_TARGET")
    @Volatile private var current: Capture? = null
    @Volatile private var leftAt: Long? = null
    @Volatile private var departureKind = "ACTIVE_TARGET"
    private const val MAX_AGE_MS = 120_000L
    private const val SHARE_HANDOFF_MS = 90_000L
    @Synchronized fun remember(identity: ShopIdentity, screenSession: String = "", source: String = "ACCESSIBILITY_DETAIL", windowId: Int = -1, traceId: String = "") {
        if (identity.name.isNotBlank()) {
            current = Capture(ShopMatcher.canonical(identity), SystemClock.elapsedRealtime(), screenSession, source, windowId, traceId); leftAt = null; departureKind = "ACTIVE_TARGET"
        }
    }
    @Synchronized fun clear() { current = null; leftAt = null; departureKind = "ACTIVE_TARGET" }
    /** Mark departure once; repeated background events must not prolong the handoff. */
    @Synchronized fun leave(destination: String? = null, shareChooser: Boolean = false) {
        if (current == null) return
        if (leftAt == null) leftAt = SystemClock.elapsedRealtime()
        if (departureKind == "ACTIVE_TARGET" || departureKind == "UNRESOLVED") departureKind = when {
            shareChooser -> "SHARE_CHOOSER"
            destination == "com.charmjh7.baeminblack" -> "DIRECT_TARGET"
            destination.isNullOrBlank() -> "UNRESOLVED"
            else -> "OTHER_APP"
        }
    }
    @Synchronized fun peekFresh(screenSession: String): ShopIdentity? {
        val capture = current ?: return null
        return capture.identity.takeIf { capture.screenSession == screenSession && SystemClock.elapsedRealtime() - capture.elapsedMs in 0..MAX_AGE_MS }
    }
    @Synchronized fun consume(requireShareHandoff: Boolean = false): Consumption {
        val capture = current ?: return Consumption(null, "RECENT_DETAIL_MISSING", null)
        current = null
        val now = SystemClock.elapsedRealtime(); val age = now - capture.elapsedMs; val departure = leftAt; leftAt = null
        val kind = departureKind; departureKind = "ACTIVE_TARGET"
        if (age !in 0..MAX_AGE_MS || (departure != null && now - departure !in 0..SHARE_HANDOFF_MS))
            return Consumption(null, "RECENT_DETAIL_EXPIRED", age, kind)
        if (requireShareHandoff && kind != "SHARE_CHOOSER" &&
            !(kind == "DIRECT_TARGET" && departure != null && now - departure <= 10000) &&
            !(kind == "ACTIVE_TARGET" && age <= 10000)) return Consumption(null, "RECENT_DETAIL_REJECTED", age, kind)
        return Consumption(capture, "RECENT_DETAIL_AVAILABLE", age, kind)
    }
    fun consumeFresh(): ShopIdentity? = consume().capture?.identity
}
