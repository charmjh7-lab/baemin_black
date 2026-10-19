package com.charmjh7.baeminblack.share

import com.charmjh7.baeminblack.matching.ShopIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

data class ShareDraft(val identity: ShopIdentity, val url: String, val original: String)

object ShareLinkParser {
    private val hosts = setOf("baemin.com", "www.baemin.com", "baemin.go.link", "baemin.me", "www.baemin.me")
    private val numberKeys = setOf("shopDetail_shopNo", "shopNo", "shopNumber")
    private val nameKeys = setOf("shopName", "shop_name", "storeName", "store_name", "restaurantName", "shopDetail_shopName")
    private val urlPattern = Regex("""https://[^\s<>"\[\]]+""")
    private val labeledName = Regex("""^(?:가게명|상호명|매장명|업체명|식당명|가게 이름|상호)\s*[:：]\s*(.+)$""")
    private val shareSentence = Regex("""^(.{2,100}?)\s*(?:가게|매장)?(?:을|를)\s*공유(?:합니다|해요|했어요|드립니다)[.!]?$""")
    private val metaTags = Regex("""<meta\b[^>]{0,3000}>""", RegexOption.IGNORE_CASE)
    private val attributes = Regex("""([a-zA-Z][\w:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val titleTag = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in hosts &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun urls(text: String): List<String> = urlPattern.findAll(text)
        .map { it.value.trimEnd('.', ',', ')', '!', '\'', '>', ']', '。') }
        .filter(::allowed).distinct().toList()

    private fun params(url: String): List<Pair<String, String>> {
        if (!allowed(url)) return emptyList()
        val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyList()
        return rawQuery.split('&').mapNotNull {
            val pair = it.split('=', limit = 2)
            if (pair.size != 2) null else runCatching {
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }.getOrNull()
        }
    }

    fun number(url: String, depth: Int = 0): String? {
        if (depth > 3 || !allowed(url)) return null
        val query = params(url)
        val numbers = query.filter { it.first in numberKeys }.map { it.second }
            .filter { it.matches(Regex("[0-9]{1,30}")) }.distinct()
        if (numbers.size > 1) return null
        if (numbers.size == 1) return numbers.first()
        return query.mapNotNull { (_, value) -> if (allowed(value)) number(value, depth + 1) else null }
            .distinct().singleOrNull()
    }

    private fun unescape(value: String): String {
        val entities = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp);""", RegexOption.IGNORE_CASE)
        return entities.replace(value) { match ->
            val key = match.groupValues[1].lowercase()
            when (key) {
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                "nbsp" -> " "
                else -> runCatching {
                    val number = if (key.startsWith("#x")) key.drop(2).toInt(16) else key.drop(1).toInt()
                    if (!Character.isValidCodePoint(number)) match.value else String(Character.toChars(number))
                }.getOrDefault(match.value)
            }
        }
    }

    private fun candidate(raw: String): String? {
        var name = unescape(raw).replace(Regex("""\s+"""), " ").trim()
        name = name.replace(Regex("""^(?:\[배달의민족\]|\[배민\]|배달의민족|배민)\s*[-:|·]\s*"""), "")
            .replace(Regex("""\s*[-|·]\s*(?:배달의민족|배민)$"""), "")
            .trim().trim('"', '\'', '“', '”', '‘', '’', '[', ']')
        if (name.length !in 2..100 || name.contains("://") || name.contains('\n')) return null
        if (name in setOf("배달의민족", "배민", "가게 공유", "가게", "매장", "맛집")) return null
        if (listOf("무료배달", "배민클럽", "내가 찾던 맛집", "쿠폰", "공유하기", "함께 주문", "주문하러", "바로가기", "앱에서 확인", "링크를 확인").any(name::contains)) return null
        return name
    }

    fun nameFromText(text: String): String? {
        val lines = text.lineSequence().map { urlPattern.replace(it, "").trim() }
            .filter { it.isNotBlank() }.toList()
        lines.firstNotNullOfOrNull { labeledName.matchEntire(it)?.groupValues?.get(1)?.let(::candidate) }
            ?.let { return it }
        lines.firstNotNullOfOrNull { shareSentence.matchEntire(it)?.groupValues?.get(1)?.let(::candidate) }
            ?.let { return it }
        // A single non-boilerplate line accompanying a URL is commonly the shared shop title.
        if (urls(text).isNotEmpty() && lines.size == 1) {
            val line = lines.single().trim()
            if (!Regex("""[.!?]|(?:확인|공유|추천|주문|열어|보세요|할인|배민에서|배달의민족에서)""").containsMatchIn(line)) {
                return candidate(line)
            }
        }
        return null
    }

    fun nameFromUrl(url: String, depth: Int = 0): String? {
        if (depth > 3) return null
        val query = params(url)
        query.filter { it.first in nameKeys }.mapNotNull { candidate(it.second) }
            .distinct().singleOrNull()?.let { return it }
        return query.mapNotNull { (_, value) ->
            if (allowed(value)) nameFromUrl(value, depth + 1) else null
        }.distinct().singleOrNull()
    }

    fun nameFromHtml(html: String): String? {
        val tags = metaTags.findAll(html).map { match ->
            attributes.findAll(match.value).associate {
                it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty {
                    it.groupValues[3].ifEmpty { it.groupValues[4] }
                })
            }
        }.toList()
        for (property in listOf("og:title", "twitter:title")) {
            tags.firstNotNullOfOrNull { tag ->
                if (tag["property"].equals(property, true) || tag["name"].equals(property, true)) {
                    tag["content"]?.let(::candidate)
                } else null
            }?.let { return it }
        }
        return titleTag.find(html)?.groupValues?.get(1)?.let { candidate(it.replace(Regex("""<[^>]+>"""), "")) }
    }

    fun identity(name: String, shopNo: String?): ShopIdentity {
        val match = Regex("""^(.+?)\s+([^\s]+점)$""").matchEntire(name)
        return if (match != null) ShopIdentity(match.groupValues[1], match.groupValues[2], shopNo)
        else ShopIdentity(name, shopNo = shopNo)
    }

    fun parse(text: String, title: String = ""): ShareDraft {
        val links = urls(text)
        val shopNo = links.mapNotNull(::number).distinct().singleOrNull()
        val name = nameFromText(text) ?: links.mapNotNull(::nameFromUrl).distinct().singleOrNull()
            ?: nameFromText(title) ?: candidate(title).takeIf { title.isNotBlank() }
        return ShareDraft(identity(name.orEmpty(), shopNo), links.firstOrNull().orEmpty(), text)
    }
}

