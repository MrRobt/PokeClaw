// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CapabilityBadge]. Verifies:
 *  - `native_audio` detection on a MuxiModelInfo and on raw capability sets
 *  - default label rendering and KV-driven override
 *  - degradation warning text only appears when native_audio is missing
 *  - override labels are trimmed and blank values fall back to the default
 */
class CapabilityBadgeTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    private val pixverseText = MuxiModelInfo(
        modelId = "pixverse-text",
        displayName = "PixVerse · 文生图",
        vendor = "pixverse",
        category = MuxiModelCategory.IMAGE_GENERATION,
        capabilities = setOf(MuxiCapability.TEXT_TO_IMAGE, MuxiCapability.NATIVE_AUDIO),
    )

    private val pixverseExtend = MuxiModelInfo(
        modelId = "pixverse-extend",
        displayName = "PixVerse · 视频延长",
        vendor = "pixverse",
        category = MuxiModelCategory.VIDEO_GENERATION,
        capabilities = setOf(MuxiCapability.VIDEO_EXTEND),
    )

    @Test
    fun hasNativeAudio_returnsTrueForModelWithCapability() {
        assertTrue(CapabilityBadge.hasNativeAudio(pixverseText))
    }

    @Test
    fun hasNativeAudio_returnsFalseForModelWithoutCapability() {
        assertFalse(CapabilityBadge.hasNativeAudio(pixverseExtend))
    }

    @Test
    fun hasNativeAudio_capabilitySetForm() {
        assertTrue(CapabilityBadge.hasNativeAudio(setOf(MuxiCapability.NATIVE_AUDIO)))
        assertTrue(
            CapabilityBadge.hasNativeAudio(
                setOf(MuxiCapability.TEXT_TO_IMAGE, MuxiCapability.NATIVE_AUDIO)
            )
        )
        assertFalse(CapabilityBadge.hasNativeAudio(setOf(MuxiCapability.TEXT_TO_IMAGE)))
        assertFalse(CapabilityBadge.hasNativeAudio(emptySet()))
    }

    @Test
    fun badgeLabel_defaultLabelForModelWithNativeAudio() {
        assertEquals(
            CapabilityBadge.NATIVE_AUDIO_DEFAULT_LABEL,
            CapabilityBadge.badgeLabel(pixverseText)
        )
    }

    @Test
    fun badgeLabel_nullForModelWithoutNativeAudio() {
        assertNull(CapabilityBadge.badgeLabel(pixverseExtend))
    }

    @Test
    fun badgeLabel_usesOverrideWhenNonBlank() {
        val override = "⚡ Native dub"
        assertEquals(override, CapabilityBadge.badgeLabel(pixverseText, override))
    }

    @Test
    fun badgeLabel_fallsBackToDefaultWhenOverrideBlank() {
        assertEquals(
            CapabilityBadge.NATIVE_AUDIO_DEFAULT_LABEL,
            CapabilityBadge.badgeLabel(pixverseText, "")
        )
        assertEquals(
            CapabilityBadge.NATIVE_AUDIO_DEFAULT_LABEL,
            CapabilityBadge.badgeLabel(pixverseText, "   ")
        )
    }

    @Test
    fun badgeLabel_trimsOverrideLabel() {
        assertEquals("⚡ Native dub", CapabilityBadge.badgeLabel(pixverseText, "  ⚡ Native dub  "))
    }

    @Test
    fun badgeLabelFor_capabilitySetForm_returnsDefault() {
        val out = CapabilityBadge.badgeLabelFor(setOf(MuxiCapability.NATIVE_AUDIO))
        assertNotNull(out)
        assertEquals(CapabilityBadge.NATIVE_AUDIO_DEFAULT_LABEL, out)
    }

    @Test
    fun badgeLabelFor_capabilitySetForm_returnsNull() {
        assertNull(CapabilityBadge.badgeLabelFor(setOf(MuxiCapability.TEXT_TO_IMAGE)))
        assertNull(CapabilityBadge.badgeLabelFor(emptySet()))
    }

    @Test
    fun badgeLabelFor_capabilitySetForm_usesOverride() {
        assertEquals(
            "🔥 With Audio",
            CapabilityBadge.badgeLabelFor(setOf(MuxiCapability.NATIVE_AUDIO), "🔥 With Audio")
        )
    }

    @Test
    fun degradationNote_emptyWhenModelHasNativeAudio() {
        assertEquals("", CapabilityBadge.degradationNote(pixverseText))
    }

    @Test
    fun degradationNote_returnsMissingNoteWhenAbsent() {
        assertEquals(
            CapabilityBadge.MISSING_NATIVE_AUDIO_NOTE,
            CapabilityBadge.degradationNote(pixverseExtend)
        )
    }

    @Test
    fun nativeAudioDefaultLabel_isNonEmpty() {
        assertTrue(CapabilityBadge.NATIVE_AUDIO_DEFAULT_LABEL.isNotBlank())
    }

    @Test
    fun missingNativeAudioNote_isNonEmpty() {
        assertTrue(CapabilityBadge.MISSING_NATIVE_AUDIO_NOTE.isNotBlank())
    }
}
