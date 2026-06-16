// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.remake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RemakeSceneCatalog] and the
 * [CatalogRemakeSceneFetcher] fallback logic
 * (US-D-028-REMAKE-SCENE-PICKER). Verifies the bundle-seeded list of
 * 6 scenes is present, the example-input renderer is stable, and the
 * fetcher falls back to the seed on every error path.
 */
class RemakeSceneCatalogTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    // ---- Catalog ---------------------------------------------------------------

    @Test
    fun count_isSix() {
        assertEquals(6, RemakeSceneCatalog.count())
    }

    @Test
    fun listAll_returnsAllSixInExpectedOrder() {
        val ids = RemakeSceneCatalog.listAll().map { it.id }
        assertEquals(
            listOf(
                "viral_generic",
                "ecommerce",
                "game",
                "knowledge",
                "beauty",
                "review",
            ),
            ids
        )
    }

    @Test
    fun findById_knownId_returnsScene() {
        val scene = RemakeSceneCatalog.findById("ecommerce")
        assertNotNull(scene)
        assertEquals("电商带货", scene!!.name)
        assertTrue(scene.exampleInputs.isNotEmpty())
    }

    @Test
    fun findById_unknownId_returnsNull() {
        assertNull(RemakeSceneCatalog.findById("not_a_real_scene"))
    }

    @Test
    fun findById_nullAndBlank_returnsNull() {
        assertNull(RemakeSceneCatalog.findById(null))
        assertNull(RemakeSceneCatalog.findById(""))
        assertNull(RemakeSceneCatalog.findById("   "))
    }

    @Test
    fun everyScene_hasNonBlankNameAndDescription() {
        for (scene in RemakeSceneCatalog.listAll()) {
            assertTrue("${scene.id}: name blank", scene.name.isNotBlank())
            assertTrue("${scene.id}: description blank", scene.description.isNotBlank())
        }
    }

    @Test
    fun everyScene_hasAtLeastOneExampleInput() {
        for (scene in RemakeSceneCatalog.listAll()) {
            assertTrue("${scene.id}: no example inputs", scene.exampleInputs.isNotEmpty())
        }
    }

    // ---- Example prompt rendering ---------------------------------------------

    @Test
    fun toExamplePrompt_emptyInputs_returnsEmptyString() {
        val s = RemakeScene(
            id = "x", name = "x", description = "x",
            exampleImageUrl = null, exampleInputs = emptyMap()
        )
        assertEquals("", s.toExamplePrompt())
    }

    @Test
    fun toExamplePrompt_canonicalKeyOrderIsRespected() {
        val s = RemakeScene(
            id = "x", name = "x", description = "x",
            exampleImageUrl = null,
            exampleInputs = linkedMapOf(
                "productDescription" to "B",
                "styleDescription" to "A",
                "fixedScript" to "C",
            ),
        )
        val out = s.toExamplePrompt()
        // Canonical order is styleDescription → productDescription → fixedScript.
        val styleIdx = out.indexOf("styleDescription:")
        val productIdx = out.indexOf("productDescription:")
        val scriptIdx = out.indexOf("fixedScript:")
        assertTrue("style must come first", styleIdx >= 0 && styleIdx < productIdx)
        assertTrue("product must come before script", productIdx < scriptIdx)
    }

    @Test
    fun toExamplePrompt_extraKeysAppendedAfterCanonical() {
        val s = RemakeScene(
            id = "x", name = "x", description = "x",
            exampleImageUrl = null,
            exampleInputs = linkedMapOf(
                "extraNotes" to "Z",
                "styleDescription" to "A",
            ),
        )
        val out = s.toExamplePrompt()
        val styleIdx = out.indexOf("styleDescription:")
        val extraIdx = out.indexOf("extraNotes:")
        assertTrue("style must come before extraNotes", styleIdx in 0 until extraIdx)
    }

    @Test
    fun toExamplePrompt_skipsBlankValues() {
        val s = RemakeScene(
            id = "x", name = "x", description = "x",
            exampleImageUrl = null,
            exampleInputs = linkedMapOf(
                "styleDescription" to "   ",
                "productDescription" to "real",
            ),
        )
        val out = s.toExamplePrompt()
        assertTrue("blank value should be skipped", !out.contains("styleDescription:"))
        assertTrue(out.contains("productDescription: real"))
    }

    @Test
    fun toExamplePrompt_linesSeparatedByNewline() {
        val s = RemakeScene(
            id = "x", name = "x", description = "x",
            exampleImageUrl = null,
            exampleInputs = linkedMapOf(
                "styleDescription" to "A",
                "productDescription" to "B",
            ),
        )
        val out = s.toExamplePrompt()
        assertTrue(out.contains("\n"))
    }

    // ---- Fetcher fallback -----------------------------------------------------

    @Test
    fun fetcher_nullRemote_returnsSeed() {
        val f = CatalogRemakeSceneFetcher(remote = null)
        val out = f.fetch()
        assertEquals(6, out.size)
    }

    @Test
    fun fetcher_remoteReturningNull_returnsSeed() {
        val f = CatalogRemakeSceneFetcher(remote = { null })
        val out = f.fetch()
        assertEquals(6, out.size)
    }

    @Test
    fun fetcher_remoteReturningEmpty_returnsSeed() {
        val f = CatalogRemakeSceneFetcher(remote = { emptyList() })
        val out = f.fetch()
        assertEquals(6, out.size)
    }

    @Test
    fun fetcher_remoteThrowing_returnsSeed() {
        val f = CatalogRemakeSceneFetcher(remote = { throw IllegalStateException("net down") })
        val out = f.fetch()
        assertEquals(6, out.size)
    }

    @Test
    fun fetcher_remoteReturningList_returnsRemote() {
        val customScene = RemakeScene(
            id = "custom",
            name = "Custom",
            description = "From cloud",
            exampleImageUrl = null,
            exampleInputs = mapOf("styleDescription" to "remote"),
        )
        val f = CatalogRemakeSceneFetcher(remote = { listOf(customScene) })
        val out = f.fetch()
        assertEquals(1, out.size)
        assertEquals("custom", out[0].id)
    }
}
