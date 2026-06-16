// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.memory

import io.agents.pokeclaw.utils.KVUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UserMemoryStore.
 *
 * 这些测试聚焦 UserMemoryStore 的纯逻辑（secret 检测、长度上限、FIFO 上限）。
 * KV 持久化层由 QA checklist 覆盖。
 *
 * 现在 KVUtils 提供了 in-memory test backing（见 KVUtils.resetTestBacking），
 * JVM 单元测试不再需要 Android Context；@Before 显式清理 in-memory map 保证
 * 测试隔离。
 */
class UserMemoryStoreTest {

    @Before
    fun setUp() {
        // KVUtils 在未 init 时使用 ConcurrentHashMap 回退；显式重置以保证
        // 测试隔离，避免跨测试污染。
        KVUtils.resetTestBacking()
        UserMemoryStore.clearAll()
    }

    @After
    fun tearDown() {
        UserMemoryStore.clearAll()
        KVUtils.resetTestBacking()
    }

    @Test
    fun add_acceptsNonSecretText() {
        val result = UserMemoryStore.add("I prefer dark mode")
        assertTrue("expected Accepted, got $result", result is UserMemoryStore.AddResult.Accepted)
    }

    @Test
    fun add_rejectsTokenKeyValue() {
        val result = UserMemoryStore.add("my api_key=sk-live-abcdef1234567890")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
        assertEquals(UserMemoryStore.REJECTED_SECRET, (result as UserMemoryStore.AddResult.Rejected).reason)
    }

    @Test
    fun add_rejectsPasswordWord() {
        val result = UserMemoryStore.add("my password is hunter2")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsMnemonicWord() {
        val result = UserMemoryStore.add("recovery mnemonic phrase alpha bravo charlie")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsRecoveryCode() {
        val result = UserMemoryStore.add("backup recovery code: 1234-5678-9012")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsTwoFactorSecret() {
        val result = UserMemoryStore.add("my 2fa secret is JKLM2345")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsOtp() {
        val result = UserMemoryStore.add("use this otp: 384921")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsBearerToken() {
        val result = UserMemoryStore.add("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsJwtToken() {
        val result = UserMemoryStore.add("jwt token eyJhbGciOiJIUzI1NiJ9 is here")
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
    }

    @Test
    fun add_rejectsTooLong() {
        val text = "x".repeat(UserMemoryStore.MAX_ENTRY_LEN + 1)
        val result = UserMemoryStore.add(text)
        assertTrue(result is UserMemoryStore.AddResult.Rejected)
        assertEquals(UserMemoryStore.REJECTED_TOO_LONG, (result as UserMemoryStore.AddResult.Rejected).reason)
    }

    @Test
    fun add_acceptsExactlyAtMaxLength() {
        val text = "y".repeat(UserMemoryStore.MAX_ENTRY_LEN)
        val result = UserMemoryStore.add(text)
        assertTrue(result is UserMemoryStore.AddResult.Accepted)
    }

    @Test
    fun add_acceptsTwoEntriesWithoutSecrets() {
        val a = UserMemoryStore.add("I like espresso")
        val b = UserMemoryStore.add("My wife's name is Sarah")
        assertTrue(a is UserMemoryStore.AddResult.Accepted)
        assertTrue(b is UserMemoryStore.AddResult.Accepted)
    }

    @Test
    fun isSecret_returnsFalseForPlainEnglish() {
        assertFalse(UserMemoryStore.isSecret("I like espresso"))
        assertFalse(UserMemoryStore.isSecret("I prefer dark mode"))
    }

    @Test
    fun exportJson_returnsEmptyArrayWhenNoEntries() {
        val json = UserMemoryStore.exportJson()
        assertEquals("[]", json)
    }

    @Test
    fun exportJson_returnsArrayOfEntries() {
        UserMemoryStore.add("alpha")
        UserMemoryStore.add("beta")
        val json = UserMemoryStore.exportJson()
        assertTrue(json.contains("alpha"))
        assertTrue(json.contains("beta"))
    }

    @Test
    fun delete_removesById() {
        val added = UserMemoryStore.add("test entry")
        assertTrue(added is UserMemoryStore.AddResult.Accepted)
        val id = (added as UserMemoryStore.AddResult.Accepted).entry.id
        assertNotNull(UserMemoryStore.get(id))
        assertTrue(UserMemoryStore.delete(id))
        assertEquals(null, UserMemoryStore.get(id))
    }

    @Test
    fun touchLastUsed_bumpsEntries() {
        val a = UserMemoryStore.add("alpha") as UserMemoryStore.AddResult.Accepted
        val b = UserMemoryStore.add("beta")  as UserMemoryStore.AddResult.Accepted
        UserMemoryStore.touchLastUsed(listOf(a.entry.id))
        val aAfter = UserMemoryStore.get(a.entry.id)
        assertNotNull(aAfter)
        assertEquals(1, aAfter!!.useCount)
        val bAfter = UserMemoryStore.get(b.entry.id)
        assertEquals(0, bAfter!!.useCount)
    }
}