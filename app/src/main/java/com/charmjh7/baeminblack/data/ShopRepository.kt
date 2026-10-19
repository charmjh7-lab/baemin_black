package com.charmjh7.baeminblack.data

import android.net.Uri
import androidx.room.withTransaction
import com.charmjh7.baeminblack.matching.ShopMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class ShopRepository(val db: BlackDatabase, val media: MediaStore) {
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
    suspend fun saveNote(note: Note, newPhotos: List<Uri>) = withContext(Dispatchers.IO) { lock.withLock {
        LocalDate.parse(note.date)
        require(note.rating == null || note.rating in 1..5) { "만족도는 1~5로 입력해 주세요." }
        require(note.body.isNotBlank() || note.title.isNotBlank() || note.menu.isNotBlank() || note.photos.isNotEmpty() || newPhotos.isNotEmpty()) { "메모나 사진을 추가해 주세요." }
        val copied = mutableListOf<String>()
        try {
            newPhotos.forEach { copied += media.copy(it) }
            val old = db.dao().note(note.id)
            db.withTransaction {
                require(db.dao().shop(note.shopId) != null) { "가게가 삭제되었어요." }
                db.dao().put(note.copy(photos = note.photos + copied, updatedAt = System.currentTimeMillis()))
                db.dao().touch(note.shopId, System.currentTimeMillis())
            }
            media.remove(old?.photos.orEmpty() - note.photos.toSet())
        } catch (e: Exception) { media.remove(copied); throw e }
    } }
    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) { lock.withLock {
        db.withTransaction { db.dao().deleteNote(note.id); db.dao().touch(note.shopId, System.currentTimeMillis()) }
        media.remove(note.photos)
    } }
    suspend fun deleteShop(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        val photos = db.dao().notes().filter { it.shopId == id }.flatMap { it.photos }
        db.dao().deleteShop(id); media.remove(photos)
    } }
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }
}
