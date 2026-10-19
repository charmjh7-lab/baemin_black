package com.charmjh7.baeminblack.matching

import com.charmjh7.baeminblack.data.Shop
import java.text.Normalizer
import java.util.Locale

data class ShopIdentity(val name: String, val branch: String = "", val shopNo: String? = null)
data class MatchReport(val shop: Shop?, val reason: String, val basis: String, val candidates: Int,
    val details: Map<String, String> = emptyMap())
object ShopMatcher {
    fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim()
    fun key(value: String): String = normalize(value).replace(" ", "")
    /** Split only an explicit branch suffix, never guess a branch from an arbitrary brand substring. */
    fun canonical(identity: ShopIdentity): ShopIdentity {
        val text = identity.name.trim()
        val suffix = Regex("""^(.+?)(?:\s+|[·|/_-]+|\s*\()([^\s()·|/_-]+점)\)?$""").matchEntire(text)
        if (suffix != null && (identity.branch.isBlank() || key(identity.branch) == key(suffix.groupValues[2])))
            return identity.copy(name = suffix.groupValues[1].trim(), branch = suffix.groupValues[2].trim())
        return identity.copy(name = text, branch = identity.branch.trim())
    }
    fun sameBase(a: ShopIdentity, b: ShopIdentity) = key(canonical(a).name) == key(canonical(b).name)
    private fun full(identity: ShopIdentity): String { val c = canonical(identity); return key(c.name) + key(c.branch) }
    fun compatible(a: ShopIdentity, b: ShopIdentity): Boolean {
        if (a.shopNo != null && b.shopNo != null && a.shopNo != b.shopNo) return false
        if (a.name.isBlank() || b.name.isBlank()) return true
        val ca = canonical(a); val cb = canonical(b)
        if (ca.branch.isNotBlank() && cb.branch.isNotBlank() && key(ca.branch) != key(cb.branch)) return false
        return full(ca) == full(cb) || (key(ca.name) == key(cb.name) && (ca.branch.isBlank() || cb.branch.isBlank()))
    }
    fun match(identity: ShopIdentity, shops: List<Shop>): Shop? = explain(identity, shops).shop
    fun explain(identity: ShopIdentity, shops: List<Shop>): MatchReport {
        val seen = canonical(identity)
        val number = seen.shopNo?.takeIf(String::isNotBlank)
        val name = key(seen.name); val branch = key(seen.branch); val fullName = full(seen)
        val comparisons = shops.map { shop ->
            val stored = canonical(ShopIdentity(shop.name, shop.branch, shop.shopNo))
            val nameEqual = key(stored.name) == name
            val branchEqual = key(stored.branch) == branch
            val fullEqual = full(stored) == fullName
            val idConflict = number != null && shop.shopNo != null && number != shop.shopNo
            val aliasEqual = shop.aliases.any { key(it) == fullName } && (branch.isBlank() || key(stored.branch) == branch)
            Comparison(shop, nameEqual, branchEqual, fullEqual, idConflict, aliasEqual)
        }
        val related = comparisons.filter { it.nameEqual || it.fullEqual || it.aliasEqual || (number != null && it.shop.shopNo == number) }
        val details = linkedMapOf("nameCandidates" to related.size.toString(), "normalizedNamePresent" to name.isNotBlank().toString(),
            "branchPresent" to branch.isNotBlank().toString(), "exactCount" to comparisons.count { it.fullEqual && !it.idConflict }.toString(),
            "aliasCount" to comparisons.count { it.aliasEqual && !it.idConflict }.toString(),
            "idConflictCount" to comparisons.count { it.idConflict }.toString(),
            "comparisons" to comparisons.take(100).joinToString(";") { "${it.shop.id}:name=${it.nameEqual},branch=${it.branchEqual},full=${it.fullEqual},alias=${it.aliasEqual},idConflict=${it.idConflict}" })
        fun answer(shop: Shop? = null, reason: String = "", basis: String = "NONE") = MatchReport(shop, reason, basis, related.size, details)
        if (number != null) {
            val numbered = comparisons.filter { it.shop.shopNo == number }
            if (numbered.size == 1) return answer(numbered.single().shop, basis = "SHOP_ID")
            if (numbered.size > 1) return answer(reason = "DUPLICATE_ID_CANDIDATES")
        }
        if (name.isBlank()) return answer(reason = "INSUFFICIENT_IDENTITY")
        val eligible = comparisons.filterNot { it.idConflict }
        // An omitted branch never selects among homonymous locations, even if one has no branch saved.
        if (branch.isBlank() && eligible.count { it.nameEqual } > 1) return answer(reason = "AMBIGUOUS_STORED_SHOPS")
        val exact = eligible.filter { it.fullEqual }
        if (exact.size == 1) return answer(exact.single().shop, basis = "NAME_BRANCH")
        if (exact.size > 1) return answer(reason = "DUPLICATE_CANDIDATES")
        val aliases = eligible.filter { it.aliasEqual }
        if (aliases.size == 1) return answer(aliases.single().shop, basis = "ALIAS")
        if (aliases.size > 1) return answer(reason = "DUPLICATE_ALIAS_CANDIDATES")
        return answer(reason = when {
            shops.isEmpty() -> "NO_RECORD"
            related.any { it.idConflict } -> "SHOP_ID_MISMATCH"
            eligible.any { it.nameEqual } -> "BRANCH_MISMATCH"
            else -> "NAME_MISMATCH"
        })
    }
    private data class Comparison(val shop: Shop, val nameEqual: Boolean, val branchEqual: Boolean,
        val fullEqual: Boolean, val idConflict: Boolean, val aliasEqual: Boolean)
}
