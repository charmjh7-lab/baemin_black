package com.charmjh7.baeminblack.share

import android.os.SystemClock
import com.charmjh7.baeminblack.matching.ShopIdentity

/**
 * In-memory fallback for an ACTION_SEND launched from the immediately preceding
 * Baemin detail screen. Never persisted or used to identify unrelated shares.
 */
object RecentBaeminDetail {
    private data class Capture(val identity: ShopIdentity, val elapsedMs: Long)
    @Volatile private var current: Capture? = null
    private const val MAX_AGE_MS = 20_000L

    fun remember(identity: ShopIdentity) {
        if (identity.name.isNotBlank()) current = Capture(identity, SystemClock.elapsedRealtime())
    }

    fun clear() { current = null }

    fun consumeFresh(): ShopIdentity? {
        val last = current
        current = null
        val age = last?.let { SystemClock.elapsedRealtime() - it.elapsedMs } ?: return null
        return last.identity.takeIf { age in 0..MAX_AGE_MS }
    }
}
