// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TemplateChannel] and [TemplateChannelFilter]
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER). Pure-Kotlin: no
 * Android Views or org.json.
 */
class TemplateChannelFilterTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    // ---- TemplateChannel.fromString ------------------------------------------

    @Test
    fun fromString_pixverseLowercase() {
        assertSame(
            TemplateChannel.PIXVERSE,
            TemplateChannel.fromString("pixverse"),
        )
    }

    @Test
    fun fromString_pixverseWithWhitespace() {
        assertSame(
            TemplateChannel.PIXVERSE,
            TemplateChannel.fromString("  PixVerse  "),
        )
    }

    @Test
    fun fromString_comfyui() {
        assertSame(
            TemplateChannel.COMFYUI,
            TemplateChannel.fromString("comfyui"),
        )
    }

    @Test
    fun fromString_muxiCanvas() {
        assertSame(
            TemplateChannel.MUXI_CANVAS,
            TemplateChannel.fromString("muxi-canvas"),
        )
    }

    @Test
    fun fromString_muxiCanvasCaseInsensitive() {
        assertSame(
            TemplateChannel.MUXI_CANVAS,
            TemplateChannel.fromString("MUXI-CANVAS"),
        )
    }

    @Test
    fun fromString_null_isUnknown() {
        assertSame(TemplateChannel.UNKNOWN, TemplateChannel.fromString(null))
    }

    @Test
    fun fromString_blank_isUnknown() {
        assertSame(TemplateChannel.UNKNOWN, TemplateChannel.fromString(""))
        assertSame(TemplateChannel.UNKNOWN, TemplateChannel.fromString("   "))
    }

    @Test
    fun fromString_unknownCode_isUnknown() {
        assertSame(
            TemplateChannel.UNKNOWN,
            TemplateChannel.fromString("muxi-foo"),
        )
    }

    @Test
    fun displayLabel_isNonBlankAndHuman() {
        for (c in TemplateChannel.values()) {
            assertTrue("${c.name}: label blank", c.displayLabel.isNotBlank())
        }
    }

    @Test
    fun displayLabel_knownChannels_containBrand() {
        // Loose check: each known channel label references its brand.
        assertTrue(TemplateChannel.PIXVERSE.displayLabel.contains("PixVerse"))
        assertTrue(TemplateChannel.COMFYUI.displayLabel.contains("ComfyUI"))
        assertTrue(TemplateChannel.MUXI_CANVAS.displayLabel.contains("Muxi"))
    }

    // ---- TemplateChannelFilter -----------------------------------------------

    private val SEED = listOf(
        AigcTemplate("pv-1", "PixVerse 1", "pixverse"),
        AigcTemplate("pv-2", "PixVerse 2", "PixVerse"),
        AigcTemplate("cu-1", "ComfyUI 1", "comfyui"),
        AigcTemplate("mx-1", "Muxi 1", "muxi-canvas"),
        AigcTemplate("orphan-1", "Orphan 1", null),
        AigcTemplate("orphan-2", "Orphan 2", "  "),
        AigcTemplate("orphan-3", "Orphan 3", "muxi-foo"),
    )

    @Test
    fun filter_all_returnsInputUnchanged() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.ALL)
        assertEquals(SEED, r)
    }

    @Test
    fun filter_pixverse_returnsOnlyPixverse() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.PIXVERSE)
        assertEquals(2, r.size)
        for (t in r) {
            assertSame(
                TemplateChannel.PIXVERSE,
                TemplateChannel.fromString(t.channelCode),
            )
        }
    }

    @Test
    fun filter_comfyui_returnsOnlyComfyui() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.COMFYUI)
        assertEquals(1, r.size)
        assertEquals("cu-1", r[0].id)
    }

    @Test
    fun filter_muxiCanvas_returnsOnlyMuxiCanvas() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.MUXI_CANVAS)
        assertEquals(1, r.size)
        assertEquals("mx-1", r[0].id)
    }

    @Test
    fun filter_unknown_returnsOrphansOnly() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.UNKNOWN)
        assertEquals(3, r.size)
        val ids = r.map { it.id }.toSet()
        assertEquals(setOf("orphan-1", "orphan-2", "orphan-3"), ids)
    }

    @Test
    fun filter_emptyInput_returnsEmpty() {
        for (ch in TemplateChannel.values()) {
            val r = TemplateChannelFilter.filter(emptyList(), ch)
            assertTrue("${ch.name}: not empty", r.isEmpty())
        }
    }

    @Test
    fun filter_preservesInputOrder() {
        val r = TemplateChannelFilter.filter(SEED, TemplateChannel.PIXVERSE)
        assertEquals(listOf("pv-1", "pv-2"), r.map { it.id })
    }

    @Test
    fun count_matchesFilterSize() {
        for (ch in TemplateChannel.values()) {
            val n = TemplateChannelFilter.count(SEED, ch)
            val filtered = TemplateChannelFilter.filter(SEED, ch)
            assertEquals(
                "${ch.name}: count != filtered.size",
                filtered.size,
                n,
            )
        }
    }

    @Test
    fun filter_totalEqualsAllBuckets() {
        // ALL = sum of (specific channels + UNKNOWN). Sanity check.
        val perBucket = TemplateChannel.values()
            .filter { it != TemplateChannel.ALL }
            .sumOf { TemplateChannelFilter.count(SEED, it) }
        assertEquals(SEED.size, perBucket)
    }

    // ---- AigcTemplateCatalog fallback -----------------------------------------

    @Test
    fun catalogCount_isAtLeast5() {
        // All known channels + at least one orphan to exercise UNKNOWN.
        val n = AigcTemplateCatalog.count()
        assertTrue("catalog too small: $n", n >= 5)
    }

    @Test
    fun catalog_coversAllChannels() {
        val all = AigcTemplateCatalog.all()
        for (ch in TemplateChannel.values()) {
            if (ch == TemplateChannel.ALL) continue
            val hit = all.any {
                TemplateChannel.fromString(it.channelCode) == ch
            }
            assertTrue("no seed for $ch", hit)
        }
    }

    @Test
    fun catalogFetcher_remoteNull_returnsSeed() {
        val f = CatalogAigcTemplateFetcher(remote = null)
        val out = f.fetch()
        assertEquals(AigcTemplateCatalog.count(), out.size)
    }

    @Test
    fun catalogFetcher_remoteReturnsList_returnsRemote() {
        val custom = AigcTemplate("custom", "Custom", "pixverse")
        val f = CatalogAigcTemplateFetcher(remote = AigcTemplateFetcher { listOf(custom) })
        val out = f.fetch()
        assertEquals(1, out.size)
        assertEquals("custom", out[0].id)
    }

    @Test
    fun catalogFetcher_remoteThrows_returnsSeed() {
        val f = CatalogAigcTemplateFetcher(
            remote = AigcTemplateFetcher { throw IllegalStateException("net down") },
        )
        val out = f.fetch()
        assertNotNull(out)
        assertEquals(AigcTemplateCatalog.count(), out.size)
    }

    @Test
    fun catalogFetcher_remoteEmpty_returnsSeed() {
        val f = CatalogAigcTemplateFetcher(remote = AigcTemplateFetcher { emptyList() })
        val out = f.fetch()
        assertEquals(AigcTemplateCatalog.count(), out.size)
    }

    @Test
    fun catalogFetcher_remoteNullList_returnsSeed() {
        val f = CatalogAigcTemplateFetcher(remote = AigcTemplateFetcher { null })
        val out = f.fetch()
        assertEquals(AigcTemplateCatalog.count(), out.size)
    }

    @Test
    fun tag_isStable() {
        // Tag is just a logging constant; we just want it to be
        // a non-blank string and stable across calls.
        val a = TemplateChannelFilter.tag()
        val b = TemplateChannelFilter.tag()
        assertEquals(a, b)
        assertTrue(a.isNotBlank())
    }
}
