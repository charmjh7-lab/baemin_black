package com.charmjh7.baeminblack.share

import com.charmjh7.baeminblack.matching.ShopIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.net.URLDecoder
import java.net.HttpURLConnection

data class ShareDraft(val identity: ShopIdentity, val url: String, val original: String)
object ShareLinkParser {
    private val hosts = setOf("baemin.com", "www.baemin.com", "baemin.go.link", "baemin.me", "www.baemin.me")
    private val keys = setOf("shopDetail_shopNo", "shopNo", "shopNumber")
    fun allowed(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme.equals("https", true) && u.host?.lowercase() in hosts && u.userInfo == null && (u.port == -1 || u.port == 443)
    }.getOrDefault(false)
    fun urls(text: String): List<String> = Regex("https://[^\\s<>\"\\[\\]]+").findAll(text)
        .map { it.value.trimEnd('.', ',', ')', '!', '\'') }.filter(::allowed).toList()
    fun number(url: String, depth: Int = 0): String? {
        if (depth > 3 || !allowed(url)) return null
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        val params = query.split('&').mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size != 2) null else runCatching {
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
            }.getOrNull()
        }
        val numbers = params.filter { it.first in keys }.map { it.second }.filter { it.matches(Regex("[0-9]{1,30}")) }.distinct()
        if (numbers.size > 1) return null
        if (numbers.size == 1) return numbers.first()
        return params.mapNotNull { (_, v) -> if (allowed(v)) number(v, depth + 1) else null }.distinct().singleOrNull()
    }
    fun parse(text: String): ShareDraft {
        val urls = urls(text)
        val number = urls.mapNotNull { number(it) }.distinct().singleOrNull()
        // Only a labeled line is a reliable suggested name. An unlabeled message remains editable raw text.
        val name = text.lineSequence().map(String::trim).firstOrNull { it.startsWith("가게명:") }?.substringAfter(':')?.trim().orEmpty()
        return ShareDraft(ShopIdentity(name, shopNo = number), urls.firstOrNull().orEmpty(), text)
    }
}
class ShareImportHandler {
    suspend fun receive(text: String): ShareDraft = withContext(Dispatchers.IO) {
        val parsed = ShareLinkParser.parse(text)
        if (parsed.identity.shopNo != null) return@withContext parsed
        val no = withTimeoutOrNull(6500) {
            ShareLinkParser.urls(text).firstNotNullOfOrNull { resolve(it) }
        }
        parsed.copy(identity = parsed.identity.copy(shopNo = no))
    }
    private fun resolve(initial: String): String? {
        var current = initial
        val deadline = System.nanoTime() + 5_000_000_000L
        repeat(4) {
            if (!ShareLinkParser.allowed(current) || System.nanoTime() >= deadline) return null
            ShareLinkParser.number(current)?.let { return it }
            val connection = (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 1200; readTimeout = 1200
                requestMethod = "HEAD"
                useCaches = false
                setRequestProperty("User-Agent", "BaeminBlack/1.0 LinkImport")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return null
                val location = connection.getHeaderField("Location") ?: return null
                current = URI(current).resolve(location).toString()
            } catch (_: Exception) { return null } finally { connection.disconnect() }
        }
        return ShareLinkParser.number(current)
    }
}
