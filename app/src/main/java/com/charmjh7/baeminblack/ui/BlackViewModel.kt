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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class BlackViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as BlackApplication
    val repo = app.repository
    val settings = app.settings.flow
    val rows = repo.shops.map<List<ShopWithNotes>, List<ShopWithNotes>?> { it }.catch { message = "기록을 읽지 못했어요. 앱을 다시 열어 주세요."; emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val route = saved.getStateFlow("route", "main")
    val selected = saved.getStateFlow("selected", "")
    val noteId = saved.getStateFlow("note", "")
    private var queuedIntent: Intent? = null
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    fun field(key: String): String = saved[key] ?: ""
    fun go(screen: String, shop: String = field("selected"), note: String = "") { saved["selected"] = shop; saved["note"] = note; saved["route"] = screen }
    fun newShop(name: String = "", branch: String = "", no: String = "", link: String = "", text: String = "") {
        saved["draft_name"] = name; saved["draft_branch"] = branch; saved["draft_no"] = no
        saved["draft_link"] = link; saved["draft_text"] = text; saved["draft_id"] = UUID.randomUUID().toString()
        go("shop", "")
    }
    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message?.takeIf { it.length < 200 } ?: "작업을 완료하지 못했어요. 다시 시도해 주세요." } finally {
                busy = false
                queuedIntent?.let { next -> queuedIntent = null; handleIntent(next, true) }
            }
        }
    }
    fun handleIntent(intent: Intent, fresh: Boolean = false) {
        val text = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty() else ""
        val title = if (intent.action == Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
                .ifBlank { intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty() }
        } else ""
        val key = if (text.isNotBlank()) "share:$text:$title" else "open:${intent.getStringExtra("shop_id")}:${intent.getStringExtra("shop_name")}:${intent.getStringExtra("shop_branch")}"
        if (!fresh && field("last_intent") == key) return
        if (busy) { queuedIntent = Intent(intent); return }
        saved["last_intent"] = key
        if (text.isNotBlank()) work {
            val draft = ShareImportHandler().receive(text, title)
            val existing = draft.identity.shopNo?.let { repo.byNumber(it) }
            if (existing != null) go("detail", existing.id)
            else newShop(draft.identity.name, draft.identity.branch, draft.identity.shopNo.orEmpty(), draft.url, draft.original)
        } else if (intent.hasExtra("shop_id")) go("detail", intent.getStringExtra("shop_id").orEmpty())
        else if (intent.hasExtra("shop_name")) newShop(intent.getStringExtra("shop_name").orEmpty(), intent.getStringExtra("shop_branch").orEmpty(), intent.getStringExtra("shop_no").orEmpty())
    }
    fun saveShop(shop: Shop, memo: String) = work { val id = repo.saveShop(shop, memo); go("detail", id) }
    fun saveNote(note: Note, photos: List<Uri>) = work { repo.saveNote(note, photos); go("detail", note.shopId) }
    fun status(shop: Shop, status: ShopStatus) = work { repo.saveShop(shop.copy(status = status)) }
    fun deleteNote(note: Note) = work { repo.deleteNote(note); message = "기록을 삭제했어요." }
    fun deleteShop(id: String) = work { repo.deleteShop(id); go("main"); message = "가게와 기록을 삭제했어요." }
    fun backup(uri: Uri, photos: Boolean) = work { BackupManager(app, repo).export(uri, photos); message = "백업 파일을 저장했어요." }
    fun restore(uri: Uri) = work { val (s, n) = BackupManager(app, repo).restore(uri); message = "가게 ${s}개, 기록 ${n}개를 가져왔어요. 기존 기록도 유지했어요." }
    fun updateSettings(settings: OverlaySettings) { app.settings.update(settings) }
}