class ShareImportHandler {
    suspend fun receive(text: String, title: String = ""): ShareDraft = withContext(Dispatchers.IO) {
        val parsed = ShareLinkParser.parse(text, title)
        val links = ShareLinkParser.urls(text).take(3)
        if (links.isEmpty() || (parsed.identity.name.isNotBlank() && parsed.identity.shopNo != null)) {
            return@withContext parsed
        }
        val deadline = System.nanoTime() + 5_000_000_000L
        val resolved = withTimeoutOrNull(6500) { links.map { inspect(it, deadline) } }
            ?: emptyList()
        val numbers = (links.mapNotNull(ShareLinkParser::number) + resolved.mapNotNull { it.first })
            .distinct()
        val number = if (numbers.size == 1) numbers.single() else null
        val titleName = resolved.mapNotNull { it.second }.distinct().singleOrNull()
        val originalName = parsed.identity.name.ifBlank { titleName.orEmpty() }
        parsed.copy(identity = ShareLinkParser.identity(originalName, number))
    }

    /** Fetch only public, HTTPS, allowlisted Baemin pages. Never forward a shared URL to another host. */
    private fun inspect(initial: String, deadline: Long): Pair<String?, String?> {
        var current = initial
        var shopNo: String? = ShareLinkParser.number(current)
        repeat(4) {
            if (!ShareLinkParser.allowed(current) || System.nanoTime() >= deadline) return shopNo to null
            val connection = runCatching {
                (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 1200
                    readTimeout = 1200
                    useCaches = false
                    requestMethod = "GET"
                    setRequestProperty("Accept", "text/html")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; BaeminBlack/1.0)")
                }
            }.getOrNull() ?: return shopNo to null
            try {
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: return shopNo to null
                        val next = URI(current).resolve(location).toString()
                        if (!ShareLinkParser.allowed(next)) return shopNo to null
                        val fromNext = ShareLinkParser.number(next)
                        if (fromNext != null && (shopNo == null || shopNo == fromNext)) shopNo = fromNext
                        current = next
                    }
                    200 -> {
                        if (!connection.contentType.orEmpty().lowercase().contains("text/html")) return shopNo to null
                        val bytes = ByteArrayOutputStream()
                        connection.inputStream.use { stream ->
                            val buffer = ByteArray(4096)
                            while (bytes.size() < 96 * 1024 && System.nanoTime() < deadline) {
                                val count = stream.read(buffer, 0, minOf(buffer.size, 96 * 1024 - bytes.size()))
                                if (count <= 0) break
                                bytes.write(buffer, 0, count)
                            }
                        }
                        val html = bytes.toString(Charsets.UTF_8.name())
                        return shopNo to ShareLinkParser.nameFromHtml(html)
                    }
                    else -> return shopNo to null
                }
            } catch (_: Exception) {
                return shopNo to null
            } finally {
                connection.disconnect()
            }
        }
        return shopNo to null
    }
}
