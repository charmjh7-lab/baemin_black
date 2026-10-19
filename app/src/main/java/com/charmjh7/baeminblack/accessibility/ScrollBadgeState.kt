package com.charmjh7.baeminblack.accessibility

/** Uses monotonic milliseconds, never wall time. A new screen invalidates every previous result. */
class ScrollBadgeState(private val settleMs: Long = SCROLL_SETTLE_MS) {
    var generation: Long = 0; private set
    private var lastScroll: Long? = null
    fun scroll(now: Long): Long { lastScroll = now; return ++generation }
    fun invalidate(): Long { lastScroll = null; return ++generation }
    fun remaining(now: Long): Long = lastScroll?.let { (settleMs - (now - it)).coerceAtLeast(0) } ?: 0
    fun mayShow(token: Long, now: Long): Boolean = token == generation && remaining(now) == 0L
    companion object { const val SCROLL_SETTLE_MS = 180L }
}
