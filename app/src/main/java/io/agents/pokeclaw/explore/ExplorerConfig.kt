package io.agents.pokeclaw.explore

/** Tunables for an exploration session. */
data class ExplorerConfig(
    val maxSteps: Int = 30,
    val maxDepth: Int = 6,
    val targetSoftwareKey: String? = null,
    val targetPackage: String? = null,
    val targetPage: String? = null,
    val settleMs: Long = 800,
    val includeBack: Boolean = true,
    val stayInTargetPackage: Boolean = true,
)
