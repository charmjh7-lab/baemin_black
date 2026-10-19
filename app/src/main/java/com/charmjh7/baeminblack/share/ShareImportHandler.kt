package com.charmjh7.baeminblack.share

import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.matching.ShopMatcher
import com.charmjh7.baeminblack.diagnostics.DiagnosticLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

data class ShareDraft(val identity: ShopIdentity, val url: String, val original: String,
    val sources: Map<String, String> = emptyMap(), val conflicts: List<String> = emptyList())
data class IdentityEvidence(val identity: ShopIdentity, val source: String)
object ShareIdentityMerger {
    fun merge(evidence: List<IdentityEvidence>): Pair<ShopIdentity, List<String>> {
        val primary = evidence.firstOrNull { it.identity.name.isNotBlank() } ?: evidence.firstOrNull()
            ?: return ShopIdentity("") to emptyList()
        val chosen = ShopMatcher.canonical(primary.identity)
        val conflicts = mutableListOf<String>()
        val compatible = evidence.filter { item ->
            val sameId = chosen.shopNo != null && chosen.shopNo == item.identity.shopNo
            val fits = sameId || ShopMatcher.compatible(chosen, item.identity)
            if (!fits) conflicts += if (chosen.shopNo != null && item.identity.shopNo != null && chosen.shopNo != item.identity.shopNo) "SHOP_ID_CONFLICT" else "SHOP_NAME_OR_BRANCH_CONFLICT"
            fits
        }
        val numbers = evidence.mapNotNull { it.identity.shopNo }.distinct()
        if (numbers.size > 1) conflicts += "SHOP_ID_CONFLICT"
        // Never attach an unrelated URL's number/branch to the name selected from another source.
        val number = if (numbers.size > 1) null else if (conflicts.isEmpty()) numbers.singleOrNull() else chosen.shopNo
        val branch = chosen.branch.ifBlank { if (conflicts.isEmpty()) compatible.map { ShopMatcher.canonical(it.identity).branch }.filter(String::isNotBlank).distinctBy(ShopMatcher::key).singleOrNull().orEmpty() else "" }
        return chosen.copy(branch = branch, shopNo = number) to conflicts.distinct()
    }
}

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

    fun parameterNames(url: String): List<String> = params(url).map { it.first.take(60) }.distinct().take(30)
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
        name = name.replace(Regex("""^\[(?:배달의민족|배민)\]\s*[-:|·]?\s*"""), "")
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
        if (urls(text).isNotEmpty()) {
            val candidates = lines.mapNotNull { line ->
                if (Regex("""[.!?]|(?:확인|공유|추천|주문하|주문해|열어|보세요|할인|배민에서|배달의민족에서)""").containsMatchIn(line)) null
                else candidate(line)
            }.distinctBy(ShopMatcher::key)
            return candidates.singleOrNull()
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

    fun identity(name: String, shopNo: String?): ShopIdentity = ShopMatcher.canonical(ShopIdentity(name, shopNo = shopNo))

    fun parse(text: String, title: String = "", extraTitle: String = ""): ShareDraft {
        val links = (urls(text) + urls(title) + urls(extraTitle)).distinct()
        val evidence = mutableListOf<IdentityEvidence>()
        nameFromText(text)?.let { evidence += IdentityEvidence(identity(it, null), "SHARE_TEXT") }
        links.forEach { url -> evidence += IdentityEvidence(identity(nameFromUrl(url).orEmpty(), number(url)), "URL_PARAMETERS") }
        for ((value, source) in listOf(title to "EXTRA_SUBJECT", extraTitle to "EXTRA_TITLE")) {
            (nameFromText(value) ?: candidate(urlPattern.replace(value, "")).takeIf { value.isNotBlank() })?.let {
                evidence += IdentityEvidence(identity(it, null), source)
            }
        }
        val (merged, conflicts) = ShareIdentityMerger.merge(evidence)
        val nameSource = evidence.firstOrNull { it.identity.name.isNotBlank() && ShopMatcher.compatible(merged, it.identity) }?.source.orEmpty()
        val noSource = evidence.firstOrNull { it.identity.shopNo != null && it.identity.shopNo == merged.shopNo }?.source.orEmpty()
        return ShareDraft(merged, links.firstOrNull().orEmpty(), text, mapOf("name" to nameSource, "shopNo" to noSource), conflicts)
    }
}

