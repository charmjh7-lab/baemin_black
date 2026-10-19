package com.charmjh7.baeminblack
import com.charmjh7.baeminblack.accessibility.ScrollBadgeState
import org.junit.Assert.*
import org.junit.Test
class ScrollBadgeStateTest {
    @Test fun repeatedScrollWaitsForLatestEventAndRejectsOldResults() {
        val gate = ScrollBadgeState()
        val old = gate.generation
        val first = gate.scroll(1000)
        assertFalse(gate.mayShow(old, 2000)); assertFalse(gate.mayShow(first, 1179))
        val second = gate.scroll(1150)
        assertFalse(gate.mayShow(first, 2000)); assertFalse(gate.mayShow(second, 1329))
        assertTrue(gate.mayShow(second, 1330))
        gate.invalidate(); assertFalse(gate.mayShow(second, 2000))
    }
}
