package io.agents.pokeclaw.explore

/** One exploration action. [key] uniquely identifies it within a screen state. */
data class ExploreAction(
    val type: String,             // tap | input | swipe | scroll | back | home | launch
    val x: Int = 0,
    val y: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val targetText: String? = null,
    val targetType: String? = null,     // fine role of the target control
    val inputText: String? = null,
    val nodeSignature: String? = null,
    val depth: Int = 0,
) {
    fun key(): String = when (type) {
        "tap", "input" -> "$type:${nodeSignature ?: "$x,$y"}"
        "swipe", "scroll" -> "$type:$x,$y->$x2,$y2"
        "launch" -> "launch:${targetText ?: ""}"
        else -> type
    }
}
