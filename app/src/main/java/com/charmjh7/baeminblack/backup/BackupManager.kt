package com.charmjh7.baeminblack.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.charmjh7.baeminblack.data.*
import com.charmjh7.baeminblack.matching.ShopMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.time.LocalDate
import java.util.UUID
import java.util.zip.*

@Serializable
data class BackupData(val formatVersion: Int = 1, val shops: List<Shop>, val notes: List<Note>)
data class MergePlan(val shops: List<Shop>, val notes: List<Note>)
object BackupMerge {
    fun plan(existingShops: List<Shop>, existingNotes: List<Note>, incoming: BackupData): MergePlan {
        require(incoming.formatVersion == 1) { "지원하지 않는 백업 버전이에요." }
        require(incoming.shops.map { it.id }.distinct().size == incoming.shops.size) { "중복 가게 ID가 있어요." }
        require(incoming.notes.map { it.id }.distinct().size == incoming.notes.size) { "중복 기록 ID가 있어요." }
        val shops = existingShops.toMutableList(); val notes = existingNotes.toMutableList()
        val map = mutableMapOf<String, String>()
        incoming.shops.forEach { s ->
            require(ShopMatcher.normalize(s.name).isNotEmpty()) { "가게명이 없는 백업이에요." }
            require(s.shopNo == null || s.shopNo.matches(Regex("[0-9]{1,30}"))) { "잘못된 가게 번호예요." }
            val target = if (s.shopNo != null) shops.singleOrNull { it.shopNo == s.shopNo } else
                shops.singleOrNull { it.id == s.id && it.shopNo == null && ShopMatcher.normalize(it.name) == ShopMatcher.normalize(s.name) && ShopMatcher.normalize(it.branch) == ShopMatcher.normalize(s.branch) }
            if (target != null) {
                map[s.id] = target.id
                // Existing classification and notes are never replaced by an import.
                val index = shops.indexOf(target)
                shops[index] = target.copy(aliases = (target.aliases + s.aliases).distinct(), shareLink = target.shareLink.ifBlank { s.shareLink }, shareText = target.shareText.ifBlank { s.shareText })
            } else {
                val id = if (shops.any { it.id == s.id }) UUID.randomUUID().toString() else s.id
                shops += s.copy(id = id); map[s.id] = id
            }
        }
        incoming.notes.forEach { n ->
            val shopId = map[n.shopId] ?: error("가게 연결이 없는 기록이에요.")
            LocalDate.parse(n.date)
            require(n.rating == null || n.rating in 1..5) { "잘못된 만족도예요." }
            val candidate = n.copy(shopId = shopId)
            val duplicate = notes.any { it.shopId == shopId && it.date == n.date && it.title == n.title && it.menu == n.menu && it.body == n.body && it.rating == n.rating && it.createdAt == n.createdAt && it.updatedAt == n.updatedAt && it.photos.size == n.photos.size }
            if (!duplicate) notes += candidate.copy(id = if (notes.any { it.id == n.id }) UUID.randomUUID().toString() else n.id)
        }
        return MergePlan(shops, notes)
    }
}
class BackupManager(private val context: Context, private val repo: ShopRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun export(uri: Uri, photos: Boolean) = withContext(Dispatchers.IO) { repo.exclusive {
        val data = repo.db.withTransaction { BackupData(shops = repo.db.dao().shops(), notes = repo.db.dao().notes()) }
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            if (!photos) {
                out.write(json.encodeToString(data.copy(notes = data.notes.map { it.copy(photos = emptyList()) })).toByteArray())
            } else ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(data).toByteArray()); zip.closeEntry()
                data.notes.flatMap { it.photos }.distinct().forEach { name ->
                    val file = repo.media.file(name); require(file.isFile) { "첨부 사진이 없어서 백업을 완료하지 못했어요." }
                    zip.putNextEntry(ZipEntry("photos/$name")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } ?: error("백업 파일을 열지 못했어요.")
    } }
    suspend fun restore(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) { repo.exclusive {
        val stage = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val committedFiles = mutableListOf<String>()
        try {
            val source = File(stage, "source")
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { limitedCopy(input, it, 250L * 1024 * 1024) } } ?: error("백업을 열지 못했어요.")
            val zipFormat = source.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
            val metadata: String
            val images = mutableMapOf<String, File>()
            if (zipFormat) {
                var manifest: String? = null; var total = 0L; val paths = mutableSetOf<String>()
                ZipInputStream(source.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(paths.add(entry.name) && paths.size <= 5000 && !entry.isDirectory) { "잘못된 백업 항목이에요." }
                        if (entry.name == "manifest.json") {
                            val bytes = ByteArrayOutputStream(); total += limitedCopy(zip, bytes, 10L * 1024 * 1024); manifest = bytes.toString("UTF-8")
                        } else {
                            require(entry.name.startsWith("photos/")) { "알 수 없는 백업 경로예요." }
                            val name = entry.name.removePrefix("photos/"); repo.media.file(name)
                            val file = File(stage, name)
                            total += file.outputStream().use { limitedCopy(zip, it, 30L * 1024 * 1024) }; images[name] = file
                        }
                        require(total <= 250L * 1024 * 1024) { "백업이 너무 커요." }
                        zip.closeEntry()
                    }
                }
                metadata = manifest ?: error("백업 정보가 없어요.")
            } else {
                require(source.length() <= 10L * 1024 * 1024); metadata = source.readText()
            }
            val incoming = json.decodeFromString<BackupData>(metadata)
            // Validate all references before copying files or committing any database changes.
            incoming.notes.flatMap { it.photos }.forEach { name -> repo.media.file(name); require(images[name]?.isFile == true) { "백업에 필요한 사진이 빠져 있어요." } }
            val counts = repo.db.withTransaction {
                val oldShops = repo.db.dao().shops(); val oldNotes = repo.db.dao().notes()
                val plan = BackupMerge.plan(oldShops, oldNotes, incoming)
                val newNotes = plan.notes.filter { n -> oldNotes.none { it.id == n.id } }
                val photoMap = mutableMapOf<String, String>()
                newNotes.flatMap { it.photos }.distinct().forEach { original ->
                    val name = UUID.randomUUID().toString() + ".img"
                    committedFiles += name
                    images.getValue(original).copyTo(repo.media.file(name)); photoMap[original] = name
                }
                plan.shops.forEach { repo.db.dao().put(it) }
                newNotes.forEach { repo.db.dao().put(it.copy(photos = it.photos.map(photoMap::getValue))) }
                plan.shops.forEach { s ->
                    val last = plan.notes.filter { it.shopId == s.id }.maxOfOrNull { it.date }
                    repo.db.dao().put(s.copy(lastRecordDate = last, updatedAt = maxOf(s.updatedAt, plan.notes.filter { it.shopId == s.id }.maxOfOrNull { it.updatedAt } ?: 0)))
                }
                (plan.shops.size - oldShops.size) to newNotes.size
            }
            counts
        } catch (e: Exception) { repo.media.remove(committedFiles); throw e } finally { stage.deleteRecursively() }
    } }
    private fun limitedCopy(input: InputStream, out: OutputStream, limit: Long): Long {
        val bytes = ByteArray(8192); var total = 0L
        while (true) { val n = input.read(bytes); if (n < 0) break; total += n; require(total <= limit) { "백업 항목이 너무 커요." }; out.write(bytes, 0, n) }
        return total
    }
}
