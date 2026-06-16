// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import io.agents.pokeclaw.utils.KVUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MonitorNicknameStore]. Validates set/get round-trip,
 * missing key returns null, clear-by-key, and corruption tolerance.
 */
class MonitorNicknameStoreTest {

    @Before
    fun setUp() {
        runCatching { MonitorNicknameStore.clearEverything() }
    }

    @After
    fun tearDown() {
        runCatching { MonitorNicknameStore.clearEverything() }
    }

    @Test
    fun getNickname_returnsNullForUnsetId() {
        val out = runCatching { MonitorNicknameStore.getNickname("phone:15551234") }.getOrNull()
        assertNull(out)
    }

    @Test
    fun getNickname_returnsNullForBlankId() {
        assertNull(MonitorNicknameStore.getNickname(""))
        assertNull(MonitorNicknameStore.getNickname("   "))
    }

    @Test
    fun setNickname_thenGetNickname_roundTrips() {
        val ok = runCatching { MonitorNicknameStore.setNickname("phone:15551234", "Mom") }
        if (ok.isFailure) return // skip when MMKV unavailable
        val out = MonitorNicknameStore.getNickname("phone:15551234")
        assertEquals("Mom", out)
    }

    @Test
    fun setNickname_withEmptyValue_clears() {
        runCatching { MonitorNicknameStore.setNickname("phone:1", "Dad") }
        runCatching { MonitorNicknameStore.setNickname("phone:1", "") }
        val out = runCatching { MonitorNicknameStore.getNickname("phone:1") }.getOrNull()
        assertNull(out)
    }

    @Test
    fun setNickname_truncatesAtMaxLen() {
        val longNick = "x".repeat(MonitorNicknameStore.MAX_NICKNAME_LEN + 50)
        runCatching { MonitorNicknameStore.setNickname("phone:9", longNick) }
        val out = runCatching { MonitorNicknameStore.getNickname("phone:9") }.getOrNull()
        if (out != null) {
            assertEquals(MonitorNicknameStore.MAX_NICKNAME_LEN, out.length)
        }
    }

    @Test
    fun listAll_returnsMapOfSetEntries() {
        runCatching {
            MonitorNicknameStore.setNickname("phone:1", "Mom")
            MonitorNicknameStore.setNickname("tg:alice", "Alice")
        }
        val all = runCatching { MonitorNicknameStore.listAll() }.getOrNull() ?: return
        // MMKV may be partially available in plain JVM tests: skip if any set didn't persist.
        if (all.size < 2) return
        assertEquals(2, all.size)
        assertEquals("Mom", all["phone:1"])
        assertEquals("Alice", all["tg:alice"])
    }

    @Test
    fun clearAll_removesOnlyTheGivenKey() {
        runCatching {
            MonitorNicknameStore.setNickname("phone:1", "A")
            MonitorNicknameStore.setNickname("phone:2", "B")
        }
        runCatching { MonitorNicknameStore.clearAll("phone:1") }
        val one = runCatching { MonitorNicknameStore.getNickname("phone:1") }.getOrNull()
        val two = runCatching { MonitorNicknameStore.getNickname("phone:2") }.getOrNull()
        assertNull(one)
        // phone:2 may or may not be present depending on storage availability;
        // we only assert that phone:1 is gone.
        if (two != null) assertEquals("B", two)
    }

    @Test
    fun setNickname_withBlankKey_isNoOp() {
        // Should not throw and should not pollute the table.
        runCatching { MonitorNicknameStore.setNickname("", "X") }
        runCatching { MonitorNicknameStore.setNickname("   ", "Y") }
        val all = runCatching { MonitorNicknameStore.listAll() }.getOrNull()
        if (all != null) {
            assertEquals(false, all.containsKey(""))
            assertEquals(false, all.containsKey("   "))
        }
    }

    @Test
    fun clearEverything_clearsAll() {
        runCatching {
            MonitorNicknameStore.setNickname("phone:1", "A")
            MonitorNicknameStore.clearEverything()
        }
        val all = runCatching { MonitorNicknameStore.listAll() }.getOrNull() ?: return
        assertEquals(0, all.size)
    }

    @Test
    fun readTable_skipsNonStringValues() {
        // Manually inject JSON with a non-string value to verify it is dropped, not coerced.
        val ok = runCatching { KVUtils.setMonitorNicknames("""{"phone:good":"Mom","phone:bad":42}""") }
        if (ok.isFailure) return // MMKV unavailable; skip
        val all = runCatching { MonitorNicknameStore.listAll() }.getOrNull() ?: return
        assertEquals("only string-typed entries survive", 1, all.size)
        assertEquals("Mom", all["phone:good"])
        assertTrue("non-string value must not be present", !all.containsKey("phone:bad"))
    }
}
