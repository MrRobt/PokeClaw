// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.utils.XLog

/**
 * A Muxi cloud-side model entry. Distinct from on-device Gemma models in
 * [LocalModelManager]: Muxi models are invoked via the cloud backend and
 * never downloaded to the phone.
 */
data class MuxiModelInfo(
    val modelId: String,
    val displayName: String,
    val vendor: String,
    val category: MuxiModelCategory,
    val capabilities: Set<MuxiCapability>,
) {
    /** True if this model declares [cap] in its capability set. */
    fun hasCapability(cap: MuxiCapability): Boolean = cap in capabilities
}

/** Top-level grouping for a Muxi model; maps to the cloud `category` field. */
enum class MuxiModelCategory(val code: String) {
    VIDEO_GENERATION("video_generation"),
    IMAGE_GENERATION("image_generation"),
    AUDIO_GENERATION("audio_generation");

    companion object {
        private val BY_CODE: Map<String, MuxiModelCategory> =
            entries.associateBy { it.code }

        fun fromCode(code: String?): MuxiModelCategory? {
            if (code.isNullOrBlank()) return null
            return BY_CODE[code.trim().lowercase()]
        }
    }
}

/**
 * Registry of Muxi cloud-side models visible to the client
 * (US-D-024-MUXI-PIXVERSE-MODEL-REGISTRY).
 *
 * Seeded from the dyq 2026-06-10 prod bundle (B-16/17/18 + A1-3 dict):
 *   - pixverse-extend          (video_extend)
 *   - pixverse-text            (text_to_image + native_audio)
 *   - pixverse-image-template  (image_to_image)
 *
 * When the cloud Muxi API exposes `GET /muxi/capabilities`, [mergeFromCloud]
 * replaces the in-memory list with live data; until then the client can
 * demo and ship with the local seed. An empty cloud response reverts to
 * the seed so a transient network blip can never leave the picker empty.
 */
object MuxiModelRegistry {

    private const val TAG = "MuxiModelRegistry"

    /** Bundle-seeded PixVerse models (3 entries). Always non-empty. */
    val SEED_PIXVERSE: List<MuxiModelInfo> = listOf(
        MuxiModelInfo(
            modelId = "pixverse-extend",
            displayName = "PixVerse · 视频延长",
            vendor = "pixverse",
            category = MuxiModelCategory.VIDEO_GENERATION,
            capabilities = setOf(MuxiCapability.VIDEO_EXTEND),
        ),
        MuxiModelInfo(
            modelId = "pixverse-text",
            displayName = "PixVerse · 文生图",
            vendor = "pixverse",
            category = MuxiModelCategory.IMAGE_GENERATION,
            capabilities = setOf(
                MuxiCapability.TEXT_TO_IMAGE,
                MuxiCapability.NATIVE_AUDIO,
            ),
        ),
        MuxiModelInfo(
            modelId = "pixverse-image-template",
            displayName = "PixVerse · 图生图",
            vendor = "pixverse",
            category = MuxiModelCategory.IMAGE_GENERATION,
            capabilities = setOf(MuxiCapability.IMAGE_TO_IMAGE),
        ),
    )

    @Volatile
    private var live: List<MuxiModelInfo> = SEED_PIXVERSE

    /** All Muxi models currently visible to the user. */
    fun listAll(): List<MuxiModelInfo> = live

    /** Look up a Muxi model by its stable id. */
    fun findById(modelId: String?): MuxiModelInfo? {
        if (modelId.isNullOrBlank()) return null
        val key = modelId.trim()
        return live.firstOrNull { it.modelId == key }
    }

    /** Models that declare [cap] in their capability set. */
    fun byCapability(cap: MuxiCapability): List<MuxiModelInfo> =
        live.filter { cap in it.capabilities }

    /** Models in a given top-level category. */
    fun byCategory(category: MuxiModelCategory): List<MuxiModelInfo> =
        live.filter { it.category == category }

    /** Models for a specific vendor (e.g. "pixverse", "comfyui"). Case-insensitive. */
    fun byVendor(vendor: String): List<MuxiModelInfo> {
        val v = vendor.trim().lowercase()
        if (v.isEmpty()) return emptyList()
        return live.filter { it.vendor.equals(v, ignoreCase = true) }
    }

    /**
     * Replace the in-memory list after a successful cloud fetch. An empty
     * [models] list reverts to the bundle seed so a transient failure
     * (e.g. cloud returned `[]` on cold start) never blanks the picker.
     */
    fun mergeFromCloud(models: List<MuxiModelInfo>) {
        live = if (models.isEmpty()) SEED_PIXVERSE else models.toList()
        XLog.i(TAG, "merged from cloud: in=${models.size} total=${live.size}")
    }

    /** Reset to the bundle seed. Intended for unit tests. */
    internal fun resetForTests() {
        live = SEED_PIXVERSE
    }
}
