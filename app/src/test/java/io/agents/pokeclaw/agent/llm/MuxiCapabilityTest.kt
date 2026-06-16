// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MuxiCapability] sealed class. Verifies round-trip parsing
 * of the four known codes (video_extend / text_to_image / image_to_image /
 * native_audio), case + whitespace normalization, and forward-compat for
 * unrecognized codes via the [MuxiCapability.Unknown] variant.
 */
class MuxiCapabilityTest {

    @org.junit.Before
    fun setUp() {
        // Disable android.util.Log writes so unit tests don't need a mock runtime.
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    @Test
    fun fromCode_returnsKnownSealedObjectForVideoExtend() {
        assertSame(MuxiCapability.VIDEO_EXTEND, MuxiCapability.fromCode("video_extend"))
    }

    @Test
    fun fromCode_returnsKnownSealedObjectForTextToImage() {
        assertSame(MuxiCapability.TEXT_TO_IMAGE, MuxiCapability.fromCode("text_to_image"))
    }

    @Test
    fun fromCode_returnsKnownSealedObjectForImageToImage() {
        assertSame(MuxiCapability.IMAGE_TO_IMAGE, MuxiCapability.fromCode("image_to_image"))
    }

    @Test
    fun fromCode_returnsKnownSealedObjectForNativeAudio() {
        assertSame(MuxiCapability.NATIVE_AUDIO, MuxiCapability.fromCode("native_audio"))
    }

    @Test
    fun fromCode_isCaseInsensitive() {
        assertSame(MuxiCapability.VIDEO_EXTEND, MuxiCapability.fromCode("Video_Extend"))
        assertSame(MuxiCapability.NATIVE_AUDIO, MuxiCapability.fromCode("NATIVE_AUDIO"))
        assertSame(MuxiCapability.TEXT_TO_IMAGE, MuxiCapability.fromCode("Text_To_Image"))
    }

    @Test
    fun fromCode_trimsSurroundingWhitespace() {
        assertSame(MuxiCapability.VIDEO_EXTEND, MuxiCapability.fromCode("  video_extend  "))
        assertSame(MuxiCapability.NATIVE_AUDIO, MuxiCapability.fromCode("\tnative_audio\n"))
        assertSame(MuxiCapability.IMAGE_TO_IMAGE, MuxiCapability.fromCode(" image_to_image "))
    }

    @Test
    fun fromCode_nullReturnsNull() {
        assertNull(MuxiCapability.fromCode(null))
    }

    @Test
    fun fromCode_blankReturnsNull() {
        assertNull(MuxiCapability.fromCode(""))
        assertNull(MuxiCapability.fromCode("   "))
        assertNull(MuxiCapability.fromCode("\t\n"))
    }

    @Test
    fun fromCode_unknownCodeReturnsUnknownPreservingNormalizedRaw() {
        val out = MuxiCapability.fromCode("future_capability_xyz")
        assertNotNull(out)
        assertTrue("expected Unknown variant", out is MuxiCapability.Unknown)
        assertEquals("future_capability_xyz", out!!.code)
    }

    @Test
    fun fromCode_unknownCodeIsAlsoNormalized() {
        // Whitespace + case are still normalized even for unknown codes.
        val out = MuxiCapability.fromCode("  Future_XYZ  ")
        assertNotNull(out)
        assertTrue(out is MuxiCapability.Unknown)
        assertEquals("future_xyz", out!!.code)
    }

    @Test
    fun toString_includesCode() {
        assertEquals("MuxiCapability(video_extend)", MuxiCapability.VIDEO_EXTEND.toString())
        assertEquals("MuxiCapability(native_audio)", MuxiCapability.NATIVE_AUDIO.toString())
        assertEquals("MuxiCapability(future_xyz)", MuxiCapability.Unknown("future_xyz").toString())
    }

    @Test
    fun known_listsAllFourKnownSealedObjects() {
        val list = MuxiCapability.known()
        assertEquals(4, list.size)
        assertTrue(MuxiCapability.VIDEO_EXTEND in list)
        assertTrue(MuxiCapability.TEXT_TO_IMAGE in list)
        assertTrue(MuxiCapability.IMAGE_TO_IMAGE in list)
        assertTrue(MuxiCapability.NATIVE_AUDIO in list)
    }
}
