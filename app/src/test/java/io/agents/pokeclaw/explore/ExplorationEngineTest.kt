package io.agents.pokeclaw.explore

import io.agents.pokeclaw.vision.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the engine against a tiny simulated app to prove dedup, coverage, the
 * unexplored queue, and bounded stepping — entirely off-device.
 *
 *   Home ──tap btnA──▶ Settings ──tap btnX──▶ About (leaf)
 *        └─tap btnB──▶ Detail (leaf)
 *        └─scroll (no-op)
 *   back pops to the parent.
 */
class ExplorationEngineTest {

    private fun node(id: String, x: Int, y: Int, scrollable: Boolean = false) = UiNode(
        className = "button", role = "interactable", fineRole = if (scrollable) "list" else "button",
        resourceId = id, left = x, top = y, right = x + 200, bottom = y + 80,
        clickable = !scrollable, scrollable = scrollable,
    )

    private val screens: Map<String, UiState> = mapOf(
        "home" to UiState("app", "Home", listOf(node("btnA", 100, 100), node("btnB", 100, 300), node("list", 0, 600, scrollable = true)), 1080, 2400),
        "settings" to UiState("app", "Settings", listOf(node("btnX", 100, 100)), 1080, 2400),
        "about" to UiState("app", "About", listOf(node("aboutText", 100, 100).copy(clickable = false, role = "text", fineRole = "text")), 1080, 2400),
        "detail" to UiState("app", "Detail", listOf(node("detailText", 100, 100).copy(clickable = false, role = "text", fineRole = "text")), 1080, 2400),
    )

    // navigation stack of screen names
    private val stack = ArrayDeque<String>().apply { addLast("home") }
    private fun cur() = stack.last()

    private fun freshState(): UiState {
        val s = screens.getValue(cur())
        return s.copy(nodes = s.nodes.toList())          // fresh instance (engine sets stateHash)
    }

    private fun act(a: ExploreAction): String {
        when {
            a.type == "back" -> { if (stack.size > 1) stack.removeLast(); return "ok" }
            a.type == "scroll" -> return "no_change"
            a.type == "tap" -> {
                val sig = a.nodeSignature ?: return "no_change"
                val next = when {
                    cur() == "home" && sig.contains("btnA") -> "settings"
                    cur() == "home" && sig.contains("btnB") -> "detail"
                    cur() == "settings" && sig.contains("btnX") -> "about"
                    else -> null
                }
                return if (next != null) { stack.addLast(next); "changed" } else "no_change"
            }
            else -> return "no_change"
        }
    }

    @Test
    fun exploresAllStatesWithDedupAndCoverage() {
        val config = ExplorerConfig(maxSteps = 40, maxDepth = 6, targetPackage = "app")
        lateinit var engine: ExplorationEngine
        engine = ExplorationEngine(
            config = config,
            observe = { freshState() },
            act = { act(it) },
            onState = { _, isNew -> if (isNew) engine.markCollected() },
        )

        val report = engine.run()

        // all four unique screens discovered and deduped
        assertEquals(4, report.uniqueStates)
        assertEquals(4, report.collectedSamples)
        assertEquals(setOf("Home", "Settings", "About", "Detail"), report.statesPerActivity.keys)
        assertTrue("should have revisited via back", report.revisits >= 1)
        assertTrue("bounded by maxSteps", report.steps <= config.maxSteps)

        // every *real* (non-back) action was explored; only back-frontier may remain
        assertTrue(
            "all real actions explored: " + engine.unexploredQueue(),
            engine.unexploredQueue().none { it.second.type != "back" },
        )
    }

    @Test
    fun respectsMaxSteps() {
        val engine = ExplorationEngine(
            ExplorerConfig(maxSteps = 3, maxDepth = 6, targetPackage = "app"),
            observe = { freshState() },
            act = { act(it) },
        )
        val report = engine.run()
        assertTrue(report.steps <= 3)
        assertTrue(report.unexploredQueued > 0)          // stopped early -> frontier remains
    }
}
