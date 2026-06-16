// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

/**
 * Capability tags for Muxi cloud-side models
 * (US-D-024-MUXI-PIXVERSE-MODEL-REGISTRY).
 *
 * Codes mirror the `muxi_model_capability` rows and `system_dict_data`
 * entries seeded by the dyq 2026-06-10 prod bundle:
 *   - A1-3 dict `native_audio` (id 1780963200001, sort 90)
 *   - B-16/17/18 three PixVerse model rows
 *     (pixverse-extend / pixverse-text / pixverse-image-template)
 *
 * Cloud responses are parsed via [fromCode]; an unknown code becomes
 * [Unknown] so a new server-side capability never crashes the client.
 */
sealed class MuxiCapability(val code: String) {

    /** Extend an existing video by N seconds. (PixVerse extend model) */
    object VIDEO_EXTEND : MuxiCapability("video_extend")

    /** Generate an image from a text prompt. */
    object TEXT_TO_IMAGE : MuxiCapability("text_to_image")

    /** Generate an image from a reference image + prompt. */
    object IMAGE_TO_IMAGE : MuxiCapability("image_to_image")

    /** Model can emit a native audio track (e.g. dubbed video). */
    object NATIVE_AUDIO : MuxiCapability("native_audio")

    /** Forward-compat: cloud returned a code we don't know yet. */
    data class Unknown(val rawCode: String) : MuxiCapability(rawCode) {
        // Override data-class auto-toString so the Unknown variant matches
        // the base format used by the four singletons.
        override fun toString(): String = "MuxiCapability($rawCode)"
    }

    override fun toString(): String = "MuxiCapability($code)"

    companion object {
        /**
         * Parse a capability code from a cloud response.
         * Returns null for null/blank input; [Unknown] for unrecognized codes.
         * Comparison is case-insensitive and trims whitespace.
         */
        fun fromCode(code: String?): MuxiCapability? {
            if (code.isNullOrBlank()) return null
            val normalized = code.trim().lowercase()
            return when (normalized) {
                "video_extend" -> VIDEO_EXTEND
                "text_to_image" -> TEXT_TO_IMAGE
                "image_to_image" -> IMAGE_TO_IMAGE
                "native_audio" -> NATIVE_AUDIO
                else -> Unknown(normalized)
            }
        }

        /** All known (non-Unknown) capabilities, useful for badge / chip rendering. */
        fun known(): List<MuxiCapability> = listOf(
            VIDEO_EXTEND,
            TEXT_TO_IMAGE,
            IMAGE_TO_IMAGE,
            NATIVE_AUDIO,
        )
    }
}
