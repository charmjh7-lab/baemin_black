package com.charmjh7.baeminblack.accessibility

/** Only package/window metadata is read from non-target windows. No other app tree is traversed. */
data class ForegroundObservation(
    val rootPackage: String? = null, val rootWindow: Int = -1,
    val applicationPackage: String? = null, val applicationWindow: Int = -1,
    val eventPackage: String? = null, val transition: Boolean = false,
    val targetTransition: Boolean = false, val ownOverlay: Boolean = false,
    val interactive: Boolean = true, val rootIsApplication: Boolean = true, val systemWindowFocused: Boolean = false
)
data class ForegroundDecision(val active: Boolean, val packageName: String?, val window: Int, val reason: String)
class ForegroundAppPolicy(private val target: String) {
    private var awaitingTargetTransition = false
    fun decide(input: ForegroundObservation): ForegroundDecision {
        val pkg = input.applicationPackage ?: input.rootPackage
        val window = if (input.applicationPackage != null) input.applicationWindow else input.rootWindow
        fun inactive(reason: String) = ForegroundDecision(false, pkg, window, reason)
        if (!input.interactive) { awaitingTargetTransition = true; return inactive("DEVICE_LOCKED") }
        if (input.transition && !input.ownOverlay && !input.eventPackage.isNullOrBlank() && input.eventPackage != target) {
            awaitingTargetTransition = true
            return ForegroundDecision(false, input.eventPackage, window, "FOREGROUND_TRANSITION_AWAY")
        }
        if (input.systemWindowFocused) { awaitingTargetTransition = true; return inactive("SYSTEM_WINDOW_FOREGROUND") }
        if (pkg == null) return inactive("ACTIVE_APPLICATION_UNAVAILABLE")
        if (pkg != target || (input.rootIsApplication && input.rootPackage != null && input.rootPackage != target)) {
            awaitingTargetTransition = true; return inactive("OTHER_APPLICATION_FOREGROUND")
        }
        // A delayed content event must not undo a launcher/recents transition while window metadata is stale.
        if (awaitingTargetTransition && !input.targetTransition) return inactive("AWAITING_TARGET_WINDOW_TRANSITION")
        if (input.targetTransition) awaitingTargetTransition = false
        return ForegroundDecision(true, pkg, window, "TARGET_APPLICATION_FOREGROUND")
    }
}
