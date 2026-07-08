package io.agents.pokeclaw.explore

import io.agents.pokeclaw.vision.UiNode

/** Enumerates candidate exploration actions for a screen, most-informative first. */
object ActionGenerator {
    fun generate(state: UiState, depth: Int, includeBack: Boolean = true): List<ExploreAction> {
        val actions = ArrayList<ExploreAction>()
        for (n in state.nodes) {
            if (!n.actionable || n.width <= 0 || n.height <= 0) continue
            actions.add(
                ExploreAction(
                    type = if (n.editable) "input" else "tap",
                    x = n.centerX, y = n.centerY,
                    targetText = n.text, targetType = n.fineRole,
                    inputText = if (n.editable) "pokeclaw" else null,
                    nodeSignature = signature(n), depth = depth,
                )
            )
        }
        // one scroll on the first scrollable container to reach off-screen content
        state.nodes.firstOrNull { it.scrollable }?.let { sc ->
            actions.add(
                ExploreAction(
                    type = "scroll",
                    x = sc.centerX, y = sc.top + sc.height * 3 / 4,
                    x2 = sc.centerX, y2 = sc.top + sc.height / 4,
                    targetType = "scroll", depth = depth,
                )
            )
        }
        if (includeBack) actions.add(ExploreAction(type = "back", depth = depth))
        return actions
    }

    fun signature(n: UiNode): String =
        "${n.role}:${n.fineRole}:${n.resourceId ?: n.text ?: ""}:${n.centerX / 40},${n.centerY / 40}"
}
