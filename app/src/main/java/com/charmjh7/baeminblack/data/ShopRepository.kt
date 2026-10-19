package com.charmjh7.baeminblack.data

import android.net.Uri
import androidx.room.withTransaction
import com.charmjh7.baeminblack.matching.ShopMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlinx.serialization.json.Json

class ShopRepository(val db: BlackDatabase, val media: MediaStore, private val logger: com.charmjh7.baeminblack.diagnostics.DiagnosticLogger? = null) {
    private val lock = Mutex()
    val shops = db.dao().observe()
    suspend fun byNumber(no: String): Shop? = withContext(Dispatchers.IO) { db.dao().byNumber(no) }
    suspend fun saveShop(shop: Shop, firstMemo: String = ""): String = withContext(Dispatchers.IO) { lock.withLock {
        require(ShopMatcher.normalize(shop.name).isNotEmpty()) { "가게명을 입력해 주세요." }
        require(shop.shopNo == null || shop.shopNo.matches(Regex("[0-9]{1,30}"))) { "가게 고유번호는 숫자로 입력해 주세요." }
        db.withTransaction {
            val existing = shop.shopNo?.let { db.dao().byNumber(it) }
            require(existing == null || existing.id == shop.id) { "같은 가게 고유번호가 이미 등록되어 있어요. 기존 가게를 수정해 주세요." }
            db.dao().put(shop.copy(name = shop.name.trim(), branch = shop.branch.trim(), aliases = shop.aliases.filter { it.isNotBlank() }.distinct(), updatedAt = System.currentTimeMillis()))
            if (firstMemo.isNotBlank()) {
                db.dao().put(Note(shopId = shop.id, date = LocalDate.now().toString(), body = firstMemo))
                db.dao().touch(shop.id, System.currentTimeMillis())
            }
            shop.id
        }
    } }
    suspend fun saveNote(note: Note, newPhotos: List<Uri>, draft: Shop? = null, trace: String = logger?.trace().orEmpty()): String = withContext(Dispatchers.IO) { lock.withLock {
        LocalDate.parse(note.date)
        require(note.rating == null || note.rating in 1..5) { "만족도는 1~5로 입력해 주세요." }
        require(note.body.isNotBlank() || note.title.isNotBlank() || note.menu.isNotBlank() || note.photos.isNotEmpty() || newPhotos.isNotEmpty()) { "메모나 사진을 추가해 주세요." }
        val copied = mutableListOf<String>()
        try {
            newPhotos.forEach { copied += media.copy(it) }
            val old = db.dao().note(note.id)
            logger?.event("storage", "NOTE_TRANSACTION_STARTED", trace = trace)
            val shopId = db.withTransaction {
                val target = if (draft != null) {
                    val identity = com.charmjh7.baeminblack.matching.ShopIdentity(draft.name, draft.branch, draft.shopNo)
                    resolve(identity) ?: draft.also { db.dao().put(it) }
                } else requireNotNull(db.dao().shop(note.shopId)) { "가게가 삭제되었어요." }
                db.dao().put(note.copy(shopId = target.id, photos = note.photos + copied, updatedAt = System.currentTimeMillis()))
                db.dao().touch(target.id, System.currentTimeMillis())
                target.id
            }
            logger?.event("storage", "NOTE_TRANSACTION_COMMITTED", trace = trace)
            removeUnused(old?.photos.orEmpty() - note.photos.toSet())
            shopId
        } catch (e: Exception) { logger?.event("storage", "NOTE_TRANSACTION_FAILED", false, "ROOM_OR_MEDIA_ERROR", trace = trace, error = e); media.remove(copied); throw e }
    } }
    private suspend fun resolve(identity: com.charmjh7.baeminblack.matching.ShopIdentity): Shop? {
        val all = db.dao().shops()
        val match = ShopMatcher.match(identity, all)
        require(match != null || all.none { ShopMatcher.normalize(it.name) == ShopMatcher.normalize(identity.name) &&
            (identity.branch.isBlank() || it.branch.isBlank() || ShopMatcher.normalize(it.branch) == ShopMatcher.normalize(identity.branch)) }) { "같은 상호의 기록을 확인한 뒤 직접 등록해 주세요." }
        return match
    }
    data class StatusUndo(val before: Shop?, val after: Shop, val notes: List<Note>)
    suspend fun quickStatus(identity: com.charmjh7.baeminblack.matching.ShopIdentity, status: ShopStatus, trace: String): StatusUndo = withContext(Dispatchers.IO) { lock.withLock {
        logger?.event("storage", "TRANSACTION_STARTED", trace = trace)
        try {
            val ticket = db.withTransaction {
                require(identity.name.isNotBlank())
                val before = resolve(identity)
                logger?.event("storage", "EXISTING_LOOKUP", trace = trace, details = mapOf("exists" to (before != null).toString()))
                val after = (before ?: Shop(name = identity.name, branch = identity.branch, shopNo = identity.shopNo)).copy(
                    status = status, updatedAt = maxOf(System.currentTimeMillis(), (before?.updatedAt ?: 0) + 1))
                db.dao().put(after)
                StatusUndo(before, after, db.dao().notes().filter { it.shopId == after.id }.sortedBy { it.id })
            }
            logger?.event("storage", if (ticket.before == null) "SHOP_CREATED" else "SHOP_UPDATED", trace = trace)
            logger?.event("storage", "TRANSACTION_COMMITTED", trace = trace)
            ticket
        } catch (e: Exception) { logger?.event("storage", "TRANSACTION_FAILED", false, "ROOM_ERROR", trace, error = e); throw e }
    } }
    suspend fun undo(ticket: StatusUndo, trace: String): Boolean = withContext(Dispatchers.IO) { lock.withLock {
        val restored = db.withTransaction {
            val current = db.dao().shop(ticket.after.id)
            val notes = db.dao().notes().filter { it.shopId == ticket.after.id }.sortedBy { it.id }
            if (current != ticket.after || notes != ticket.notes) false
            else { if (ticket.before != null) db.dao().put(ticket.before.copy(updatedAt = maxOf(System.currentTimeMillis(), ticket.after.updatedAt + 1)))
                else if (notes.isEmpty()) db.dao().deleteShop(ticket.after.id) else return@withTransaction false
                true
            }
        }
        logger?.event("storage", "UNDO", restored, if (restored) "" else "UNDO_CONFLICT", trace)
        restored
    } }
    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) { lock.withLock {
        db.withTransaction { db.dao().deleteNote(note.id); db.dao().touch(note.shopId, System.currentTimeMillis()) }
        removeUnused(note.photos)
    } }
    suspend fun deleteShop(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        val photos = db.dao().notes().filter { it.shopId == id }.flatMap { it.photos }
        db.dao().deleteShop(id); removeUnused(photos)
    } }
    private suspend fun removeUnused(names: Iterable<String>) {
        // A restored image can be referenced by more than one note. A cleanup failure leaves an
        // orphan file rather than putting any successfully committed record at risk.
        val references = runCatching { db.dao().photoPayloads().flatMap { Json.decodeFromString<List<String>>(it) }.toSet() }.getOrNull() ?: return
        media.remove(names.filter { it !in references })
    }
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }
}
