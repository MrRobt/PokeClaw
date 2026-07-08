package io.agents.pokeclaw.explore

/**
 * Deterministic auto-exploration core.
 *
 * Loop: observe current screen -> hash + dedup -> if new, enumerate its candidate
 * actions -> pick an untried action on the current screen (else back-track) ->
 * act -> re-observe. Repeats up to [ExplorerConfig.maxSteps]. Records unique
 * states, an unexplored-action queue, per-activity coverage, and revisits.
 *
 * All device I/O (observe/act) and per-step collection are injected, so the engine
 * runs and is unit-tested entirely off-device.
 */
class ExplorationEngine(
    private val config: ExplorerConfig,
    private val observe: () -> UiState,
    private val act: (ExploreAction) -> String,          // returns result: ok | no_change | error
    private val onState: (state: UiState, isNew: Boolean) -> Unit = { _, _ -> },
    private val onStep: (action: ExploreAction, result: String) -> Unit = { _, _ -> },
) {
    private val visited = HashSet<String>()
    private val generated = LinkedHashMap<String, List<ExploreAction>>()  // stateHash -> candidates
    private val tried = HashSet<String>()                                 // "stateHash#actionKey"
    private val statesPerActivity = LinkedHashMap<String, Int>()
    private var actionsExecuted = 0
    private var revisits = 0
    private var collected = 0

    /** Call from [onState]/[onStep] hooks when a sample is collected for a state. */
    fun markCollected() { collected++ }

    fun run(): CoverageReport {
        var steps = 0
        var depth = 0
        var state = observeHashed()
        ingest(state, depth)

        while (steps < config.maxSteps) {
            val action = pickUntried(state)
            if (action == null) {
                if (depth <= 0) break                       // fully explored reachable frontier
                act(ExploreAction("back", depth = depth)); actionsExecuted++; steps++; depth--
                state = observeHashed(); ingest(state, depth); continue
            }
            tried.add("${state.stateHash}#${action.key()}")
            val result = act(action)
            actionsExecuted++; steps++
            onStep(action, result)
            depth = if (action.type == "back") maxOf(0, depth - 1) else minOf(depth + 1, config.maxDepth)

            var obs = observeHashed()
            if (config.stayInTargetPackage && config.targetPackage != null &&
                obs.pkg != null && obs.pkg != config.targetPackage
            ) {
                act(ExploreAction("back", depth = depth)); actionsExecuted++
                obs = observeHashed(); depth = maxOf(0, depth - 1)
            }
            state = obs
            ingest(state, depth)
        }
        return report(steps)
    }

    private fun observeHashed(): UiState {
        val s = observe()
        s.stateHash = StateHasher.hash(s.pkg, s.activity, s.nodes)
        return s
    }

    private fun ingest(state: UiState, depth: Int) {
        val isNew = visited.add(state.stateHash)
        if (isNew) {
            statesPerActivity.merge(state.activity ?: state.pkg ?: "unknown", 1, Int::plus)
            generated[state.stateHash] = ActionGenerator.generate(state, depth, config.includeBack)
        } else {
            revisits++
        }
        onState(state, isNew)
    }

    private fun pickUntried(state: UiState): ExploreAction? {
        val cands = generated[state.stateHash] ?: return null
        // prefer non-back actions; fall back to back only when nothing else is left
        return cands.firstOrNull { it.type != "back" && untried(state.stateHash, it) }
            ?: cands.firstOrNull { it.type == "back" && untried(state.stateHash, it) }
    }

    private fun untried(hash: String, a: ExploreAction) = "$hash#${a.key()}" !in tried

    private fun report(steps: Int): CoverageReport {
        val unexplored = generated.entries.sumOf { (h, list) -> list.count { untried(h, it) } }
        return CoverageReport(
            steps = steps,
            uniqueStates = visited.size,
            actionsExecuted = actionsExecuted,
            unexploredQueued = unexplored,
            statesPerActivity = LinkedHashMap(statesPerActivity),
            collectedSamples = collected,
            revisits = revisits,
        )
    }

    /** The remaining unexplored (state, action) frontier — for the coverage page. */
    fun unexploredQueue(): List<Pair<String, ExploreAction>> =
        generated.entries.flatMap { (h, list) -> list.filter { untried(h, it) }.map { h to it } }
}