class ShareImportHandler {
    suspend fun receive(text: String, title: String = "", extraTitle: String = "", logger: DiagnosticLogger? = null,
        trace: String = "", resolvePublicLinks: Boolean = true): ShareDraft = withContext(Dispatchers.IO) {
        val parsed = ShareLinkParser.parse(text, title, extraTitle)
        val links = (ShareLinkParser.urls(text) + ShareLinkParser.urls(title) + ShareLinkParser.urls(extraTitle)).distinct().take(3)
        logger?.event("share", "SHARE_PAYLOAD_PARSED", trace = trace, details = mapOf("textPresent" to text.isNotBlank().toString(),
            "textLength" to text.length.toString(), "subjectPresent" to title.isNotBlank().toString(), "titlePresent" to extraTitle.isNotBlank().toString(),
            "urlCount" to links.size.toString(), "conflicts" to parsed.conflicts.joinToString(",")))
        logger?.sensitive("share_payload", trace, mapOf("text" to text.take(2000), "subject" to title.take(200), "title" to extraTitle.take(200)))
        links.forEach { url -> logger?.event("share", "SHARE_URL_FOUND", trace = trace, details = mapOf("host" to URI(url).host.orEmpty(), "parameterNames" to ShareLinkParser.parameterNames(url).joinToString(","))) }
        if (links.isEmpty() || !resolvePublicLinks || (parsed.identity.name.isNotBlank() && parsed.identity.shopNo != null) || parsed.conflicts.isNotEmpty()) {
            logger?.event("share", "SHARE_URL_RESOLVED", parsed.conflicts.isEmpty(),
                if (links.isEmpty()) "NO_ALLOWED_URL" else if (parsed.conflicts.isNotEmpty()) "PAYLOAD_CONFLICT" else "NETWORK_NOT_REQUIRED", trace,
                mapOf("networkAttempted" to "false"))
            return@withContext parsed
        }
        val deadline = System.nanoTime() + 5_000_000_000L
        val resolved = withTimeoutOrNull(6500) { links.map { inspect(it, deadline, logger, trace) } } ?: emptyList()
        if (resolved.isEmpty() && links.isNotEmpty()) logger?.event("share", "SHARE_URL_RESOLVED", false, "RESOLUTION_TIMEOUT_OR_EMPTY", trace)
        val evidence = listOf(IdentityEvidence(parsed.identity, "PARSED_PAYLOAD")) + resolved.map { IdentityEvidence(ShopMatcher.canonical(ShopIdentity(it.second.orEmpty(), shopNo = it.first)), "PUBLIC_LINK_PREVIEW") }
        val (merged, conflicts) = ShareIdentityMerger.merge(evidence)
        parsed.copy(identity = merged, conflicts = parsed.conflicts + conflicts, sources = parsed.sources + mapOf(
            "name" to if (parsed.identity.name.isNotBlank()) parsed.sources["name"].orEmpty() else if (merged.name.isNotBlank()) "PUBLIC_LINK_PREVIEW" else "",
            "shopNo" to if (parsed.identity.shopNo != null) parsed.sources["shopNo"].orEmpty() else if (merged.shopNo != null) "PUBLIC_LINK_PREVIEW" else ""))
    }

    /** Fetch only public, HTTPS, allowlisted Baemin pages. Never forward a shared URL to another host. */
    private fun inspect(initial: String, deadline: Long, logger: DiagnosticLogger?, trace: String): Pair<String?, String?> {
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
                val code = connection.responseCode
                logger?.event("share", "SHARE_URL_RESOLVED", code in 200..399, if (code in 200..399) "" else "HTTP_STATUS", trace,
                    mapOf("host" to URI(current).host.orEmpty(), "statusCode" to code.toString(), "redirectStep" to it.toString()))
                when (code) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: return shopNo to null
                        val next = URI(current).resolve(location).toString()
                        if (!ShareLinkParser.allowed(next)) {
                            logger?.event("share", "SHARE_URL_RESOLVED", false, "REDIRECT_HOST_REJECTED", trace)
                            return shopNo to null
                        }
                        val fromNext = ShareLinkParser.number(next)
                        if (fromNext != null && shopNo != null && shopNo != fromNext) {
                            logger?.event("share", "SHARE_URL_RESOLVED", false, "REDIRECT_SHOP_ID_CONFLICT", trace)
                            return null to null
                        }
                        if (fromNext != null) shopNo = fromNext
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
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logger?.event("share", "SHARE_URL_RESOLVED", false, "LINK_RESOLUTION_EXCEPTION", trace, details = mapOf("host" to URI(current).host.orEmpty()), error = e)
                return shopNo to null
            } finally {
                connection.disconnect()
            }
        }
        return shopNo to null
    }
}
