package io.agents.pokeclaw.vision

/**
 * Client-side mirror of the hub `/api/v1/models/resolve` response. Carries the
 * routed **active** model plus an optional shadow-only candidate.
 */
data class YoloModelDescriptor(
    val softwareKey: String?,
    val packageName: String?,
    val modelId: String?,
    val version: Int,
    val status: String,       // active | candidate | archived
    val source: String,       // software_active | category | generic_fallback | none
    val sourceKind: String,   // software | category | generic
    val classes: List<String>,
    val defaultConfidence: Float,
    val confidenceThresholds: Map<String, Float>,
    val downloadUrl: String?,
    val checksum: String?,
    val sizeBytes: Long,
    val format: String,
    val needsData: Boolean,
    val updateAvailable: Boolean,
    val candidate: CandidateModel?,
) {
    val usable: Boolean get() = modelId != null
    val isGenericFallback: Boolean get() = source == "generic_fallback" || sourceKind == "generic"

    fun thresholdFor(cls: String): Float = confidenceThresholds[cls] ?: defaultConfidence
}

/** A candidate model attached to a route; may only shadow-run, never control. */
data class CandidateModel(
    val modelId: String,
    val version: Int,
    val checksum: String?,
    val downloadUrl: String?,
    val classes: List<String>,
    val shadowOnly: Boolean = true,
)
