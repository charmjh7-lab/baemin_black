package com.charmjh7.baeminblack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.matching.ShopIdentity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class QuickRecordTest {
    private lateinit var db: BlackDatabase
    private lateinit var repo: ShopRepository
    @Before fun setup() { val context = ApplicationProvider.getApplicationContext<Context>(); db = Room.inMemoryDatabaseBuilder(context, BlackDatabase::class.java).build(); repo = ShopRepository(db, MediaStore(context)) }
    @After fun close() { db.close() }
    @Test fun createBlockUndoAndDuplicatePrevention() = runBlocking {
        val identity = ShopIdentity("치킨", "강남점", "12")
        val first = repo.quickStatus(identity, ShopStatus.BLOCKED, "t1")
        assertEquals(ShopStatus.BLOCKED, db.dao().shops().single().status)
        assertTrue(repo.undo(first, "t1")); assertTrue(db.dao().shops().isEmpty())
        repo.quickStatus(identity, ShopStatus.BLOCKED, "t2")
        repo.quickStatus(identity, ShopStatus.BLOCKED, "t3")
        assertEquals(1, db.dao().shops().size)
    }
    @Test fun existingNotesSurviveUndoAndLaterChangesPreventUndo() = runBlocking {
        val shop = Shop(name = "가게", branch = "서울점", status = ShopStatus.FAVORITE)
        repo.saveShop(shop)
        val note = Note(shopId = shop.id, date = "2026-10-20", body = "보존")
        repo.saveNote(note, emptyList())
        val ticket = repo.quickStatus(ShopIdentity(shop.name, shop.branch), ShopStatus.BLOCKED, "t")
        assertTrue(repo.undo(ticket, "t")); assertEquals(ShopStatus.FAVORITE, db.dao().shop(shop.id)?.status)
        assertEquals("보존", db.dao().notes().single().body)
        val second = repo.quickStatus(ShopIdentity(shop.name, shop.branch), ShopStatus.BLOCKED, "u")
        repo.saveNote(note.copy(body = "이후 수정"), emptyList())
        assertFalse(repo.undo(second, "u")); assertEquals("이후 수정", db.dao().notes().single().body)
    }
    @Test fun newShopWithAdditionalNoteCannotBeDeletedByUndo() = runBlocking {
        val ticket = repo.quickStatus(ShopIdentity("새 가게"), ShopStatus.BLOCKED, "t")
        repo.saveNote(Note(shopId = ticket.after.id, date = "2026-10-20", body = "보호"), emptyList())
        assertFalse(repo.undo(ticket, "t")); assertEquals(1, db.dao().shops().size)
    }
    @Test fun draftIsOnlyPersistedTogetherWithValidNote() = runBlocking {
        val draft = Shop(name = "신규", branch = "부산점")
        assertTrue(db.dao().shops().isEmpty()) // cancel path never calls a repository write
        val blank = Note(shopId = draft.id, date = "2026-10-20")
        assertTrue(runCatching { repo.saveNote(blank, emptyList(), draft) }.isFailure)
        assertTrue(db.dao().shops().isEmpty())
        val id = repo.saveNote(blank.copy(body = "메모"), emptyList(), draft)
        assertEquals(id, db.dao().notes().single().shopId)
        val otherDraft = draft.copy(id = "second")
        repo.saveNote(blank.copy(id = "note2", body = "둘째"), emptyList(), otherDraft)
        assertEquals(1, db.dao().shops().size); assertEquals(2, db.dao().notes().size)
    }
    @Test fun ambiguousBranchCannotCreateDuplicate() = runBlocking {
        repo.saveShop(Shop(name = "가게", branch = "강남점")); repo.saveShop(Shop(name = "가게", branch = "서초점"))
        assertTrue(runCatching { repo.quickStatus(ShopIdentity("가게"), ShopStatus.BLOCKED, "t") }.isFailure)
        assertTrue(db.dao().shops().all { it.status == ShopStatus.NORMAL })
    }
}
