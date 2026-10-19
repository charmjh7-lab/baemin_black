package com.charmjh7.baeminblack.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StageStatus(val value: String, val at: Long = System.currentTimeMillis(), val trace: String = "")
data class ServiceHealth(
    val service: StageStatus = StageStatus("SERVICE_DISABLED"), val target: StageStatus = StageStatus("TARGET_INACTIVE"),
    val screen: StageStatus = StageStatus("SHOP_UNRECOGNIZED"), val matching: StageStatus = StageStatus("NOT_MATCHED"),
    val overlay: StageStatus = StageStatus("HIDDEN"), val overlays: Map<String, StageStatus> = emptyMap(), val shopLabel: String = "", val error: String = "",
    val lastTarget: Long = 0, val lastShop: Long = 0, val lastOverlay: Long = 0, val lastHealthy: Long = 0
)
class ServiceStatus(context: android.content.Context? = null) {
    private val prefs = context?.getSharedPreferences("service_history", android.content.Context.MODE_PRIVATE)
    private var lastPersist = 0L
    private val state = MutableStateFlow(ServiceHealth(lastTarget = prefs?.getLong("target", 0) ?: 0,
        lastShop = prefs?.getLong("shop", 0) ?: 0, lastOverlay = prefs?.getLong("overlay", 0) ?: 0,
        lastHealthy = prefs?.getLong("healthy", 0) ?: 0, error = prefs?.getString("error", "").orEmpty()))
    val flow = state.asStateFlow()
    fun change(block: (ServiceHealth) -> ServiceHealth) {
        val previous = state.value; val next = block(previous); state.value = next
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPersist >= 1000 || previous.error != next.error || previous.service != next.service) {
            prefs?.edit()?.putLong("target", next.lastTarget)?.putLong("shop", next.lastShop)?.putLong("overlay", next.lastOverlay)
                ?.putLong("healthy", next.lastHealthy)?.putString("error", next.error)?.apply()
            lastPersist = now
        }
    }
    fun disconnect(reason: String) { change { it.copy(service = StageStatus("SERVICE_DISABLED"), target = StageStatus("TARGET_INACTIVE"),
        screen = StageStatus("SHOP_UNRECOGNIZED"), matching = StageStatus("NOT_MATCHED"), overlay = StageStatus("HIDDEN"), overlays = emptyMap(), shopLabel = "", error = reason) } }
}
