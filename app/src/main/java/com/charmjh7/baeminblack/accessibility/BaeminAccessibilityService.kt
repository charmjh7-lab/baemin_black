package com.charmjh7.baeminblack.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.charmjh7.baeminblack.BlackApplication
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.matching.ShopMatcher
import com.charmjh7.baeminblack.overlay.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class BaeminAccessibilityService : AccessibilityService() {
    companion object { const val TARGET_PACKAGE = "com.sampleapp"; private const val CONTENT_MERGE_MS = 85L }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = ShopScreenDetector()
    private val gate = ScrollBadgeState()
    private var analysis: Job? = null
    private lateinit var badges: BadgeOverlayController
    private lateinit var detail: DetailOverlayController
    private var records: List<ShopWithNotes> = emptyList()
    private var settings = OverlaySettings()
    private var targetActive = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { clear(); targetActive = false }
    }
    override fun onServiceConnected() {
        badges = BadgeOverlayController(this); detail = DetailOverlayController(this)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
        val app = application as BlackApplication
        scope.launch {
            combine(app.repository.shops, app.settings.flow) { rows, prefs -> rows to prefs }.collect { (rows, prefs) ->
                records = rows; settings = prefs
                if (targetActive) { clear(); schedule(0) }
            }
        }
        schedule(0)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::badges.isInitialized) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Our overlay windows also emit window events; they must not tear themselves down.
            val changed = windows.firstOrNull { it.id == event.windowId }
            if (changed?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return
            val meaningful = AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or AccessibilityEvent.WINDOWS_CHANGE_BOUNDS or AccessibilityEvent.WINDOWS_CHANGE_LAYER
            if (event.windowChanges and meaningful == 0) return
            clear()
            schedule(CONTENT_MERGE_MS)
            return
        }
        val packageName = event.packageName?.toString()
        if (packageName != TARGET_PACKAGE) {
            // Observe window transitions from other apps only to clear; never traverse their trees.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                clear(); targetActive = false
            }
            return
        }
        targetActive = true
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                analysis?.cancel(); gate.scroll(SystemClock.uptimeMillis()); badges.hide(); detail.hide(); schedule(ScrollBadgeState.SCROLL_SETTLE_MS)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> { clear(); schedule(CONTENT_MERGE_MS) }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Do not keep a possibly stale location on screen while a list is being rebuilt.
                badges.hide(); detail.hide(); schedule(maxOf(CONTENT_MERGE_MS, gate.remaining(SystemClock.uptimeMillis())))
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun schedule(delayMs: Long) {
        analysis?.cancel()
        val token = gate.generation
        analysis = scope.launch {
            delay(delayMs)
            if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { clear(); return@launch }
            val root = rootInActiveWindow ?: run { clear(); return@launch }
            if (root.packageName?.toString() != TARGET_PACKAGE) { root.recycle(); clear(); targetActive = false; return@launch }
            targetActive = true
            try {
                val snapshot = withContext(Dispatchers.Default) { detector.snapshot(root) }
                val rows = records
                val result = withContext(Dispatchers.Default) { detector.detect(snapshot, rows.map { it.shop }) }
                ensureActive()
                if (!gate.mayShow(token, SystemClock.uptimeMillis())) return@launch
                // Re-check the active root/window after asynchronous analysis; no stale-screen publication.
                val active = rootInActiveWindow
                val valid = active != null && active.windowId == snapshot.windowId && active.packageName?.toString() == TARGET_PACKAGE
                active?.recycle()
                if (!valid) { clear(); return@launch }
                val blockers = windows.filter { it.id != snapshot.windowId && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.layer > (windows.firstOrNull { w -> w.id == snapshot.windowId }?.layer ?: Int.MAX_VALUE) }
                    .map { w -> Rect().also(w::getBoundsInScreen) }
                when (result) {
                    is ShopScreen.Listing -> {
                        detail.hide()
                        val matches = if (settings.badges) result.shops.filter { ShopMatcher.match(it.identity, rows.map { r -> r.shop })?.status == ShopStatus.BLOCKED } else emptyList()
                        badges.update(matches, snapshot.viewport, blockers)
                    }
                    is ShopScreen.Detail -> {
                        badges.hide()
                        val match = ShopMatcher.match(result.shop.identity, rows.map { it.shop })
                        // Ambiguous existing names do not expose any record or a misleading create shortcut.
                        val ambiguous = match == null && rows.count { ShopMatcher.normalize(it.shop.name) == ShopMatcher.normalize(result.shop.identity.name) } > 0
                        if (ambiguous) detail.hide() else detail.show(result.shop, rows.firstOrNull { it.shop.id == match?.id }, settings, snapshot.viewport, blockers)
                    }
                    ShopScreen.Unknown -> { badges.hide(); detail.hide() }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { badges.hide(); detail.hide() } finally { root.recycle() }
        }
    }
    private fun clear() { analysis?.cancel(); gate.invalidate(); if (::badges.isInitialized) { badges.hide(); detail.hide() } }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); clear(); if (targetActive) schedule(CONTENT_MERGE_MS) }
    override fun onInterrupt() { clear() }
    override fun onUnbind(intent: Intent?): Boolean { clear(); return super.onUnbind(intent) }
    override fun onDestroy() { clear(); scope.cancel(); runCatching { unregisterReceiver(screenReceiver) }; if (::badges.isInitialized) badges.destroy(); super.onDestroy() }
}
