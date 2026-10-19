package com.charmjh7.baeminblack.accessibility

import android.view.accessibility.AccessibilityWindowInfo

/** Window state/content events from our own accessibility windows never change the target screen. */
object OverlayEventPolicy {
    fun isOverlay(windowType: Int?) = windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
    fun isOwnedWindow(windowType: Int?, windowId: Int?, previouslyObserved: Set<Int>): Boolean =
        if (windowType != null) isOverlay(windowType) else windowId != null && windowId in previouslyObserved
}
