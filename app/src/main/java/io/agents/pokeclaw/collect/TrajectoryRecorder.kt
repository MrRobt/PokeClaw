package io.agents.pokeclaw.collect

/** One observation/action/result step of an exploration trajectory. */
data class TrajectoryStep(
    val stepIndex: Int,
    val pkg: String?,
    val activity: String?,
    val pageHash: String?,
    val actionType: String?,
    val actionTargetText: String?,
    val actionResult: String?,
    val boxCount: Int,
    val modelId: String?,
    val modelVersion: Int?,
)

/** In-memory trajectory for a collection session; serializes to JSON lines. */
class TrajectoryRecorder(val sessionId: String) {
    private val steps = ArrayList<TrajectoryStep>()

    fun record(step: TrajectoryStep) { steps.add(step) }
    fun steps(): List<TrajectoryStep> = steps.toList()
    fun size(): Int = steps.size

    fun toJsonLines(): String = steps.joinToString("\n") { s ->
        "{\"session\":${q(sessionId)},\"step\":${s.stepIndex},\"pkg\":${q(s.pkg)}," +
            "\"activity\":${q(s.activity)},\"page_hash\":${q(s.pageHash)}," +
            "\"action\":${q(s.actionType)},\"target\":${q(s.actionTargetText)}," +
            "\"result\":${q(s.actionResult)},\"boxes\":${s.boxCount}," +
            "\"model_id\":${q(s.modelId)},\"model_version\":${s.modelVersion ?: "null"}}"
    }

    private fun q(s: String?): String =
        if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
