package com.charmjh7.baeminblack.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Rect
import android.os.*
import android.provider.Settings
import android.view.accessibility.*
import android.widget.Toast
import com.charmjh7.baeminblack.BlackApplication
import com.charmjh7.baeminblack.MainActivity
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.diagnostics.*
import com.charmjh7.baeminblack.matching.*
import com.charmjh7.baeminblack.overlay.*
import com.charmjh7.baeminblack.share.RecentBaeminDetail
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class BaeminAccessibilityService : AccessibilityService() {
    companion object { const val TARGET_PACKAGE = "com.sampleapp" }
    private val app get() = application as BlackApplication
    private val log get() = app.diagnostics
    private val health get() = app.serviceStatus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
        if (error !is CancellationException) {
            log.event("service", "TASK_EXCEPTION", false, "SERVICE_TASK_ERROR", error = error)
            health.change { it.copy(error = "SERVICE_TASK_ERROR") }
            runCatching { if (targetActive) showStatus() }.onFailure {
                log.event("status_overlay", "REFRESH_FAILED", false, "STATUS_REFRESH_ERROR", error = it)
            }
        }
    })
    private val foregroundPolicy = ForegroundAppPolicy(TARGET_PACKAGE)
    private var foreground = ForegroundDecision(false, null, -1, "NOT_OBSERVED")
    private var foregroundTrace = ""
    private val overlayWindowIds = linkedSetOf<Int>()
    private val detector = ShopScreenDetector()
    private val gate = ScrollBadgeState()
    private val scheduler = AnalysisScheduler()
    private var analysis: Job? = null
    private var collecting: Job? = null
    private var monitor: Job? = null
    private var connected = false
    private var registered = false
    private var records: List<ShopWithNotes> = emptyList()
    private var prefs = OverlaySettings()
    private var activeWindow = -1
    private var targetActive = false
    private var viewport = Rect()
    private var binding: Binding? = null
    private var undoTicket: ShopRepository.StatusUndo? = null
    private var saving = false
    private var eventCount = 0
    private var firstEventAt = 0L
    private var lastEventAt = 0L
    private var contentDirty = false
    private var lastAnalysisStarted = 0L
    private lateinit var badges: BadgeOverlayController
    private lateinit var detail: DetailOverlayController
    private lateinit var status: ServiceStatusOverlayController
    data class Binding(val shop: DetectedShop, val window: Int, val token: Long, val trace: String, val record: Shop?)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            log.event("service", intent?.action.orEmpty()); deactivate("SCREEN_LOCKED")
            if (intent?.action == Intent.ACTION_USER_PRESENT) schedule(0)
        }
    }
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').mapNotNull(ComponentName::unflattenFromString).contains(ComponentName(this@BaeminAccessibilityService, BaeminAccessibilityService::class.java))
            log.event("service", "ACCESSIBILITY_SETTING_CHANGED", details = mapOf("enabled" to enabled.toString()))
            if (!enabled) { connected = false; deactivate("PERMISSION_REVOKED"); health.disconnect("PERMISSION_REVOKED") }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        log.event("service", "CONNECT_STARTED")
        if (::status.isInitialized) { deactivate("RECONNECTED"); badges.destroy() }
        connected = true
        badges = BadgeOverlayController(this, log, ::overlayMetadata) { ok, reason -> overlayResult("badge", ok, reason) }
        detail = DetailOverlayController(this, log, ::overlayMetadata) { ok, reason -> overlayResult("detail", ok, reason) }
        status = ServiceStatusOverlayController(this, log, ::overlayMetadata) { ok, reason -> overlayResult("status", ok, reason) }
        health.change { it.copy(service = StageStatus("SERVICE_CONNECTED"), error = "") }
        log.event("service", "SERVICE_CONNECTED")
        if (!registered) {
            val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, settingsObserver)
            registered = true
        }
        collecting?.cancel()
        collecting = scope.launch {
            try { combine(app.repository.shops, app.settings.flow) { rows, settings -> rows to settings }.collect { (rows, settings) ->
                records = rows; prefs = settings
                if (targetActive) { showStatus(); schedule(0) }
            } } catch (e: CancellationException) { throw e } catch (e: Exception) { failure("repository", "ROOM_OBSERVE_ERROR", e) }
        }
        monitor?.cancel()
        monitor = scope.launch {
            while (isActive && connected) { delay(1000)
                try {
                if (targetActive) refreshForeground()
                aggregateEvents()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure("service", "LIFECYCLE_MONITOR_ERROR", e) }
            }
        }
        if (refreshForeground()) schedule(0)
    }
    private fun usable() = connected && getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !connected || !::status.isInitialized) return
        try { handleEvent(event) } catch (e: Exception) { failure("service", "EVENT_PROCESSING_ERROR", e) }
    }
    private fun overlayMetadata(): Map<String, String> = mapOf("foregroundPackage" to foreground.packageName.orEmpty(),
        "foregroundWindow" to foreground.window.toString(), "foregroundTrace" to foregroundTrace)
    @Suppress("DEPRECATION")
    private fun refreshForeground(event: AccessibilityEvent? = null): Boolean {
        val all = windows
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString(); val rootWindow = root?.windowId ?: -1
        root?.recycle()
        val application = all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
            .maxByOrNull { it.layer } ?: all.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.id == rootWindow }
        val applicationRoot = application?.root
        val applicationPackage = applicationRoot?.packageName?.toString()
        applicationRoot?.recycle()
        all.filter { OverlayEventPolicy.isOverlay(it.type) }.forEach { overlayWindowIds += it.id }
        while (overlayWindowIds.size > 64) overlayWindowIds.remove(overlayWindowIds.first())
        val own = OverlayEventPolicy.isOwnedWindow(all.firstOrNull { it.id == event?.windowId }?.type, event?.windowId, overlayWindowIds)
        val systemFocused = all.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused && it.layer > (application?.layer ?: -1) }
        val transition = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED && !own && event.windowChanges and
                (AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED) != 0)
        val targetTransition = !own && ((transition && event?.packageName?.toString() == TARGET_PACKAGE) ||
            (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED && event.windowChanges and
                (AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED) != 0 && applicationPackage == TARGET_PACKAGE))
        val decision = foregroundPolicy.decide(ForegroundObservation(rootPackage, rootWindow, applicationPackage, application?.id ?: -1,
            event?.packageName?.toString(), transition, targetTransition, own, usable(),
            all.firstOrNull { it.id == rootWindow }?.type?.let { it == AccessibilityWindowInfo.TYPE_APPLICATION } ?: true, systemFocused))
        val previous = foreground
        if (previous != decision) {
            foregroundTrace = log.trace()
            log.event("foreground", "ACTIVE_APPLICATION_EVALUATED", decision.active, if (decision.active) "" else decision.reason, foregroundTrace,
                mapOf("previousPackage" to previous.packageName.orEmpty(), "newPackage" to decision.packageName.orEmpty(),
                    "previousWindow" to previous.window.toString(), "newWindow" to decision.window.toString(), "active" to decision.active.toString(),
                    "rootPackage" to rootPackage.orEmpty(), "applicationPackage" to applicationPackage.orEmpty(), "eventPackage" to event?.packageName?.toString().orEmpty(),
                    "ownOverlay" to own.toString(), "reason" to decision.reason))
        }
        foreground = decision
        if (!decision.active) {
            if (targetActive || activeWindow >= 0) deactivate(decision.reason)
            return false
        }
        activate(decision.window)
        return true
    }
    private fun handleEvent(event: AccessibilityEvent) {
        // Foreground validity is evaluated BEFORE the self-overlay filter or source-package check.
        if (!refreshForeground(event)) return
        val pkg = event.packageName?.toString()
        val own = OverlayEventPolicy.isOwnedWindow(windows.firstOrNull { it.id == event.windowId }?.type, event.windowId, overlayWindowIds)
        if (own) { log.event("scheduler", "SKIPPED", reason = "SELF_OVERLAY_WINDOW"); return }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            showStatus(); schedule(85); return
        }
        if (pkg != TARGET_PACKAGE) return
        showStatus()
        eventCount++; val now = SystemClock.elapsedRealtime(); if (firstEventAt == 0L) firstEventAt = now; lastEventAt = now
        if (!targetActive) log.event("service", "TARGET_EVENT_RECEIVED", details = mapOf("type" to event.eventType.toString(), "window" to event.windowId.toString()))
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (gate.remaining(SystemClock.uptimeMillis()) == 0L) log.event("scheduler", "SCROLL_STARTED")
                cancelAnalysis("SCROLL_IN_PROGRESS"); gate.scroll(SystemClock.uptimeMillis()); binding = null
                badges.hide("SCROLL_IN_PROGRESS"); detail.hide("SCROLL_IN_PROGRESS")
                health.change { it.copy(screen = StageStatus("SHOP_UNRECOGNIZED"), shopLabel = "", matching = StageStatus("NOT_MATCHED"), overlay = StageStatus("HIDDEN"), overlays = emptyMap()) }
                showStatus(); schedule(ScrollBadgeState.SCROLL_SETTLE_MS)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { invalidate("SCREEN_TRANSITION"); showStatus(); schedule(85) }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> { contentDirty = true; badges.hide("CONTENT_CHANGED"); schedule(85) }
        }
    }
    private fun activate(window: Int) {
        if (activeWindow != window && window >= 0) { invalidate("WINDOW_CHANGED"); activeWindow = window; log.event("service", "SCREEN_CHANGED", details = mapOf("window" to window.toString())) }
        val wasActive = targetActive; targetActive = true
        health.change { it.copy(target = StageStatus("TARGET_DETECTED"), lastTarget = System.currentTimeMillis()) }
        if (!wasActive || viewport.isEmpty) {
            val metrics = resources.displayMetrics; viewport = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }
    private fun aggregateEvents() {
        if (eventCount > 0) { log.event("service", "TARGET_EVENTS_AGGREGATED", details = mapOf("count" to eventCount.toString(),
            "firstElapsed" to firstEventAt.toString(), "lastElapsed" to lastEventAt.toString())); eventCount = 0; firstEventAt = 0 }
    }
    private fun cancelAnalysis(reason: String) {
        if (analysis?.isActive == true) { log.event("scheduler", "ANALYSIS_CANCELLED", reason = reason); analysis?.cancel() }
        analysis = null; scheduler.started()
    }
    private fun invalidate(reason: String) {
        cancelAnalysis(reason); gate.invalidate(); binding = null; undoTicket = null
        if (::badges.isInitialized) { badges.hide(reason, foregroundTrace); detail.hide(reason, foregroundTrace) }
        health.change { it.copy(screen = StageStatus("SHOP_UNRECOGNIZED"), matching = StageStatus("NOT_MATCHED"), overlay = StageStatus("HIDDEN"), overlays = emptyMap(), shopLabel = "") }
    }
    private fun deactivate(reason: String) {
        targetActive = false
        log.event("foreground", "OVERLAY_REMOVAL_REQUESTED", reason = reason, trace = foregroundTrace, details = overlayMetadata())
        invalidate(reason); activeWindow = -1; viewport = Rect(); RecentBaeminDetail.leave()
        if (::status.isInitialized) status.hide(reason, foregroundTrace)
        health.change { it.copy(target = StageStatus("TARGET_INACTIVE"), error = if (reason == "TARGET_INACTIVE") it.error else reason) }
    }
    private fun schedule(delayMs: Long) {
        if (!connected || !targetActive) return
        if (analysis?.isActive == true) { log.event("scheduler", "ANALYSIS_MERGED", reason = "SAME_SCREEN_PENDING"); return }
        val trace = log.trace(); val token = gate.generation
        val now = SystemClock.uptimeMillis(); val deadline = scheduler.deadline(now)
        log.event("scheduler", "ANALYSIS_SCHEDULED", trace = trace, details = mapOf("token" to token.toString(), "waitMs" to delayMs.toString(), "maxDeadline" to deadline.toString()))
        val wait = if (delayMs >= ScrollBadgeState.SCROLL_SETTLE_MS) delayMs else
            maxOf(minOf(delayMs, (deadline - now).coerceAtLeast(0)), if (delayMs == 85L) (lastAnalysisStarted + 250 - now).coerceAtLeast(0) else 0)
        analysis = scope.launch(start = CoroutineStart.LAZY) {
            delay(wait)
            scheduler.started(); contentDirty = false; lastAnalysisStarted = SystemClock.uptimeMillis()
            try { analyze(token, trace) }
            catch (e: CancellationException) { log.event("analysis", "ANALYSIS_CANCELLED", reason = "GENERATION_OR_LIFECYCLE_CHANGED", trace = trace); throw e }
            catch (e: Exception) { failure("analysis", "ACCESSIBILITY_EXCEPTION", e, trace) }
            finally {
                if (contentDirty && token == gate.generation && targetActive && connected) {
                    analysis = null; schedule(85)
                }
            }
        }
        analysis?.start()
    }
    @Suppress("DEPRECATION")
    private suspend fun analyze(token: Long, trace: String) {
        if (!refreshForeground()) return
        val start = SystemClock.elapsedRealtime()
        log.event("analysis", "ANALYSIS_STARTED", trace = trace)
        val root = rootInActiveWindow
        if (root == null) { deactivate("ROOT_UNAVAILABLE"); return }
        try {
            if (root.packageName?.toString() != TARGET_PACKAGE) { log.event("analysis", "SKIPPED", reason = "WRONG_PACKAGE", trace = trace); deactivate("TARGET_INACTIVE"); return }
            activate(root.windowId)
            // activate can invalidate a token when the initial root supplies a previously unknown window.
            if (token != gate.generation) { log.event("analysis", "SKIPPED", reason = "STALE_WINDOW", trace = trace); analysis = null; schedule(0); return }
            viewport = Rect().also(root::getBoundsInScreen)
            log.event("analysis", "ROOT_AVAILABLE", trace = trace, details = mapOf("package" to TARGET_PACKAGE, "windowId" to root.windowId.toString(), "viewport" to viewport.toShortString()))
            val snapshot = detector.snapshot(root)
            val screenSession = "${log.sessionId}:$activeWindow:${gate.generation}"
            val recent = RecentBaeminDetail.peekFresh(screenSession)
            val report = withContext(Dispatchers.Default) { detector.analyze(snapshot, records.map { it.shop }, recent) }
            log.sensitive("recognition_candidates", trace, mapOf("candidates" to report.candidates.joinToString("\n")))
            currentCoroutineContext().ensureActive()
            if (!gate.mayShow(token, SystemClock.uptimeMillis()) || !validRoot(snapshot.windowId) || !publicationValid(snapshot, report.screen)) { log.event("analysis", "SKIPPED", reason = "STALE_WINDOW", trace = trace); return }
            log.event("analysis", "ANALYSIS_COMPLETED", report.screen != ShopScreen.Unknown, report.reason, trace,
                report.metrics + ("screen" to report.screen.javaClass.simpleName), SystemClock.elapsedRealtime() - start)
            if (report.screen == ShopScreen.Unknown) { log.snapshot(snapshot, trace, report.reason, TARGET_PACKAGE); unrecognized(report.reason, trace); return }
            health.change { it.copy(screen = StageStatus("SCREEN_RECOGNIZED", trace = trace), error = "") }
            val targetLayer = windows.firstOrNull { it.id == snapshot.windowId }?.layer ?: Int.MAX_VALUE
            val blockers = windows.filter { it.id != snapshot.windowId && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.layer > targetLayer }.map { Rect().also(it::getBoundsInScreen) }
            binding = null
            when (val result = report.screen) {
                is ShopScreen.Listing -> {
                    RecentBaeminDetail.clear(); detail.hide("LISTING")
                    val matches = result.shops.filter { match(it.identity, trace)?.status == ShopStatus.BLOCKED }
                    health.change { it.copy(shopLabel = "목록 ${result.shops.size}개 가게", matching = StageStatus(if (matches.isEmpty()) "NOT_MATCHED" else "MATCHED", trace = trace)) }
                    log.event("badge", "BLOCKED_MATCHES_SELECTED", trace = trace, details = mapOf("recognized" to result.shops.size.toString(), "blockedMatches" to matches.size.toString()))
                    if (prefs.badges) badges.update(matches, viewport, blockers, trace)
                    else { badges.hide("SETTING_DISABLED", trace); log.event("badge", "SKIPPED", reason = "SETTING_DISABLED", trace = trace) }
                    if (matches.isEmpty()) log.event("badge", "SKIPPED", reason = "NO_BLOCKED_MATCH", trace = trace)
                }
                is ShopScreen.Detail -> {
                    badges.hide("DETAIL", trace)
                    val record = match(result.shop.identity, trace)
                    val ambiguous = record == null && records.any { ShopMatcher.sameBase(ShopIdentity(it.shop.name, it.shop.branch), result.shop.identity) &&
                        (result.shop.identity.branch.isBlank() || it.shop.branch.isBlank() || ShopMatcher.key(it.shop.branch) == ShopMatcher.key(result.shop.identity.branch)) }
                    if (!ambiguous) {
                        binding = Binding(result.shop, snapshot.windowId, token, trace, record); RecentBaeminDetail.remember(result.shop.identity, screenSession, "ACCESSIBILITY_DETAIL", snapshot.windowId, trace)
                    } else { log.event("matching", "QUICK_ACTION_DISABLED", reason = "AMBIGUOUS_STORED_SHOPS", trace = trace) }
                    health.change { it.copy(screen = StageStatus("SHOP_RECOGNIZED", trace = trace), shopLabel = result.shop.identity.name + " " + result.shop.identity.branch,
                        matching = StageStatus(if (record != null) "MATCHED" else "NOT_MATCHED", trace = trace), lastShop = System.currentTimeMillis(),
                        error = if (ambiguous) "AMBIGUOUS_STORED_SHOPS" else "") }
                    val b = binding
                    if (b != null) detail.show(result.shop, records.firstOrNull { it.shop.id == record?.id }, prefs, viewport, blockers, trace, { quickStatus(b) }, { openMemo(b) })
                    else detail.hide("AMBIGUOUS_IDENTITY")
                }
                ShopScreen.Unknown -> Unit
            }
            log.event("scheduler", "SCROLL_SETTLED", trace = trace)
            showStatus()
        } finally { root.recycle() }
    }
    @Suppress("DEPRECATION") private suspend fun publicationValid(snapshot: NodeSnapshot, screen: ShopScreen): Boolean {
        val targets = when (screen) {
            is ShopScreen.Detail -> listOf(screen.shop)
            is ShopScreen.Listing -> screen.shops
            ShopScreen.Unknown -> return true
        }
        val root = rootInActiveWindow ?: return false
        try {
            if (root.windowId != snapshot.windowId || root.packageName?.toString() != TARGET_PACKAGE) return false
            val fresh = detector.snapshot(root)
            return targets.all { shop ->
                val title = snapshot.nodes.firstOrNull { it.bounds == shop.titleBounds && it.text.isNotBlank() } ?: return@all false
                fresh.nodes.any { it.visible && it.text == title.text && it.bounds == title.bounds }
            }
        } finally { root.recycle() }
    }
    private fun match(identity: ShopIdentity, trace: String): Shop? {
        val report = ShopMatcher.explain(identity, records.map { it.shop })
        log.event("matching", "MATCH_RESULT", report.shop != null, report.reason, trace, mapOf("hasName" to identity.name.isNotBlank().toString(),
            "hasBranch" to identity.branch.isNotBlank().toString(), "shopNo" to identity.shopNo.orEmpty(), "basis" to report.basis,
            "stored" to records.size.toString(), "candidates" to report.candidates.toString(), "blocked" to (report.shop?.status == ShopStatus.BLOCKED).toString()) + report.details)
        log.sensitive("matching_names", trace, mapOf("observed" to identity.toString(), "stored" to records.take(30).joinToString("\n") { "${it.shop.id}:${it.shop.name}/${it.shop.branch}" }, "result" to report.reason, "basis" to report.basis))
        return report.shop
    }
    private fun unrecognized(reason: String, trace: String) {
        binding = null; badges.hide(reason, trace); detail.hide(reason)
        health.change { it.copy(screen = StageStatus("SHOP_UNRECOGNIZED", trace = trace), matching = StageStatus("NOT_MATCHED", trace = trace), shopLabel = "", error = reason) }
        log.event("recognition", "SHOP_UNRECOGNIZED", false, reason, trace); if (targetActive) showStatus()
    }
    @Suppress("DEPRECATION") private fun validRoot(window: Int): Boolean {
        if (!refreshForeground() || !targetActive || activeWindow != window) return false
        val root = rootInActiveWindow ?: return false
        return try { root.packageName?.toString() == TARGET_PACKAGE && root.windowId == window } finally { root.recycle() }
    }
    @Suppress("DEPRECATION") private suspend fun validate(b: Binding): Boolean {
        log.event("quick_action", "VALIDATION_STARTED", trace = b.trace)
        if (b.token != gate.generation || binding?.shop?.identity != b.shop.identity || !validRoot(b.window)) return invalid(b)
        val root = rootInActiveWindow ?: return invalid(b)
        val fresh = try { detector.analyze(detector.snapshot(root), records.map { it.shop }).screen } finally { root.recycle() }
        val valid = ActionTarget(b.shop.identity, b.window, b.token).accepts((fresh as? ShopScreen.Detail)?.shop?.identity, activeWindow, gate.generation) && validRoot(b.window)
        log.event("quick_action", "TARGET_VALIDATED", valid, if (valid) "" else "STALE_ACTION_TARGET", b.trace)
        if (!valid) { invalid(b); return false }; return true
    }
    private fun invalid(b: Binding): Boolean { log.event("quick_action", "TARGET_REJECTED", false, "STALE_ACTION_TARGET", b.trace); binding = null; detail.hide("STALE_ACTION_TARGET"); toast("화면이 바뀌었어요. 가게를 다시 확인하고 있어요."); cancelAnalysis("STALE_ACTION_TARGET"); schedule(0); showStatus(); return false }
    private fun quickStatus(b: Binding) {
        if (saving) return
        saving = true
        scope.launch { try {
            if (!validate(b)) return@launch
            val state = if (b.record?.status == ShopStatus.BLOCKED) ShopStatus.NORMAL else ShopStatus.BLOCKED
            log.event("quick_action", "STATUS_BUTTON_CLICK", trace = b.trace)
            val ticket = app.repository.quickStatus(b.shop.identity, state, b.trace)
            if (b.token == gate.generation && validRoot(b.window)) undoTicket = ticket
            toast(if (state == ShopStatus.BLOCKED) "블랙리스트에 등록했어요. 상태 메뉴에서 실행취소할 수 있어요." else "블랙리스트에서 해제했어요. 상태 메뉴에서 실행취소할 수 있어요.")
            log.event("quick_action", "UI_SAVE_CONFIRMED", trace = b.trace); showStatus()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { failure("storage", "ROOM_SAVE_ERROR", e, b.trace); toast("저장하지 못했어요. 상태 메뉴에서 오류를 확인해 주세요.") }
        finally { saving = false } }
    }
    private fun openMemo(b: Binding) { scope.launch { try { if (validate(b)) openApp("memo", b) } catch (e: Exception) { if (e is CancellationException) throw e; failure("quick_action", "MEMO_OPEN_ERROR", e, b.trace) } } }
    private fun openApp(action: String, b: Binding? = null) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("quick_action", action).putExtra("action_id", log.trace()).putExtra("trace_id", b?.trace)
        if (b != null) { intent.putExtra("shop_name", b.shop.identity.name).putExtra("shop_branch", b.shop.identity.branch).putExtra("shop_no", b.shop.identity.shopNo); b.record?.let { intent.putExtra("shop_id", it.id) } }
        try { startActivity(intent) } catch (e: RuntimeException) { failure("quick_action", "ACTIVITY_OPEN_FAILED", e) }
    }
    private fun showStatus() {
        if (!::status.isInitialized || !refreshForeground() || !targetActive) return
        val state = health.flow.value; val b = binding
        val actions = mutableListOf<ServiceStatusOverlayController.Action>()
        if (b != null) {
            actions += ServiceStatusOverlayController.Action(if (b.record?.status == ShopStatus.BLOCKED) "블랙 해제" else "블랙 등록") { quickStatus(b) }
            actions += ServiceStatusOverlayController.Action("메모 작성") { openMemo(b) }
            if (b.record != null) actions += ServiceStatusOverlayController.Action("기록 열기") { scope.launch { if (validate(b)) openApp("record", b) } }
        } else actions += ServiceStatusOverlayController.Action("직접 등록") { openApp("manual") }
        undoTicket?.let { ticket -> actions += ServiceStatusOverlayController.Action("실행취소") { scope.launch {
            try { val ok = app.repository.undo(ticket, log.trace()); undoTicket = null; toast(if (ok) "이번 변경을 되돌렸어요." else "기록이 이후 변경되어 실행취소하지 않았어요."); showStatus() }
            catch (e: Exception) { if (e is CancellationException) throw e; failure("storage", "UNDO_ERROR", e); toast("실행취소하지 못했어요.") }
        } } }
        actions += ServiceStatusOverlayController.Action("현재 인식 상태 · 진단 기록") { openApp("diagnostics") }
        val description = (if (b != null) state.shopLabel else if (state.screen.value == "SCREEN_RECOGNIZED") "${state.shopLabel} · 상세화면에서 기록할 수 있어요" else "가게 정보 인식 안 됨") +
            (if (state.error.isNotBlank()) "\n${state.error}" else "\n${state.screen.value}")
        status.show(if (state.error.isNotBlank()) "확인" else if (b != null) "인식" else "대기", description, prefs.minimized, viewport,
            b?.trace ?: state.screen.trace, actions) { app.settings.update(prefs.copy(minimized = !prefs.minimized)) }
    }
    private fun overlayResult(kind: String, ok: Boolean, reason: String) {
        val now = System.currentTimeMillis(); val hidden = reason == "HIDDEN"
        health.change { previous ->
            val stages = previous.overlays + (kind to StageStatus(if (hidden) "HIDDEN" else if (ok) "OVERLAY_VISIBLE" else "OVERLAY_FAILED", trace = binding?.trace.orEmpty()))
            val failed = stages.values.any { it.value == "OVERLAY_FAILED" }
            previous.copy(overlays = stages, overlay = StageStatus(if (failed) "OVERLAY_FAILED" else if (stages.values.any { it.value == "OVERLAY_VISIBLE" }) "OVERLAY_VISIBLE" else "HIDDEN"),
                lastOverlay = if (ok && !hidden) now else previous.lastOverlay, lastHealthy = if (ok && !hidden && !failed) now else previous.lastHealthy,
                error = if (!ok) "$kind:$reason" else if (!failed && previous.error.startsWith("$kind:")) "" else previous.error)
        }
    }
    private fun failure(stage: String, reason: String, error: Throwable, trace: String = log.sessionId) {
        log.event(stage, "EXCEPTION", false, reason, trace, error = error)
        health.change { it.copy(error = reason) }
        binding = null
        if (::badges.isInitialized) { badges.hide(reason, trace); detail.hide(reason) }
        if (targetActive && ::status.isInitialized && stage != "status_overlay") showStatus()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); invalidate("CONFIGURATION_CHANGED"); if (targetActive) { viewport = Rect(); schedule(85) } }
    override fun onInterrupt() { log.event("service", "ON_INTERRUPT"); deactivate("SERVICE_INTERRUPTED"); health.change { it.copy(error = "SERVICE_INTERRUPTED") } }
    override fun onUnbind(intent: Intent?): Boolean { log.event("service", "ON_UNBIND"); connected = false; deactivate("SERVICE_UNBOUND"); health.disconnect("SERVICE_UNBOUND"); return super.onUnbind(intent) }
    override fun onDestroy() {
        log.event("service", "ON_DESTROY"); connected = false; aggregateEvents(); deactivate("SERVICE_DESTROYED"); health.disconnect("SERVICE_DESTROYED")
        if (registered) { unregisterReceiver(screenReceiver); contentResolver.unregisterContentObserver(settingsObserver); registered = false }
        scope.cancel(); if (::badges.isInitialized) badges.destroy(); super.onDestroy()
    }
}
