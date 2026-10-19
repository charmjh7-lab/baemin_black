package com.charmjh7.baeminblack.accessibility

import android.view.accessibility.AccessibilityWindowInfo

/** Window state/content events from our own accessibility windows never change the target screen. */
object OverlayEventPolicy {
    fun isOverlay(windowType: Int?) = windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
}
