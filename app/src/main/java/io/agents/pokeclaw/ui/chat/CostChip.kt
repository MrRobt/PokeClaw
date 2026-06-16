// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

/**
 * Pure helpers for rendering a "花费 N 积分" cost chip on a chat message
 * (US-D-026-TASK-COST-CHIP-IN-CHAT).
 *
 * The data is sourced from a cloud `submitTaskResult` request whose DTO
 * (`TaskResultRequest.creditConsumed`) was added in this story. The DTO
 * may be null until the cloud fills it in; in that case the chip is
 * suppressed (no crash, no "0" chip).
 *
 * Color levels:
 *   - LOW    (< 10 credits)    — green
 *   - MEDIUM (10..99 credits)  — yellow/amber
 *   - HIGH   (>= 100 credits)  — red
 *
 * Implementation note: the JSON parser is regex-based (no `org.json`
 * dependency) so the same code is testable in plain JVM unit tests
 * where the Android JSON stub throws. The pattern is intentionally
 * tolerant — a missing field or a quoted number all parse cleanly.
 */
object CostChip {

    enum class Level { NONE, LOW, MEDIUM, HIGH }

    data class ChipInfo(
        val credits: Long,
        val text: String,
        val level: Level,
    )

    /** Hardcoded display template; the value [credits] is rendered verbatim. */
    const val CHIP_LABEL_TEMPLATE = "花费 %d 积分"

    /** Default low/medium/high thresholds. Operators can override per-product. */
    const val MEDIUM_THRESHOLD = 10L
    const val HIGH_THRESHOLD = 100L

    /**
     * Build a [ChipInfo] from an explicit credit count. Returns null when
     * the value is null or non-positive (no chip should be shown).
     */
    fun forCredits(credits: Long?): ChipInfo? {
        if (credits == null || credits <= 0L) return null
        val level = when {
            credits >= HIGH_THRESHOLD -> Level.HIGH
            credits >= MEDIUM_THRESHOLD -> Level.MEDIUM
            else -> Level.LOW
        }
        return ChipInfo(
            credits = credits,
            text = CHIP_LABEL_TEMPLATE.format(credits),
            level = level,
        )
    }

    // ---- Internal JSON parsing --------------------------------------------------
    //
    // The `result` payload is JSON; we only need one number. A regex is
    // simpler and dependency-free. The patterns are intentionally narrow:
    // we look for `"creditConsumed": <int>` either at the top level or
    // inside an inline `{"billing": { ... "creditConsumed": <int> }}`.
    //
    // Quoted numbers (`"creditConsumed": "12"`) are tolerated because
    // some serialization libraries stringify everything.

    private val TOP_LEVEL_CREDIT: Regex =
        Regex(""""creditConsumed"\s*:\s*"?(-?\d+)"?""")

    private val NESTED_BILLING_CREDIT: Regex =
        Regex(""""billing"\s*:\s*\{[^{}]*?"creditConsumed"\s*:\s*"?(-?\d+)"?""")

    /**
     * Parse a result payload and extract `creditConsumed`. Returns null
     * when the input is missing, malformed, or carries a non-positive
     * credit value. Top-level `creditConsumed` wins over the nested
     * `billing.creditConsumed` form.
     */
    fun parseFromResultJson(resultJson: String?): ChipInfo? {
        if (resultJson.isNullOrBlank()) return null
        val topLevel = extractLong(resultJson, TOP_LEVEL_CREDIT)
        if (topLevel != null) return forCredits(topLevel)
        val nested = extractLong(resultJson, NESTED_BILLING_CREDIT)
        if (nested != null) return forCredits(nested)
        return null
    }

    private fun extractLong(input: String, pattern: Regex): Long? {
        val m = pattern.find(input) ?: return null
        return m.groupValues[1].toLongOrNull()
    }
}
