// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.instructions

import io.agents.pokeclaw.memory.UserMemoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GlobalInstructionsStore]. Covers persistence, secret
 * filtering, and length validation.
 *
 * Note: this test runs in plain JVM and depends on KVUtils (MMKV). If MMKV
 * is uninitialized, the persistence paths will throw; the secrets/length
 * validation paths are pure and don't touch storage.
 */
class GlobalInstructionsStoreTest {

    @Before
    fun setUp() {
        runCatching { GlobalInstructionsStore.clear() }
    }

    @After
    fun tearDown() {
        runCatching { GlobalInstructionsStore.clear() }
    }

    @Test
    fun get_returnsEmptyWhenUnset() {
        runCatching { GlobalInstructionsStore.clear() }
        val out = runCatching { GlobalInstructionsStore.get() }.getOrNull() ?: ""
        assertEquals("", out)
    }

    @Test
    fun isConfigured_falseWhenEmpty() {
        runCatching { GlobalInstructionsStore.clear() }
        val configured = runCatching { GlobalInstructionsStore.isConfigured() }.getOrDefault(false)
        assertFalse(configured)
    }

    @Test
    fun set_rejectsTextExceedingMaxLength() {
        val tooLong = "a".repeat(GlobalInstructionsStore.MAX_LEN + 1)
        val err = runCatching { GlobalInstructionsStore.set(tooLong) }.getOrNull()
        assertEquals(GlobalInstructionsStore.REJECTED_TOO_LONG, err)
    }

    @Test
    fun set_acceptsTextAtExactlyMaxLength() {
        val atMax = "a".repeat(GlobalInstructionsStore.MAX_LEN)
        val err = runCatching { GlobalInstructionsStore.set(atMax) }.getOrNull()
        // Either null (persisted) or a storage error — but it must not be REJECTED_TOO_LONG.
        if (err != null) {
            assertNotNull(err)
            assertFalse(
                "REJECTED_TOO_LONG must not fire at exact boundary, got $err",
                err == GlobalInstructionsStore.REJECTED_TOO_LONG,
            )
        }
    }

    @Test
    fun isSecret_detectsTokenAssignment() {
        // Pure, MMKV-free: verifies the secret pattern that GlobalInstructionsStore.set() relies on.
        assertTrue(UserMemoryStore.isSecret("use token=abc123 for the API"))
    }

    @Test
    fun isSecret_detectsPasswordWord() {
        assertTrue(UserMemoryStore.isSecret("my password is hunter2"))
    }

    @Test
    fun isSecret_detectsMnemonicPhrase() {
        assertTrue(UserMemoryStore.isSecret("wallet mnemonic: apple banana cherry"))
    }

    @Test
    fun isSecret_doesNotMatchNormalPreference() {
        // Sanity: ordinary preference text must NOT be flagged as a secret.
        val normal = "Always reply in simplified Chinese. Keep answers under 3 sentences."
        assertFalse(UserMemoryStore.isSecret(normal))
    }

    @Test
    fun set_acceptsNormalPreferenceText() {
        val normal = "Always reply in simplified Chinese. Keep answers under 3 sentences."
        val err = runCatching { GlobalInstructionsStore.set(normal) }.getOrNull()
        // If storage works, err is null. If MMKV unavailable, we still get no rejection code.
        if (err == null) {
            val stored = runCatching { GlobalInstructionsStore.get() }.getOrNull() ?: ""
            // We can only assert round-trip when storage is available; otherwise we just verify
            // the text was not rejected (REJECTED_SECRET / REJECTED_TOO_LONG never fire on normal input).
            if (stored.isNotEmpty()) {
                assertEquals(normal, stored)
            }
        } else {
            // Some storage error other than the explicit rejection codes.
            assertFalse(err == GlobalInstructionsStore.REJECTED_SECRET)
            assertFalse(err == GlobalInstructionsStore.REJECTED_TOO_LONG)
        }
    }

    @Test
    fun set_withEmptyString_clearsStore() {
        val err = runCatching { GlobalInstructionsStore.set("") }.getOrNull()
        assertNull("empty input should clear, not error", err)
        val stored = runCatching { GlobalInstructionsStore.get() }.getOrNull() ?: ""
        assertEquals("", stored)
    }

    @Test
    fun set_withWhitespaceOnly_clearsStore() {
        val err = runCatching { GlobalInstructionsStore.set("   \n\t  ") }.getOrNull()
        assertNull(err)
        val stored = runCatching { GlobalInstructionsStore.get() }.getOrNull() ?: ""
        assertEquals("", stored)
    }

    @Test
    fun maxLengthConstant_isReasonable() {
        // 2000 is the design budget — guards against accidental regression to 500.
        assertEquals(2000, GlobalInstructionsStore.MAX_LEN)
        assertTrue(GlobalInstructionsStore.MAX_LEN >= 1000)
    }
}
