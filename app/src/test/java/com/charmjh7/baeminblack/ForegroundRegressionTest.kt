package com.charmjh7.baeminblack

import com.charmjh7.baeminblack.accessibility.*
import org.junit.Assert.*
import org.junit.Test

class ForegroundRegressionTest {
    private fun target() = ForegroundObservation(rootPackage = "com.sampleapp", rootWindow = 4,
        applicationPackage = "com.sampleapp", applicationWindow = 4)
    @Test fun targetMetadataActivatesAndOtherAppsRemoveTarget() {
        for (other in listOf("com.sec.android.app.launcher", "other.app", "com.charmjh7.baeminblack")) {
            val policy = ForegroundAppPolicy("com.sampleapp")
            assertTrue(policy.decide(target()).active)
            assertFalse(policy.decide(target().copy(rootPackage = other, applicationPackage = other, eventPackage = other, transition = true)).active)
        }
    }
    @Test fun delayedBaeminContentCannotUndoHomeTransitionEvenWithStaleRoot() {
        val policy = ForegroundAppPolicy("com.sampleapp")
        assertTrue(policy.decide(target()).active)
        assertFalse(policy.decide(target().copy(eventPackage = "launcher", transition = true)).active)
        assertEquals("AWAITING_TARGET_WINDOW_TRANSITION",policy.decide(target().copy(eventPackage = "com.sampleapp")).reason)
        assertTrue(policy.decide(target().copy(eventPackage = "com.sampleapp", transition = true, targetTransition = true, rootWindow = 9, applicationWindow = 9)).active)
    }
    @Test fun overlayFilterNeverOverridesActualForeground() {
        val policy = ForegroundAppPolicy("com.sampleapp")
        assertTrue(policy.decide(target().copy(ownOverlay = true)).active)
        assertFalse(policy.decide(target().copy(applicationPackage = "launcher", rootPackage = "launcher", ownOverlay = true)).active)
    }
    @Test fun rootsUnavailableAndRecentsAndLockFailClosed() {
        val policy = ForegroundAppPolicy("com.sampleapp")
        assertTrue(policy.decide(target()).active)
        assertFalse(policy.decide(ForegroundObservation()).active)
        assertFalse(policy.decide(target().copy(systemWindowFocused = true)).active)
        assertFalse(policy.decide(target().copy(interactive = false)).active)
    }
    @Test fun removedOverlayIdDoesNotHideARealApplicationTransition() {
        assertTrue(OverlayEventPolicy.isOwnedWindow(null, 50, setOf(50)))
        assertFalse(OverlayEventPolicy.isOwnedWindow(android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION, 50, setOf(50)))
    }
    @Test fun sourcePackageCannotOverrideAnotherActiveApplication() {
        val policy = ForegroundAppPolicy("com.sampleapp")
        assertFalse(policy.decide(target().copy(applicationPackage = "other.app", applicationWindow = 8, eventPackage = "com.sampleapp")).active)
        assertFalse(policy.decide(target().copy(rootPackage = "other.app", eventPackage = "com.sampleapp")).active)
    }
}
