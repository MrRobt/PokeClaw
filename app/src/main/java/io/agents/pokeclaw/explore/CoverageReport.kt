package io.agents.pokeclaw.explore

/** Coverage summary produced at the end of an exploration session. */
data class CoverageReport(
    val steps: Int,
    val uniqueStates: Int,
    val actionsExecuted: Int,
    val unexploredQueued: Int,
    val statesPerActivity: Map<String, Int>,
    val collectedSamples: Int,
    val revisits: Int,
) {
    fun summary(): String =
        "steps=$steps states=$uniqueStates actions=$actionsExecuted " +
            "unexplored=$unexploredQueued samples=$collectedSamples revisits=$revisits"
}
