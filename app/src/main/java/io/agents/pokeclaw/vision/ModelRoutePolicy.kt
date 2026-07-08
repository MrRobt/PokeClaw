package io.agents.pokeclaw.vision

/**
 * Client-side mirror of the hub routing priority. The hub is authoritative, but
 * this keeps the same rules on-device for offline routing and — critically —
 * enforces that a **candidate** model may only shadow-run, never control actions.
 */
object ModelRoutePolicy {
    enum class Source { SOFTWARE_ACTIVE, CATEGORY, GENERIC, NONE }

    data class Available(
        val softwareActive: YoloModelDescriptor? = null,
        val categoryActive: YoloModelDescriptor? = null,
        val generic: YoloModelDescriptor? = null,
    )

    data class Decision(
        val controlling: YoloModelDescriptor?,
        val source: Source,
        val needsData: Boolean,
    )

    /** Priority: software active -> category -> generic. */
    fun choose(available: Available): Decision {
        available.softwareActive?.let { return Decision(it, Source.SOFTWARE_ACTIVE, false) }
        available.categoryActive?.let { return Decision(it, Source.CATEGORY, true) }
        available.generic?.let { return Decision(it, Source.GENERIC, true) }
        return Decision(null, Source.NONE, true)
    }

    /** The model allowed to drive clicks: must be active, never a candidate. */
    fun controllingModel(desc: YoloModelDescriptor?): YoloModelDescriptor? =
        desc?.takeIf { it.usable && it.status != "candidate" }

    /** The candidate to shadow-run for evaluation only (no control). */
    fun shadowCandidate(desc: YoloModelDescriptor?): CandidateModel? =
        desc?.candidate?.takeIf { it.shadowOnly }
}
