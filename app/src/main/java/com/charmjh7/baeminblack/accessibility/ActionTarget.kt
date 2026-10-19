package com.charmjh7.baeminblack.accessibility

import com.charmjh7.baeminblack.matching.ShopIdentity

/** Both the current screen token and the freshly observed identity are required for writes. */
data class ActionTarget(val identity: ShopIdentity, val window: Int, val generation: Long) {
    fun accepts(current: ShopIdentity?, currentWindow: Int, currentGeneration: Long): Boolean =
        current != null && identity == current && window == currentWindow && generation == currentGeneration
}
