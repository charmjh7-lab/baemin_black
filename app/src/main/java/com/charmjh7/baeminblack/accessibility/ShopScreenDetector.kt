package com.charmjh7.baeminblack.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.charmjh7.baeminblack.matching.ShopIdentity
import com.charmjh7.baeminblack.matching.ShopMatcher
import com.charmjh7.baeminblack.data.Shop
import com.charmjh7.baeminblack.share.ShareLinkParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ScreenNode(val index: Int, val parent: Int, val text: String, val resourceId: String, val bounds: Rect, val clickable: Boolean, val heading: Boolean, val visible: Boolean)
data class NodeSnapshot(val nodes: List<ScreenNode>, val viewport: Rect, val windowId: Int, val truncated: Boolean = false)
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
        val result = mutableListOf<ScreenNode>(); var truncated = false
        val viewport = Rect().also(root::getBoundsInScreen)
        suspend fun visit(node: AccessibilityNodeInfo, parent: Int, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (result.size >= MAX_NODES || depth > MAX_DEPTH) { truncated = true; return }
            val index = result.size
            val bounds = Rect().also(node::getBoundsInScreen)
            result += ScreenNode(index, parent, node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }, node.viewIdResourceName.orEmpty(), bounds, node.isClickable, node.isHeading, node.isVisibleToUser)
            for (i in 0 until node.childCount.coerceAtMost(150)) {
                if (result.size >= MAX_NODES) { truncated = true; break }
                val child = node.getChild(i) ?: continue
                try { visit(child, index, depth + 1) } finally { child.recycle() }
            }
        }
        visit(root, -1, 0)
        return NodeSnapshot(result, viewport, root.windowId, truncated)
    }
    fun detect(snapshot: NodeSnapshot, known: List<Shop>): ShopScreen {
        if (snapshot.truncated || snapshot.viewport.isEmpty) return ShopScreen.Unknown
        val nodes = snapshot.nodes
        val visible = nodes.filter { it.visible && !it.bounds.isEmpty && snapshot.viewport.contains(it.bounds) }
        fun descendants(parent: ScreenNode) = visible.filter { candidate ->
            var i = candidate.index; var depth = 0
            while (i >= 0 && depth++ <= MAX_DEPTH) { if (i == parent.index) return@filter true; i = nodes[i].parent }
            false
        }
        val nameId = Regex("(?i)(shop|store|restaurant)[_:/]*(name|title)|(?:name|title)[_:/]*(shop|store|restaurant)")
        val branchId = Regex("(?i)branch.*(name|title)|branch_name")
        val knownNames = known.flatMap { listOf(it.name, it.name + " " + it.branch) + it.aliases }.map(ShopMatcher::normalize).toSet()
        val names = visible.filter { n ->
            val text = n.text.trim()
            text.length in 2..100 && !text.contains('\n') && (nameId.containsMatchIn(n.resourceId) || ShopMatcher.normalize(text) in knownNames || n.heading)
        }
        fun identity(name: ScreenNode, region: List<ScreenNode>): ShopIdentity {
            var title = name.text.trim(); var branch = region.firstOrNull { branchId.containsMatchIn(it.resourceId) }?.text?.trim().orEmpty()
            if (branch.isBlank()) {
                val match = Regex("^(.+?)\\s+([^\\s]+점)$").matchEntire(title)
                if (match != null) { title = match.groupValues[1]; branch = match.groupValues[2] }
            }
            val no = region.asSequence().filter { it.text.contains("https://") }.mapNotNull { ShareLinkParser.parse(it.text).identity.shopNo }.distinct().toList().singleOrNull()
            return ShopIdentity(title, branch, no)
        }
        val cards = names.mapNotNull { name ->
            var parent = name.parent; var chosen: ScreenNode? = null
            repeat(6) {
                if (parent >= 0) {
                    val p = nodes[parent]
                    val region = descendants(p)
                    val otherTitles = names.count { it.index != name.index && region.any { child -> child.index == it.index } }
                    val metadata = region.any { Regex("배달|최소.?주문|리뷰|★|도착|[0-9]+분").containsMatchIn(it.text) }
                    if (chosen == null && p.clickable && otherTitles == 0 && metadata && p.bounds.height() >= name.bounds.height() * 2 && p.bounds.height() <= snapshot.viewport.height() * 0.6 && snapshot.viewport.contains(p.bounds)) chosen = p
                    parent = p.parent
                }
            }
            chosen?.let { card ->
                val region = descendants(card)
                val occupied = region.filter { it.index != card.index && it.index != name.index && (it.text.isNotBlank() || it.clickable) }.map { it.bounds }
                DetectedShop(identity(name, region), name.bounds, card.bounds, occupied)
            }
        }.distinctBy { it.cardBounds.toShortString() }
        if (cards.isNotEmpty()) return ShopScreen.Listing(cards)
        val detailMarkers = visible.count { it.text.trim() in setOf("메뉴", "리뷰", "가게정보", "가게 정보", "정보", "원산지", "주문하기") }
        val detailNames = names.filter { (nameId.containsMatchIn(it.resourceId) || it.heading) && it.bounds.top < snapshot.viewport.top + snapshot.viewport.height() * 0.55 }
            .distinctBy { ShopMatcher.normalize(it.text) }
        if (detailMarkers >= 2 && detailNames.size == 1) {
            val n = detailNames.single()
            return ShopScreen.Detail(DetectedShop(identity(n, visible), n.bounds, n.bounds, visible.filter { it.index != n.index && (it.text.isNotBlank() || it.clickable) && it.bounds.width() < snapshot.viewport.width() * 0.9 }.map { it.bounds }))
        }
        return ShopScreen.Unknown
    }
}
