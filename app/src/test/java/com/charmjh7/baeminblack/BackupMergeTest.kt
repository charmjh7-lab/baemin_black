package com.charmjh7.baeminblack
import com.charmjh7.baeminblack.backup.*
import com.charmjh7.baeminblack.data.*
import org.junit.Assert.*
import org.junit.Test
class BackupMergeTest {
    @Test fun shopNumberMergesButPreservesExistingClassificationAndNotes() {
        val old = Shop(id = "local", name = "가게", shopNo = "123", status = ShopStatus.BLOCKED)
        val imported = old.copy(id = "imported", status = ShopStatus.NORMAL)
        val note = Note(id = "n", shopId = "imported", date = "2026-10-20", body = "경험")
        val merged = BackupMerge.plan(listOf(old), emptyList(), BackupData(shops = listOf(imported), notes = listOf(note)))
        assertEquals(1, merged.shops.size); assertEquals(ShopStatus.BLOCKED, merged.shops.single().status)
        assertEquals("local", merged.notes.single().shopId)
        val twice = BackupMerge.plan(merged.shops, merged.notes, BackupData(shops = listOf(imported), notes = listOf(note)))
        assertEquals(merged.notes, twice.notes)
    }
    @Test fun collidingIdsNeverOverwriteUnrelatedShopOrNote() {
        val shop = Shop(id = "same", name = "원래 가게")
        val old = Note(id = "n", shopId = "same", date = "2026-10-19", body = "기존")
        val imported = Shop(id = "same", name = "다른 가게")
        val n = old.copy(body = "가져온 기록")
        val plan = BackupMerge.plan(listOf(shop), listOf(old), BackupData(shops = listOf(imported), notes = listOf(n)))
        assertEquals(2, plan.shops.size); assertEquals(2, plan.notes.size)
        assertEquals(old, plan.notes.first()); assertNotEquals("n", plan.notes.last().id); assertNotEquals("same", plan.notes.last().shopId)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectFutureFormat() { BackupMerge.plan(emptyList(), emptyList(), BackupData(99, emptyList(), emptyList())) }
    @Test(expected = IllegalStateException::class) fun rejectOrphanBeforeCommit() { BackupMerge.plan(emptyList(), emptyList(), BackupData(shops = emptyList(), notes = listOf(Note(shopId = "missing", date = "2026-10-20")))) }
}
