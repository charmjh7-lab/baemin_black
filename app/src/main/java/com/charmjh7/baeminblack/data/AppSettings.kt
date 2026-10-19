package com.charmjh7.baeminblack.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlaySettings(val badges: Boolean = true, val warnings: Boolean = true, val memo: Boolean = true)
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(OverlaySettings(prefs.getBoolean("badges", true), prefs.getBoolean("warnings", true), prefs.getBoolean("memo", true)))
    val flow = state.asStateFlow()
    fun update(settings: OverlaySettings) {
        prefs.edit().putBoolean("badges", settings.badges).putBoolean("warnings", settings.warnings).putBoolean("memo", settings.memo).apply()
        state.value = settings
    }
}
