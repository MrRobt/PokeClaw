// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * The "source channel" of an AIGC template
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER). Maps to the
 * `aigc_template.channel_code` field defined in the dyq bundle
 * (A2-5): "pixverse" | "comfyui" | "muxi-canvas" | … + an ALL
 * pass-through and an UNKNOWN bucket.
 */
enum class TemplateChannel(val displayLabel: String) {
    /** Pseudo-value: do not filter. */
    ALL("全部"),

    /** PixVerse (muxi pixverse seeds — B-16/17/18). */
    PIXVERSE("PixVerse"),

    /** ComfyUI based templates. */
    COMFYUI("ComfyUI"),

    /** Muxi in-house canvas templates. */
    MUXI_CANVAS("Muxi Canvas"),

    /**
     * Anything whose `channelCode` is blank, null, or not in the
     * known set. Kept as an explicit filter bucket so users can
     * still find orphaned templates rather than losing them.
     */
    UNKNOWN("其他");

    companion object {
        /**
         * Parse a free-form `channelCode` string into a
         * [TemplateChannel]. Trims, lowercases, and treats null /
         * blank as [UNKNOWN]. Unknown values (e.g. "muxi-foo") also
         * land in [UNKNOWN] so the filter bucket stays meaningful.
         */
        fun fromString(code: String?): TemplateChannel {
            if (code.isNullOrBlank()) return UNKNOWN
            val normalized = code.trim().lowercase()
            return when (normalized) {
                "" -> UNKNOWN
                "pixverse" -> PIXVERSE
                "comfyui" -> COMFYUI
                "muxi-canvas" -> MUXI_CANVAS
                else -> UNKNOWN
            }
        }
    }
}
