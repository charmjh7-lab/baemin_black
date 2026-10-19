package com.charmjh7.baeminblack.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.charmjh7.baeminblack.BlackApplication
import com.charmjh7.baeminblack.backup.BackupManager
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.share.ShareImportHandler
import com.charmjh7.baeminblack.share.ShareLinkParser
import com.charmjh7.baeminblack.share.RecentBaeminDetail
import com.charmjh7.baeminblack.share.IdentityEvidence
import com.charmjh7.baeminblack.share.ShareIdentityMerger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class BlackViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as BlackApplication
    val repo = app.repository
    val diagnostics = app.diagnostics
    val serviceHealth = app.serviceStatus.flow
    val diagnosticView = diagnostics.view
    val detailedUntil = diagnostics.detailedUntil
    fun refreshDiagnostics() { viewModelScope.launch { diagnostics.refresh() } }
    fun startDetailed(text: Boolean) { diagnostics.startDetailed(text) }
    fun stopDetailed() { diagnostics.stopDetailed() }
    fun clearDiagnostics() = work { diagnostics.clear() }
    fun deleteSnapshots() = work { diagnostics.deleteSnapshots() }
    suspend fun traceEvents(id: String) = diagnostics.traceEvents(id)
    suspend fun readSnapshot(name: String) = diagnostics.snapshotText(name)
    fun exportDiagnostics(uri: Uri, snapshots: Boolean) = work("diagnostic_export") {
        com.charmjh7.baeminblack.diagnostics.DiagnosticExporter(app, diagnostics).export(uri, snapshots)
        message = "진단 ZIP을 저장하고 다시 열어 확인했어요."
    }
    fun draftShop() = Shop(id = field("draft_id"), name = field("draft_name"), branch = field("draft_branch"), shopNo = field("draft_no").ifBlank { null })
    fun cancelNote(shopId: String) { go(if (field("route") == "quick_note") "main" else "detail", shopId) }
    val settings = app.settings.flow
    val rows = repo.shops.map<List<ShopWithNotes>, List<ShopWithNotes>?> { it }.catch { diagnostics.event("repository", "OBSERVE_FAILED", false, "ROOM_OBSERVE_ERROR", error = it); message = "기록을 읽지 못했어요. 앱을 다시 열어 주세요."; emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val route = saved.getStateFlow("route", "main")
    val selected = saved.getStateFlow("selected", "")
    val noteId = saved.getStateFlow("note", "")
    private var queuedIntent: Intent? = null
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    fun field(key: String): String = saved[key] ?: ""
    fun go(screen: String, shop: String = field("selected"), note: String = "") {
        if (field("route") == "shop" && screen != "shop" && saved.get<Boolean>("share_draft_pending") == true) {
            diagnostics.event("share", "REGISTRATION_LEFT_WITHOUT_SAVE", trace = field("active_trace"), details = mapOf("saveAttempted" to (saved.get<Boolean>("share_save_attempted") == true).toString()))
            saved["share_draft_pending"] = false
        }
        saved["selected"] = shop; saved["note"] = note; saved["route"] = screen }
    fun newShop(name: String = "", branch: String = "", no: String = "", link: String = "", text: String = "") {
        saved["draft_name"] = name; saved["draft_branch"] = branch; saved["draft_no"] = no
        saved["draft_link"] = link; saved["draft_text"] = text; saved["draft_id"] = UUID.randomUUID().toString()
        go("shop", "")
    }
    fun work(stage: String = "app_operation", block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { diagnostics.event(stage, "FAILED", false, "${stage.uppercase()}_ERROR", trace = field("active_trace").ifBlank { diagnostics.sessionId }, error = e); message = e.message?.takeIf { it.length < 200 } ?: "작업을 완료하지 못했어요. 다시 시도해 주세요." } finally {
                busy = false
                queuedIntent?.let { next -> queuedIntent = null; handleIntent(next, true) }
            }
        }
    }
    fun handleIntent(intent: Intent, fresh: Boolean = false) {
        val text = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty() else ""
        val title = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty() else ""
        val extraTitle = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty() else ""
        val key = if (intent.action == Intent.ACTION_SEND) "share:$text:$title:$extraTitle" else "open:${intent.getStringExtra("action_id")}:${intent.getStringExtra("quick_action")}:${intent.getStringExtra("shop_id")}:${intent.getStringExtra("shop_name")}:${intent.getStringExtra("shop_branch")}"
        if (!fresh && field("last_intent") == key) return
        if (busy) { queuedIntent = Intent(intent); return }
        saved["last_intent"] = key
        saved["active_trace"] = intent.getStringExtra("trace_id") ?: diagnostics.trace()
        val quick = intent.getStringExtra("quick_action")
        if (quick == "diagnostics") { go("diagnostics"); return }
        if (quick == "manual") { newShop(); return }
        if (quick == "memo") {
            val id = intent.getStringExtra("shop_id")
            if (!id.isNullOrBlank()) go("note", id)
            else {
                newShop(intent.getStringExtra("shop_name").orEmpty(), intent.getStringExtra("shop_branch").orEmpty(), intent.getStringExtra("shop_no").orEmpty())
                go("quick_note", "")
            }
            diagnostics.event("memo", "EDITOR_OPENED", trace = intent.getStringExtra("trace_id") ?: diagnostics.sessionId)
            return
        }
        if (intent.action == Intent.ACTION_SEND) {
            val trace = field("active_trace")
            diagnostics.event("share", "SHARE_INTENT_RECEIVED", trace = trace, details = mapOf("action" to Intent.ACTION_SEND,
                "textPresent" to text.isNotBlank().toString(), "textLength" to text.length.toString(),
                "subjectPresent" to title.isNotBlank().toString(), "titlePresent" to extraTitle.isNotBlank().toString()))
            val allowedLinks = (ShareLinkParser.urls(text) + ShareLinkParser.urls(title) + ShareLinkParser.urls(extraTitle)).distinct()
            val recent = if (allowedLinks.isNotEmpty()) RecentBaeminDetail.consume(requireShareHandoff = true) else null
            work("share") {
                diagnostics.event("share", recent?.reason ?: "RECENT_DETAIL_REJECTED", success = recent?.capture != null, trace = trace,
                    reason = recent?.reason ?: "NO_ALLOWED_BAEMIN_URL", details = mapOf("ageMs" to recent?.ageMs?.toString().orEmpty(),
                        "departureKind" to recent?.departureKind.orEmpty(), "screenSession" to recent?.capture?.screenSession.orEmpty(), "source" to recent?.capture?.source.orEmpty()), parentTrace = recent?.capture?.traceId.orEmpty())
                val draft = ShareImportHandler().receive(text, title, extraTitle, diagnostics, trace)
                val observed = recent?.capture?.identity
                val fits = observed != null && draft.conflicts.isEmpty() && com.charmjh7.baeminblack.matching.ShopMatcher.compatible(draft.identity, observed)
                if (observed != null && !fits) diagnostics.event("share", "RECENT_DETAIL_REJECTED", false, "IDENTITY_CONFLICT", trace)
                val evidence = listOf(IdentityEvidence(draft.identity, "SHARE_PAYLOAD")) + if (fits) listOf(IdentityEvidence(observed!!, "RECENT_ACCESSIBILITY_DETAIL")) else emptyList()
                val (identity, conflicts) = ShareIdentityMerger.merge(evidence)
                val allConflicts = draft.conflicts + conflicts
                val sources = mapOf("nameSource" to if (draft.identity.name.isNotBlank()) draft.sources["name"].orEmpty() else if (fits) "RECENT_ACCESSIBILITY_DETAIL" else "",
                    "idSource" to if (draft.identity.shopNo != null) draft.sources["shopNo"].orEmpty() else if (fits && observed?.shopNo != null) "RECENT_ACCESSIBILITY_DETAIL" else "")
                diagnostics.event("share", "SHARE_IDENTITY_MERGED", allConflicts.isEmpty(), allConflicts.joinToString(","), trace, sources + mapOf("hasName" to identity.name.isNotBlank().toString(), "hasBranch" to identity.branch.isNotBlank().toString(), "hasNumber" to (identity.shopNo != null).toString()))
                diagnostics.event("share", if (identity.name.isNotBlank()) "SHARE_NAME_FOUND" else "SHARE_NAME_MISSING", identity.name.isNotBlank(),
                    if (identity.name.isBlank()) "NO_NAME_IN_SHARE_OR_RECENT_DETAIL" else "", trace, sources)
                val existing = if (allConflicts.isEmpty()) repo.find(identity) else null
                diagnostics.event("share", "SHARE_EXISTING_LOOKUP", trace = trace, details = mapOf("matched" to (existing != null).toString()))
                if (existing != null) go("detail", existing.id)
                else {
                    newShop(identity.name, identity.branch, identity.shopNo.orEmpty(), draft.url, draft.original)
                    saved["share_draft_pending"] = true; saved["share_save_attempted"] = false
                    if (allConflicts.isNotEmpty()) message = "공유 정보가 서로 달라요. 입력된 가게 정보를 확인해 주세요."
                    else if (identity.name.isBlank()) message = "가게명을 확인하지 못했어요. 가게명만 입력해 주세요. 받은 링크는 유지했어요."
                }
                diagnostics.event("share", if (identity.name.isNotBlank() || existing != null) "SHARE_AUTOFILL_COMPLETED" else "SHARE_AUTOFILL_FAILED",
                    identity.name.isNotBlank() || existing != null, if (identity.name.isBlank() && existing == null) "NAME_REQUIRES_USER_INPUT" else "", trace,
                    sources + mapOf("hasName" to identity.name.isNotBlank().toString(), "hasBranch" to identity.branch.isNotBlank().toString(), "hasNumber" to (identity.shopNo != null).toString(),
                        "hasLink" to draft.url.isNotBlank().toString(), "openedExisting" to (existing != null).toString(), "dbWriteAttempted" to "false"))
            }
        } else if (intent.hasExtra("shop_id")) go("detail", intent.getStringExtra("shop_id").orEmpty())
        else if (intent.hasExtra("shop_name")) newShop(intent.getStringExtra("shop_name").orEmpty(), intent.getStringExtra("shop_branch").orEmpty(), intent.getStringExtra("shop_no").orEmpty())
    }
    fun saveShop(shop: Shop, memo: String) = work("storage") {
        val trace = field("active_trace").ifBlank { diagnostics.trace() }
        saved["share_save_attempted"] = true
        diagnostics.event("storage", "SHOP_SAVE_BUTTON_CLICK", trace = trace)
        val id = repo.saveShop(shop, memo, trace)
        saved["share_draft_pending"] = false
        go("detail", id); diagnostics.event("storage", "SHOP_SAVE_UI_CONFIRMED", trace = trace)
    }
    fun saveNote(note: Note, photos: List<Uri>) = work("storage") {
        val draft = if (field("route") == "quick_note") draftShop() else null
        val trace = field("active_trace").ifBlank { diagnostics.trace() }
        diagnostics.event("memo", "SAVE_BUTTON_CLICK", trace = trace)
        val id = repo.saveNote(note, photos, draft, trace); go("detail", id)
        diagnostics.event("memo", "UI_SAVE_CONFIRMED", trace = trace)
    }
    fun status(shop: Shop, status: ShopStatus) = work { repo.saveShop(shop.copy(status = status)) }
    fun deleteNote(note: Note) = work { repo.deleteNote(note); message = "기록을 삭제했어요." }
    fun deleteShop(id: String) = work { repo.deleteShop(id); go("main"); message = "가게와 기록을 삭제했어요." }
    fun backup(uri: Uri, photos: Boolean) = work { BackupManager(app, repo).export(uri, photos); message = "백업 파일을 저장했어요." }
    fun restore(uri: Uri) = work { val (s, n) = BackupManager(app, repo).restore(uri); message = "가게 ${s}개, 기록 ${n}개를 가져왔어요. 기존 기록도 유지했어요." }
    fun updateSettings(settings: OverlaySettings) { app.settings.update(settings) }
}
