package io.agents.pokeclaw.explore

import io.agents.pokeclaw.vision.UiNode

/** A captured screen state during exploration. [stateHash] is filled by the engine. */
data class UiState(
    val pkg: String?,
    val activity: String?,
    val nodes: List<UiNode>,
    val screenW: Int,
    val screenH: Int,
) {
    var stateHash: String = ""
}
