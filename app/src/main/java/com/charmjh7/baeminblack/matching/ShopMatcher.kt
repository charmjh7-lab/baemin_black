package com.charmjh7.baeminblack.matching

import com.charmjh7.baeminblack.data.Shop
import java.text.Normalizer
import java.util.Locale

data class ShopIdentity(val name: String, val branch: String = "", val shopNo: String? = null)
object ShopMatcher {
    fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim()
    fun match(identity: ShopIdentity, shops: List<Shop>): Shop? {
        val number = identity.shopNo?.takeIf { it.isNotBlank() }
        if (number != null) {
            val numbered = shops.filter { it.shopNo == number }
            if (numbered.isNotEmpty()) return numbered.singleOrNull()
        }
        val candidates = shops.filter { number == null || it.shopNo == null || it.shopNo == number }
        val name = normalize(identity.name)
        if (name.isEmpty()) return null
        val branch = normalize(identity.branch)
        val full = listOf(name, branch).filter { it.isNotEmpty() }.joinToString(" ")
        val exact = candidates.filter {
            val n = normalize(it.name); val b = normalize(it.branch)
            (n == name && b == branch) || (branch.isEmpty() && b.isNotEmpty() && "$n $b" == name)
        }
        // Missing branch information never chooses one of several branches, including an unqualified record.
        val homonyms = candidates.filter { normalize(it.name) == name }
        if (branch.isEmpty() && homonyms.size > 1) return null
        if (exact.isNotEmpty()) return exact.singleOrNull()
        return candidates.filter { s -> s.aliases.any { normalize(it) == full } }.singleOrNull()
    }
}

data class MatchReport(val shop: Shop?, val reason: String, val basis: String, val candidates: Int)
fun ShopMatcher.explain(identity: ShopIdentity, shops: List<Shop>): MatchReport {
    val found = match(identity, shops)
    val names = shops.filter { normalize(it.name) == normalize(identity.name) || it.aliases.any { a -> normalize(a) == normalize(identity.name + " " + identity.branch) } }
    if (found != null) return MatchReport(found, "", if (identity.shopNo != null && identity.shopNo == found.shopNo) "SHOP_ID" else if (normalize(found.name) == normalize(identity.name)) "NAME_BRANCH" else "ALIAS", names.size)
    val reason = when {
        identity.name.isBlank() -> "INSUFFICIENT_IDENTITY"
        shops.isEmpty() -> "NO_RECORD"
        names.isEmpty() -> "NAME_MISMATCH"
        identity.shopNo != null && names.all { it.shopNo != null && it.shopNo != identity.shopNo } -> "SHOP_ID_MISMATCH"
        names.size > 1 -> "AMBIGUOUS_STORED_SHOPS"
        normalize(names.single().branch) != normalize(identity.branch) -> "BRANCH_MISMATCH"
        else -> "DUPLICATE_CANDIDATES"
    }
    return MatchReport(null, reason, "NONE", names.size)
}
