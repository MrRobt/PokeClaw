// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

/**
 * Pure helpers for rendering capability badges on Muxi cloud models
 * (US-D-025-NATIVE-AUDIO-CAPABILITY-BADGE).
 *
 * This object is intentionally free of Android View dependencies so it
 * can be unit-tested in a plain JVM. UI code (e.g. ModelPickerActivity)
 * reads the returned [String] and applies its own background drawable.
 *
 * The `native_audio` label is hardcoded with a default emoji form, and
 * may be overridden at runtime by the operator (cloud-driven) via
 * [io.agents.pokeclaw.utils.KVUtils.getMuxiCapabilityNativeAudioLabel].
 */
object CapabilityBadge {

    /** Hardcoded default shown when the user/operator hasn't overridden the label. */
    const val NATIVE_AUDIO_DEFAULT_LABEL = "🎙️ 原生带配音"

    /**
     * Note appended to model-degradation warnings when a chosen Muxi model
     * lacks the `native_audio` capability, so the user knows a separate TTS
     * step would be required to produce a dubbed video.
     */
    const val MISSING_NATIVE_AUDIO_NOTE = "本模型无配音"

    /** True when the model declares the `native_audio` capability. */
    fun hasNativeAudio(model: MuxiModelInfo): Boolean =
        MuxiCapability.NATIVE_AUDIO in model.capabilities

    /** Capability-set form of [hasNativeAudio] (useful for capability lists). */
    fun hasNativeAudio(capabilities: Set<MuxiCapability>): Boolean =
        MuxiCapability.NATIVE_AUDIO in capabilities

    /**
     * Returns the badge text to display for [model], or null if no badge
     * should be shown. The label defaults to [NATIVE_AUDIO_DEFAULT_LABEL]
     * and is overridden by [overrideLabel] when it is non-blank.
     */
    fun badgeLabel(model: MuxiModelInfo, overrideLabel: String? = null): String? =
        if (hasNativeAudio(model)) effectiveLabel(overrideLabel) else null

    /** Capability-set form of [badgeLabel] for callers that already have a [Set]. */
    fun badgeLabelFor(
        capabilities: Set<MuxiCapability>,
        overrideLabel: String? = null,
    ): String? = if (hasNativeAudio(capabilities)) effectiveLabel(overrideLabel) else null

    /**
     * Returns the degradation-warning suffix to append for [model] when the
     * Muxi model lacks `native_audio` and the operator has chosen to
     * surface this. Returns an empty string when the capability IS present
     * or when the user has not opted into the warning at all.
     */
    fun degradationNote(model: MuxiModelInfo): String =
        if (hasNativeAudio(model)) "" else MISSING_NATIVE_AUDIO_NOTE

    private fun effectiveLabel(overrideLabel: String?): String {
        val trimmed = overrideLabel?.trim().orEmpty()
        return if (trimmed.isEmpty()) NATIVE_AUDIO_DEFAULT_LABEL else trimmed
    }
}
