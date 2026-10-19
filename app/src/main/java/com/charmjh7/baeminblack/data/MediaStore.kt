package com.charmjh7.baeminblack.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class MediaStore(private val context: Context) {
    val directory = File(context.filesDir, "photos").apply { mkdirs() }
    fun file(name: String): File {
        require(name.matches(Regex("[a-zA-Z0-9-]+\\.(jpg|png|webp|img)"))) { "잘못된 사진 경로예요." }
        return File(directory, name)
    }
    fun copy(uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/")) { "사진 파일을 선택해 주세요." }
        val name = UUID.randomUUID().toString() + ".img"
        val target = file(name)
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val size = source.read(buffer); if (size < 0) break
                        total += size; require(total <= 30L * 1024 * 1024) { "사진은 한 장에 30MB까지 저장할 수 있어요." }
                        out.write(buffer, 0, size)
                    }
                    require(total > 0) { "빈 사진 파일이에요." }
                }
            } ?: error("사진을 읽지 못했어요. 다시 선택해 주세요.")
            return name
        } catch (e: Exception) { target.delete(); throw e }
    }
    fun remove(names: Iterable<String>) { names.forEach { runCatching { file(it).delete() } } }
}
