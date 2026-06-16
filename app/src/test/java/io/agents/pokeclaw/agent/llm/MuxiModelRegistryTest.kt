// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MuxiModelRegistry] — the bundle-seeded PixVerse registry
 * (US-D-024-MUXI-PIXVERSE-MODEL-REGISTRY). Verifies the three modelIds are
 * present, capability / category / vendor lookups return the right rows,
 * and [mergeFromCloud] both replaces and gracefully reverts to the seed.
 */
class MuxiModelRegistryTest {

    @org.junit.Before
    fun setUp() {
        // Disable android.util.Log writes so unit tests don't need a mock runtime.
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @After
    fun tearDown() {
        MuxiModelRegistry.resetForTests()
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    @Test
    fun seed_hasThreePixVerseModels() {
        assertEquals(3, MuxiModelRegistry.SEED_PIXVERSE.size)
        val ids = MuxiModelRegistry.SEED_PIXVERSE.map { it.modelId }.toSet()
        assertTrue("pixverse-extend missing from seed", "pixverse-extend" in ids)
        assertTrue("pixverse-text missing from seed", "pixverse-text" in ids)
        assertTrue("pixverse-image-template missing from seed", "pixverse-image-template" in ids)
    }

    @Test
    fun listAll_initiallyReturnsSeed() {
        val all = MuxiModelRegistry.listAll()
        assertEquals(3, all.size)
        assertEquals(MuxiModelRegistry.SEED_PIXVERSE.map { it.modelId }.toSet(),
            all.map { it.modelId }.toSet())
    }

    @Test
    fun findById_knownId_returnsModel() {
        val out = MuxiModelRegistry.findById("pixverse-text")
        assertNotNull(out)
        assertEquals("PixVerse · 文生图", out!!.displayName)
        assertEquals("pixverse", out.vendor)
        assertEquals(MuxiModelCategory.IMAGE_GENERATION, out.category)
    }

    @Test
    fun findById_unknownId_returnsNull() {
        assertNull(MuxiModelRegistry.findById("not-a-real-model"))
    }

    @Test
    fun findById_nullAndBlank_returnsNull() {
        assertNull(MuxiModelRegistry.findById(null))
        assertNull(MuxiModelRegistry.findById(""))
        assertNull(MuxiModelRegistry.findById("   "))
    }

    @Test
    fun byCapability_nativeAudio_returnsPixverseText() {
        val out = MuxiModelRegistry.byCapability(MuxiCapability.NATIVE_AUDIO)
        assertEquals(1, out.size)
        assertEquals("pixverse-text", out[0].modelId)
    }

    @Test
    fun byCapability_videoExtend_returnsPixverseExtend() {
        val out = MuxiModelRegistry.byCapability(MuxiCapability.VIDEO_EXTEND)
        assertEquals(1, out.size)
        assertEquals("pixverse-extend", out[0].modelId)
    }

    @Test
    fun byCapability_imageToImage_returnsPixverseImageTemplate() {
        val out = MuxiModelRegistry.byCapability(MuxiCapability.IMAGE_TO_IMAGE)
        assertEquals(1, out.size)
        assertEquals("pixverse-image-template", out[0].modelId)
    }

    @Test
    fun byCategory_videoGeneration_returnsPixverseExtend() {
        val out = MuxiModelRegistry.byCategory(MuxiModelCategory.VIDEO_GENERATION)
        assertEquals(1, out.size)
        assertEquals("pixverse-extend", out[0].modelId)
    }

    @Test
    fun byCategory_imageGeneration_returnsTwoPixverseModels() {
        val out = MuxiModelRegistry.byCategory(MuxiModelCategory.IMAGE_GENERATION)
        assertEquals(2, out.size)
        val ids = out.map { it.modelId }.toSet()
        assertTrue("pixverse-text missing", "pixverse-text" in ids)
        assertTrue("pixverse-image-template missing", "pixverse-image-template" in ids)
    }

    @Test
    fun byVendor_pixverse_returnsAllThree() {
        val out = MuxiModelRegistry.byVendor("pixverse")
        assertEquals(3, out.size)
    }

    @Test
    fun byVendor_otherVendor_returnsEmpty() {
        val out = MuxiModelRegistry.byVendor("comfyui")
        assertTrue(out.isEmpty())
    }

    @Test
    fun byVendor_isCaseInsensitive() {
        // "PixVerse" is mixed-case; the all-uppercase form of "pixverse" must
        // also hit the case-insensitive filter and return all 3 seeded models.
        val upper = "pixverse".uppercase()
        assertEquals(3, MuxiModelRegistry.byVendor("PixVerse").size)
        assertEquals(3, MuxiModelRegistry.byVendor(upper).size)
    }

    @Test
    fun byVendor_blankReturnsEmpty() {
        assertTrue(MuxiModelRegistry.byVendor("").isEmpty())
        assertTrue(MuxiModelRegistry.byVendor("   ").isEmpty())
    }

    @Test
    fun mergeFromCloud_replacesList() {
        val custom = listOf(
            MuxiModelInfo(
                modelId = "comfyui-v1",
                displayName = "ComfyUI v1",
                vendor = "comfyui",
                category = MuxiModelCategory.IMAGE_GENERATION,
                capabilities = setOf(MuxiCapability.TEXT_TO_IMAGE),
            ),
        )
        MuxiModelRegistry.mergeFromCloud(custom)
        assertEquals(1, MuxiModelRegistry.listAll().size)
        assertEquals("comfyui-v1", MuxiModelRegistry.listAll()[0].modelId)
    }

    @Test
    fun mergeFromCloud_emptyRevertsToSeed() {
        // First replace with a non-empty list so we can observe the revert.
        MuxiModelRegistry.mergeFromCloud(
            listOf(
                MuxiModelInfo(
                    modelId = "tmp",
                    displayName = "tmp",
                    vendor = "x",
                    category = MuxiModelCategory.AUDIO_GENERATION,
                    capabilities = emptySet(),
                ),
            )
        )
        assertEquals(1, MuxiModelRegistry.listAll().size)

        MuxiModelRegistry.mergeFromCloud(emptyList())
        assertEquals(3, MuxiModelRegistry.listAll().size)
        assertNotNull(MuxiModelRegistry.findById("pixverse-extend"))
    }

    @Test
    fun pixverseText_hasNativeAudio() {
        val m = MuxiModelRegistry.findById("pixverse-text")
        assertNotNull(m)
        assertTrue(m!!.hasCapability(MuxiCapability.NATIVE_AUDIO))
    }

    @Test
    fun pixverseExtend_doesNotHaveNativeAudio() {
        val m = MuxiModelRegistry.findById("pixverse-extend")
        assertNotNull(m)
        assertFalse(m!!.hasCapability(MuxiCapability.NATIVE_AUDIO))
    }

    @Test
    fun pixverseImageTemplate_doesNotHaveNativeAudio() {
        val m = MuxiModelRegistry.findById("pixverse-image-template")
        assertNotNull(m)
        assertFalse(m!!.hasCapability(MuxiCapability.NATIVE_AUDIO))
    }

    @Test
    fun modelCategory_fromCode_roundTrips() {
        assertEquals(
            MuxiModelCategory.VIDEO_GENERATION,
            MuxiModelCategory.fromCode("video_generation")
        )
        assertEquals(
            MuxiModelCategory.IMAGE_GENERATION,
            MuxiModelCategory.fromCode("image_generation")
        )
        assertEquals(
            MuxiModelCategory.AUDIO_GENERATION,
            MuxiModelCategory.fromCode("audio_generation")
        )
    }

    @Test
    fun modelCategory_fromCode_isCaseInsensitive() {
        assertEquals(
            MuxiModelCategory.VIDEO_GENERATION,
            MuxiModelCategory.fromCode("Video_Generation")
        )
    }

    @Test
    fun modelCategory_fromCode_unknownReturnsNull() {
        assertNull(MuxiModelCategory.fromCode("future_category"))
        assertNull(MuxiModelCategory.fromCode(null))
        assertNull(MuxiModelCategory.fromCode(""))
    }
}
