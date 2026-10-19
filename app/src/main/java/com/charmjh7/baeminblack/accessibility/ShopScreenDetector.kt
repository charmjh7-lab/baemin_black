package com.charmjh7.baeminblack.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.matching.ShopMatcher
import com.charmjh7.baeminblack.data.Shop
import com.charmjh7.baeminblack.share.ShareLinkParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ScreenNode(val index: Int, val parent: Int, val text: String, val resourceId: String, val bounds: Rect, val clickable: Boolean, val heading: Boolean, val visible: Boolean, val scrollable: Boolean = false, val className: String = "")
data class NodeSnapshot(val nodes: List<ScreenNode>, val viewport: Rect, val windowId: Int, val truncated: Boolean = false, val maxDepth: Int = 0)
data class DetectedShop(val identity: ShopIdentity, val titleBounds: Rect, val cardBounds: Rect, val occupiedBounds: List<Rect> = emptyList())
sealed interface ShopScreen {
    data class Listing(val shops: List<DetectedShop>) : ShopScreen
    data class Detail(val shop: DetectedShop) : ShopScreen
    data object Unknown : ShopScreen
}
class ShopScreenDetector {
    companion object { const val MAX_NODES = 1800; const val MAX_DEPTH = 28 }
    @Suppress("DEPRECATION")
    suspend fun snapshot(root: AccessibilityNodeInfo): NodeSnapshot {
        val result = mutableListOf<ScreenNode>(); var truncated = false; var maxDepth = 0
        val viewport = Rect().also(root::getBoundsInScreen)
        suspend fun visit(node: AccessibilityNodeInfo, parent: Int, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (result.size >= MAX_NODES || depth > MAX_DEPTH) { truncated = true; return }
            maxDepth = maxOf(maxDepth, depth)
            val index = result.size
            val bounds = Rect().also(node::getBoundsInScreen)
            result += ScreenNode(index, parent, node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }, node.viewIdResourceName.orEmpty(), bounds, node.isClickable, node.isHeading, node.isVisibleToUser, node.isScrollable, node.className?.toString().orEmpty())
            if (node.childCount > 150) truncated = true
            for (i in 0 until node.childCount.coerceAtMost(150)) {
                if (result.size >= MAX_NODES) { truncated = true; break }
                val child = node.getChild(i) ?: continue
                try { visit(child, index, depth + 1) } finally { child.recycle() }
            }
        }
        visit(root, -1, 0)
        return NodeSnapshot(result, viewport, root.windowId, truncated, maxDepth)
    }
    fun detect(snapshot: NodeSnapshot, known: List<Shop>): ShopScreen = analyze(snapshot, known).screen
    fun analyze(snapshot: NodeSnapshot, known: List<Shop>, recent: ShopIdentity? = null): DetectionReport {
        val candidates = mutableListOf<String>()
        val stats = linkedMapOf("nodes" to snapshot.nodes.size.toString(), "depth" to snapshot.maxDepth.toString(),
            "truncated" to snapshot.truncated.toString(), "windowId" to snapshot.windowId.toString(), "viewport" to snapshot.viewport.toShortString())
        fun result(screen: ShopScreen = ShopScreen.Unknown, reason: String = "") = DetectionReport(screen, reason, stats.toMap(), candidates.toList())
        if (snapshot.truncated) return result(reason = "SNAPSHOT_TRUNCATED")
        if (snapshot.viewport.isEmpty) return result(reason = "NO_VISIBLE_NODES")
        val nodes = snapshot.nodes
        val byIndex = nodes.associateBy { it.index }
        val visible = nodes.filter { it.visible && !it.bounds.isEmpty && snapshot.viewport.contains(it.bounds) }
        stats["visibleTextNodes"] = visible.count { it.text.isNotBlank() }.toString()
        if (visible.isEmpty()) return result(reason = "NO_VISIBLE_NODES")
        fun descendants(parent: ScreenNode) = visible.filter { candidate ->
            var i = candidate.index; var depth = 0
            while (i >= 0 && depth++ <= MAX_DEPTH) { if (i == parent.index) return@filter true; i = byIndex[i]?.parent ?: -1 }
            false
        }
        val nameId = Regex("(?i)(shop|store|restaurant)[_:/]*(name|title)|(?:name|title)[_:/]*(shop|store|restaurant)")
        val branchId = Regex("(?i)branch.*(name|title)|branch_name")
        val markers = setOf("메뉴", "리뷰", "가게정보", "가게 정보", "정보", "원산지", "주문하기")
        val generic = markers + setOf("배달", "포장", "검색", "검색 결과", "가게", "맛집", "장바구니", "홈", "찜", "주문내역", "주문 내역", "배달의민족", "전체", "추천", "인기", "음식배달")
        val metadata = Regex("배달|최소.?주문|리뷰|★|도착|[0-9]+분|평점|별점")
        val excluded = Regex("(?:[0-9,]+\\s*원|[0-9]+\\s*분|[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}|https?://|인증|주문번호|도로명|배달주소)")
        val knownNames = known.flatMap { listOf(it.name, it.name + " " + it.branch) + it.aliases }.map(ShopMatcher::key).toSet()
        val rejected = linkedMapOf<String, Int>()
        fun reject(reason: String): Boolean { rejected[reason] = (rejected[reason] ?: 0) + 1; return false }
        val possible = visible.filter { n ->
            val t = n.text.trim()
            when {
                t.length !in 2..100 || t.contains('\n') -> reject("TEXT_LENGTH_OR_MULTILINE")
                t in generic && !nameId.containsMatchIn(n.resourceId) -> reject("GENERIC_LABEL")
                excluded.containsMatchIn(t) || !t.any { it.isLetter() } -> reject("PRICE_CONTACT_OR_ORDER")
                Regex("[가-힣]+(?:로|길)\\s*\\d+|[가-힣]+[동읍면]\\s*\\d+").containsMatchIn(t) -> reject("ADDRESS")
                metadata.containsMatchIn(t) && !nameId.containsMatchIn(n.resourceId) && !n.heading -> reject("DELIVERY_METADATA")
                else -> true
            }
        }
        val medianHeight = visible.filter { it.text.isNotBlank() }.map { it.bounds.height() }.sorted().let { it.getOrNull(it.size / 2) ?: 1 }
        fun prominent(n: ScreenNode): Boolean = n.bounds.width() >= snapshot.viewport.width() * .25 &&
            n.bounds.height() >= medianHeight * 1.15 && n.bounds.top < snapshot.viewport.top + snapshot.viewport.height() * .45
        fun evidence(n: ScreenNode): Map<String, Int> {
            val near = visible.filter { it.index != n.index && it.bounds.top >= n.bounds.bottom && it.bounds.top - n.bounds.bottom < snapshot.viewport.height() * .18 }
            return linkedMapOf("resourceId" to if (nameId.containsMatchIn(n.resourceId)) 6 else 0,
                "heading" to if (n.heading) 4 else 0, "knownName" to if (ShopMatcher.key(n.text) in knownNames) 3 else 0,
                "topPosition" to if (n.bounds.top < snapshot.viewport.top + snapshot.viewport.height() * .4) 2 else 0,
                "nearMetadata" to if (near.any { metadata.containsMatchIn(it.text) }) 3 else 0,
                "nearDetailTab" to if (near.any { it.text.trim() in markers }) 2 else 0,
                "titleGeometry" to if (prominent(n)) 3 else 0,
                "recentDetail" to if (recent != null && ShopMatcher.compatible(ShopIdentity(n.text), recent)) 2 else 0)
        }
        val scores = possible.associate { it.index to evidence(it) }
        fun score(n: ScreenNode) = scores[n.index]?.values?.sum() ?: 0
        val names = possible.filter { score(it) >= 4 }
        stats["nameCandidates"] = names.size.toString()
        rejected.forEach { (k,v) -> stats["excluded.$k"] = v.toString() }
        stats["candidateNodes"] = names.joinToString(",") { "${it.index}:${score(it)}" }
        possible.take(50).forEach { node ->
            val related = visible.filter { it.parent == node.parent && it.index != node.index }.take(12).map { it.index }
            val explanation = "score=${score(node)},basis=${scores[node.index]},resourceId=${node.resourceId},bounds=${node.bounds.toShortString()},parent=${node.parent},related=$related,decision=${if (node in names) "CANDIDATE" else "SCORE_INSUFFICIENT"}"
            stats["candidate.${node.index}"] = explanation
            candidates += "node=${node.index},text=${node.text.take(160)},$explanation"
        }
        fun identity(name: ScreenNode, region: List<ScreenNode>): ShopIdentity {
            var title = name.text.trim(); var branch = region.firstOrNull { branchId.containsMatchIn(it.resourceId) }?.text?.trim().orEmpty()
            if (branch.isBlank()) {
                val match = Regex("^(.+?)\\s+([^\\s]+점)$").matchEntire(title)
                if (match != null) { title = match.groupValues[1]; branch = match.groupValues[2] }
            }
            val no = region.asSequence().filter { it.text.contains("https://") }.mapNotNull { ShareLinkParser.parse(it.text).identity.shopNo }.distinct().toList().singleOrNull()
            return ShopMatcher.canonical(ShopIdentity(title, branch, no))
        }
        val cards = names.mapNotNull { name ->
            var parent = name.parent; var chosen: ScreenNode? = null
            repeat(6) {
                val p = byIndex[parent]
                if (p != null) {
                    val region = descendants(p)
                    val otherTitles = names.count { it.index != name.index && region.any { child -> child.index == it.index } }
                    val hasMetadata = region.any { metadata.containsMatchIn(it.text) }
                    val hasStrongName = nameId.containsMatchIn(name.resourceId) || name.heading || ShopMatcher.key(name.text) in knownNames
                    if (chosen == null && otherTitles == 0 && (hasMetadata || (p.clickable && hasStrongName)) &&
                        p.bounds.height() >= name.bounds.height() * 2 && p.bounds.height() <= snapshot.viewport.height() * .6 &&
                        p.bounds.width() >= name.bounds.width() && snapshot.viewport.contains(p.bounds)) chosen = p
                    parent = p.parent
                }
            }
            chosen?.let { card ->
                val region = descendants(card)
                DetectedShop(identity(name, region), name.bounds, card.bounds,
                    region.filter { it.index != card.index && it.index != name.index && (it.text.isNotBlank() || it.clickable) }.map { it.bounds })
            }
        }.distinctBy { it.cardBounds.toShortString() }
        stats["cardCandidates"] = cards.size.toString()
        if (cards.size > 1) return result(ShopScreen.Listing(cards))
        val detailMarkers = visible.count { it.text.trim() in markers }
        stats["detailClues"] = detailMarkers.toString()
        val topNames = names.filter { it.bounds.top < snapshot.viewport.top + snapshot.viewport.height() * .55 }
            .groupBy { ShopMatcher.normalize(it.text) }.values.map { group -> group.maxBy { score(it) } }
            .sortedByDescending { score(it) }
        val best = topNames.firstOrNull()
        if (best == null) return result(reason = "NO_SHOP_NAME_CANDIDATE")
        val ambiguous = topNames.drop(1).any { score(it) >= score(best) - 1 }
        if (ambiguous) return result(reason = "AMBIGUOUS_SHOP_NAME")
        val nearbyMetadata = visible.any { it.bounds.top >= best.bounds.bottom && it.bounds.top - best.bounds.bottom < snapshot.viewport.height() * .2 && metadata.containsMatchIn(it.text) }
        val strongTitle = nameId.containsMatchIn(best.resourceId) || best.heading
        val searchStructure = visible.any { (it.className.endsWith("EditText") && it.bounds.top < best.bounds.top) || (it.resourceId.contains("search_input", true) && it.bounds.top < best.bounds.top) || it.text.trim() in setOf("검색", "검색 결과", "최근 검색어", "인기 검색어") }
        val repeatedClickableCards = visible.filter { it.clickable && it.bounds.height() in (best.bounds.height() * 2)..(snapshot.viewport.height() * .55).toInt() && it.bounds.width() > snapshot.viewport.width() * .65 }
            .groupBy { it.parent }.values.any { group -> group.size >= 2 && group.map { it.bounds.top }.distinct().size >= 2 }
        val contentRegions = visible.filter { it.index != best.index && it.bounds.top >= best.bounds.bottom && it.bounds.width() > snapshot.viewport.width() * .6 && it.bounds.height() > snapshot.viewport.height() * .2 &&
            (it.scrollable || descendants(it).count { child -> child.text.isNotBlank() } >= 2) }
        val sectionParents = visible.filter { it.bounds.top > best.bounds.bottom && it.text.isNotBlank() }.map { it.parent }.filter { it >= 0 }.distinct()
        val hierarchy = contentRegions.isNotEmpty() || (sectionParents.size >= 2 && visible.count { it.bounds.top > best.bounds.bottom && it.text.isNotBlank() } >= 4)
        val continuity = recent != null && ShopMatcher.compatible(identity(best, visible), recent)
        val headerGeometry = best.bounds.width() > snapshot.viewport.width() * .25 && best.bounds.top < snapshot.viewport.top + snapshot.viewport.height() * .4
        val structuralDetail = headerGeometry && hierarchy && (strongTitle || prominent(best) || continuity) && !searchStructure && !repeatedClickableCards
        stats["detail.structure"] = "header=$headerGeometry,hierarchy=$hierarchy,contentRegions=${contentRegions.size},sections=${sectionParents.size},repeatedCards=$repeatedClickableCards,search=$searchStructure,continuity=$continuity"
        stats["selectedNode"] = best.index.toString()
        stats["candidate.${best.index}.decision"] = if (structuralDetail) "STRUCTURAL_DETAIL" else "AWAIT_DETAIL_EVIDENCE"
        if (!searchStructure && ((detailMarkers >= 1 && (strongTitle || nearbyMetadata)) || (nearbyMetadata && score(best) >= 7) || structuralDetail)) {
            return result(ShopScreen.Detail(DetectedShop(identity(best, visible), best.bounds, snapshot.viewport,
                visible.filter { it.index != best.index && (it.text.isNotBlank() || it.clickable) && it.bounds.width() < snapshot.viewport.width() * .9 }.map { it.bounds })))
        }
        if (cards.isNotEmpty()) return result(ShopScreen.Listing(cards))
        return result(reason = if (detailMarkers == 0) "DETAIL_STRUCTURE_INSUFFICIENT" else "CARD_STRUCTURE_NOT_FOUND")
    }
}
data class DetectionReport(val screen: ShopScreen, val reason: String, val metrics: Map<String, String>, val candidates: List<String> = emptyList())
