// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.remake

/**
 * A single video-remake scene preset (US-D-028-REMAKE-SCENE-PICKER).
 *
 * Mirrors the columns of dyq `aigc_remake_scene`:
 *   - id                — stable id (e.g. "viral_generic")
 *   - name              — display name
 *   - description       — short pitch shown under the card
 *   - exampleImageUrl   — preview thumbnail (may be null while CDN is warming)
 *   - exampleInputs     — ordered key/value pairs that prefill the chat
 *                         input bar; rendered by [toExamplePrompt]
 *
 * The `exampleInputs` keys are domain-driven (e.g. "styleDescription",
 * "productDescription", "fixedScript"). This class does not enforce a
 * schema; the consumer (composer that fills the chat) decides the
 * canonical order via [EXAMPLE_INPUT_KEY_ORDER].
 */
data class RemakeScene(
    val id: String,
    val name: String,
    val description: String,
    val exampleImageUrl: String?,
    val exampleInputs: Map<String, String>,
) {
    /**
     * Render the example inputs as a single multi-line prompt suitable
     * for pasting into the chat input bar. The canonical key order
     * (defined in [EXAMPLE_INPUT_KEY_ORDER]) is preserved; extra keys
     * are appended in their natural iteration order so no data is lost.
     */
    fun toExamplePrompt(): String {
        if (exampleInputs.isEmpty()) return ""
        val lines = ArrayList<String>(exampleInputs.size)
        for (key in EXAMPLE_INPUT_KEY_ORDER) {
            val v = exampleInputs[key] ?: continue
            if (v.isBlank()) continue
            lines += "$key: $v"
        }
        for ((k, v) in exampleInputs) {
            if (k in EXAMPLE_INPUT_KEY_ORDER) continue
            if (v.isBlank()) continue
            lines += "$k: $v"
        }
        return lines.joinToString(separator = "\n")
    }

    companion object {
        /**
         * Canonical render order for the prompt lines. New keys can be
         * added at the end; missing keys are silently skipped. This list
         * is also used to drive UI layout hints in the picker sheet.
         */
        val EXAMPLE_INPUT_KEY_ORDER: List<String> = listOf(
            "styleDescription",
            "productDescription",
            "fixedScript",
            "referenceUrl",
            "extraNotes",
        )
    }
}
