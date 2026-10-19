package com.charmjh7.baeminblack

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.charmjh7.baeminblack.backup.BackupManager
import com.charmjh7.baeminblack.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class RepositoryBackupTest {
    private lateinit var context: Context; private lateinit var db: BlackDatabase; private lateinit var repo: ShopRepository
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, BlackDatabase::class.java).build(); repo = ShopRepository(db, MediaStore(context)) }
    @After fun close() { db.close() }
    @Test fun saveUpdateDeleteAndDuplicateNumber() = runBlocking {
        val s = Shop(name = "치킨", shopNo = "12"); repo.saveShop(s)
        val n = Note(shopId = s.id, date = "2026-10-20", body = "첫 기록"); repo.saveNote(n, emptyList())
        repo.saveNote(n.copy(body = "수정한 기록"), emptyList())
        assertEquals("수정한 기록", db.dao().notes().single().body)
        assertEquals(n.date, db.dao().shop(s.id)?.lastRecordDate)
        assertTrue(runCatching { repo.saveShop(s.copy(id = "different")) }.isFailure)
        assertEquals(1, db.dao().shops().size)
        repo.deleteNote(n); assertTrue(db.dao().notes().isEmpty()); assertNull(db.dao().shop(s.id)?.lastRecordDate)
    }
    @Test fun zipRoundTripWithPhotoAndIdempotentRestore() = runBlocking {
        val s = Shop(name = "백업 가게", shopNo = "77", status = ShopStatus.BLOCKED); repo.saveShop(s)
        val image = "aaaaaaaa-1111.img"; repo.media.file(image).writeBytes(byteArrayOf(1, 2, 3, 4))
        val n = Note(shopId = s.id, date = "2026-10-20", body = "사진 기록", photos = listOf(image)); repo.saveNote(n, emptyList())
        val backup = File(context.cacheDir, "roundtrip.zip"); val manager = BackupManager(context, repo)
        manager.export(Uri.fromFile(backup), true)
        repo.deleteShop(s.id); assertFalse(repo.media.file(image).exists())
        assertEquals(1 to 1, manager.restore(Uri.fromFile(backup)))
        val restored = db.dao().notes().single(); assertArrayEquals(byteArrayOf(1,2,3,4), repo.media.file(restored.photos.single()).readBytes())
        assertEquals(0 to 0, manager.restore(Uri.fromFile(backup)))
        repo.deleteShop(s.id); assertFalse(repo.media.file(restored.photos.single()).exists())
    }
    @Test fun failedPhotoAndMaliciousRestorePreserveExistingRecords() = runBlocking {
        val s = Shop(name = "보호"); repo.saveShop(s)
        val n = Note(shopId = s.id, date = "2026-10-20", body = "보존"); repo.saveNote(n, emptyList())
        val beforeFailure = db.dao().notes().single()
        assertTrue(runCatching { repo.saveNote(n.copy(body = "손실 금지"), listOf(Uri.parse("content://missing/image"))) }.isFailure)
        assertEquals("보존", db.dao().notes().single().body)
        val bad = File(context.cacheDir, "bad.zip")
        ZipOutputStream(bad.outputStream()).use { it.putNextEntry(ZipEntry("photos/../../escape.img")); it.write(byteArrayOf(1)); it.closeEntry() }
        assertTrue(runCatching { BackupManager(context, repo).restore(Uri.fromFile(bad)) }.isFailure)
        assertEquals(s.id, db.dao().shops().single().id); assertEquals(beforeFailure, db.dao().notes().single())
    }
}
