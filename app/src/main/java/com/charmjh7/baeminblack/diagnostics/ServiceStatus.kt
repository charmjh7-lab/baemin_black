package com.charmjh7.baeminblack.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StageStatus(val value: String, val at: Long = System.currentTimeMillis(), val trace: String = "")
data class ServiceHealth(
    val service: StageStatus = StageStatus("SERVICE_DISABLED"), val target: StageStatus = StageStatus("TARGET_INACTIVE"),
    val screen: StageStatus = StageStatus("SHOP_UNRECOGNIZED"), val matching: StageStatus = StageStatus("NOT_MATCHED"),
    val overlay: StageStatus = StageStatus("HIDDEN"), val shopLabel: String = "", val error: String = "",
    val lastTarget: Long = 0, val lastShop: Long = 0, val lastOverlay: Long = 0, val lastHealthy: Long = 0
)
class ServiceStatus {
    private val state = MutableStateFlow(ServiceHealth())
    val flow = state.asStateFlow()
    fun change(block: (ServiceHealth) -> ServiceHealth) { state.value = block(state.value) }
    fun disconnect(reason: String) { change { it.copy(service = StageStatus("SERVICE_DISABLED"), target = StageStatus("TARGET_INACTIVE"),
        screen = StageStatus("SHOP_UNRECOGNIZED"), matching = StageStatus("NOT_MATCHED"), overlay = StageStatus("HIDDEN"), shopLabel = "", error = reason) } }
}
