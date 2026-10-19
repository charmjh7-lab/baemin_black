package com.charmjh7.baeminblack.accessibility

/** A fixed deadline from the first event prevents starvation under an endless content stream. */
class AnalysisScheduler(private val debounce: Long = 85, private val maxWait: Long = 250) {
    private var first: Long? = null
    fun deadline(now: Long): Long { val start = first ?: now.also { first = it }; return minOf(now + debounce, start + maxWait) }
    fun started() { first = null }
}
