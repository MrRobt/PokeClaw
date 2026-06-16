// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CustomModelSourceStore URL/host validation logic.
 *
 * Persistence round-trip is exercised by the QA checklist; these tests
 * focus on the URL allowlist, HTTPS check, and SHA-256 format check that
 * are pure-logic and don't require Android storage.
 */
class CustomModelSourceStoreTest {

    @Before
    fun setUp() {
        CustomModelSourceStore.clearAll()
    }

    @After
    fun tearDown() {
        CustomModelSourceStore.clearAll()
    }

    @Test
    fun validateUrl_acceptsHuggingFaceHttps() {
        assertEquals(null, CustomModelSourceStore.validateUrl("https://huggingface.co/foo/bar/file.litertlm"))
    }

    @Test
    fun validateUrl_acceptsRawGithubusercontent() {
        assertEquals(null, CustomModelSourceStore.validateUrl("https://raw.githubusercontent.com/u/r/main/f.litertlm"))
    }

    @Test
    fun validateUrl_rejectsHttp() {
        assertEquals("URL_NOT_HTTPS", CustomModelSourceStore.validateUrl("http://huggingface.co/x"))
    }

    @Test
    fun validateUrl_rejectsUnknownHost() {
        assertEquals("URL_NOT_ALLOWED", CustomModelSourceStore.validateUrl("https://evil.example.com/x"))
    }

    @Test
    fun validateUrl_rejectsLocalhost() {
        assertEquals("URL_NOT_ALLOWED", CustomModelSourceStore.validateUrl("https://localhost/x"))
    }

    @Test
    fun validateUrl_rejectsFileScheme() {
        assertEquals("URL_NOT_HTTPS", CustomModelSourceStore.validateUrl("file:///etc/passwd"))
    }

    @Test
    fun validateSha256_acceptsValidHex() {
        val sha = "0123456789abcdef".repeat(4) // 64 chars
        assertEquals(null, CustomModelSourceStore.validateSha256(sha))
    }

    @Test
    fun validateSha256_acceptsEmpty() {
        // null and blank are treated as "no checksum declared" and are valid.
        assertEquals(null, CustomModelSourceStore.validateSha256(null))
        assertEquals(null, CustomModelSourceStore.validateSha256(""))
    }

    @Test
    fun validateSha256_rejectsShort() {
        assertEquals("INVALID_SHA256_FORMAT", CustomModelSourceStore.validateSha256("deadbeef"))
    }

    @Test
    fun validateSha256_rejectsNonHex() {
        val bad = "Z".repeat(64)
        assertEquals("INVALID_SHA256_FORMAT", CustomModelSourceStore.validateSha256(bad))
    }

    @Test
    fun allowedHosts_isFiniteAndPredictable() {
        val hosts = CustomModelSourceStore.ALLOWED_HOSTS
        assertTrue(hosts.contains("huggingface.co"))
        assertTrue(hosts.contains("github.com"))
        assertTrue(hosts.contains("gitlab.com"))
        assertTrue(hosts.size in 3..6)
    }

    @Test
    fun add_acceptsValidSource() {
        val r = CustomModelSourceStore.add(
            name = "my model",
            url = "https://huggingface.co/me/repo/resolve/main/m.litertlm",
            sha256 = null,
        )
        assertTrue("expected Accepted, got $r", r is CustomModelSourceStore.AddResult.Accepted)
    }

    @Test
    fun add_rejectsHttpUrl() {
        val r = CustomModelSourceStore.add(
            name = "my model",
            url = "http://huggingface.co/me/repo/resolve/main/m.litertlm",
        )
        assertTrue(r is CustomModelSourceStore.AddResult.Rejected)
        assertEquals("URL_NOT_HTTPS", (r as CustomModelSourceStore.AddResult.Rejected).reason)
    }

    @Test
    fun add_rejectsUnknownHost() {
        val r = CustomModelSourceStore.add(
            name = "my model",
            url = "https://attacker.example/m.litertlm",
        )
        assertTrue(r is CustomModelSourceStore.AddResult.Rejected)
        assertEquals("URL_NOT_ALLOWED", (r as CustomModelSourceStore.AddResult.Rejected).reason)
    }

    @Test
    fun add_enforcesMaxSources() {
        repeat(CustomModelSourceStore.MAX_SOURCES) {
            val r = CustomModelSourceStore.add(
                name = "m-$it",
                url = "https://huggingface.co/repo$it/file$it.litertlm",
            )
            assertTrue("entry $it must be Accepted", r is CustomModelSourceStore.AddResult.Accepted)
        }
        val overflow = CustomModelSourceStore.add(
            name = "overflow",
            url = "https://huggingface.co/x/y.litertlm",
        )
        assertTrue(overflow is CustomModelSourceStore.AddResult.Rejected)
        assertEquals("MAX_SOURCES_EXCEEDED", (overflow as CustomModelSourceStore.AddResult.Rejected).reason)
    }

    @Test
    fun toModelInfo_prefixesIdAndDerivesFilename() {
        val source = CustomModelSource(
            id = "abc",
            name = "Custom X",
            url = "https://huggingface.co/me/repo/resolve/main/custom.litertlm",
            sha256 = null,
        )
        val mi = source.toModelInfo()
        assertEquals("custom_abc", mi.id)
        assertEquals("custom.litertlm", mi.fileName)
        assertTrue(mi.displayName.contains("自定义"))
    }

    @Test
    fun toModelInfo_fallsBackToIdForEmptyUrlPath() {
        val source = CustomModelSource(
            id = "fallback",
            name = "Edge",
            url = "https://huggingface.co/",
            sha256 = null,
        )
        val mi = source.toModelInfo()
        assertNotEquals("", mi.fileName)
        assertTrue(mi.fileName.endsWith(".litertlm") || mi.fileName.contains("fallback"))
    }
}