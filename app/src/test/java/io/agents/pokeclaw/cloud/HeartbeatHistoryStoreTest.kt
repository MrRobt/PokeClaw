// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// HeartbeatHistoryStore 单测 — record/load 序列化、MAX_ENTRIES 裁剪、summary 聚合、clear。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.KVUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeartbeatHistoryStoreTest {

    @Before
    fun setUp() {
        KVUtils.resetTestBacking()
        HeartbeatHistoryStore.clear()
    }

    @After
    fun tearDown() {
        KVUtils.resetTestBacking()
    }

    @Test
    fun record_then_load_returnsSameEntry() {
        HeartbeatHistoryStore.record(
            HeartbeatHistoryStore.Entry(
                timestamp = 1000L,
                latencyMs = 50L,
                success = true,
            )
        )
        val list = HeartbeatHistoryStore.load()
        assertEquals(1, list.size)
        val e = list[0]
        assertEquals(1000L, e.timestamp)
        assertEquals(50L, e.latencyMs)
        assertTrue(e.success)
        assertNull(e.errorCategory)
    }

    @Test
    fun record_failure_with_errorCategory() {
        HeartbeatHistoryStore.record(
            HeartbeatHistoryStore.Entry(
                timestamp = 2000L,
                latencyMs = 999L,
                success = false,
                errorCategory = "TIMEOUT",
            )
        )
        val e = HeartbeatHistoryStore.load().single()
        assertEquals(false, e.success)
        assertEquals("TIMEOUT", e.errorCategory)
    }

    @Test
    fun record_trimsToMaxEntries() {
        // 直接 verify 序列化层不变量：append-only 到 MAX_ENTRIES 上限。
        // KVUtils 是顺序 put 覆盖，但内部 list 在 record 内部会裁剪到 1000。
        // 这里不灌 1001 个（会拖慢测试），改为验证裁剪路径：先灌 5 个，再灌 1 个，再清空 4 个后剩 1。
        // 直接验证：append 1001 个后只剩 1000。
        repeat(1001) { i ->
            HeartbeatHistoryStore.record(
                HeartbeatHistoryStore.Entry(
                    timestamp = i.toLong(),
                    latencyMs = i.toLong(),
                    success = true,
                )
            )
        }
        val size = HeartbeatHistoryStore.load().size
        assertEquals("应被裁剪到 MAX_ENTRIES=1000", 1000, size)
    }

    @Test
    fun load_returnsEmptyListWhenNoData() {
        assertEquals(emptyList<HeartbeatHistoryStore.Entry>(), HeartbeatHistoryStore.load())
    }

    @Test
    fun clear_removesAllHistory() {
        HeartbeatHistoryStore.record(
            HeartbeatHistoryStore.Entry(timestamp = 1L, latencyMs = 1L, success = true)
        )
        assertEquals(1, HeartbeatHistoryStore.load().size)
        HeartbeatHistoryStore.clear()
        assertEquals(0, HeartbeatHistoryStore.load().size)
    }

    @Test
    fun record_sanitizesPipeAndCommaInErrorCategory() {
        // "|" 和 "," 是序列化分隔符；save() 应替换为 "/" 以避免破坏解析
        HeartbeatHistoryStore.record(
            HeartbeatHistoryStore.Entry(
                timestamp = 1L,
                latencyMs = 1L,
                success = false,
                errorCategory = "A|B,C",
            )
        )
        // load 后能解析（parts.size >= 3），但 errorCategory 应该是替换后的值
        val loaded = HeartbeatHistoryStore.load()
        assertEquals(1, loaded.size)
        // 保存时 | 替换为 / , 替换为 /
        assertEquals("A/B/C", loaded[0].errorCategory)
    }

    @Test
    fun summary_aggregatesSuccessAndFailureByCategory() {
        // 注入 4 个 entry：2 成功、2 失败（不同 category）
        // summary 默认窗口 24h，我们用近期时间戳确保都进窗口
        val now = System.currentTimeMillis()
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now - 1000L, 10L, true))
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now - 2000L, 20L, true))
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now - 3000L, 30L, false, "TIMEOUT"))
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now - 4000L, 40L, false, "NETWORK"))

        val s = HeartbeatHistoryStore.summary(windowMs = 60_000L)
        assertEquals(4, s["total"])
        assertEquals(2, s["success"])
        assertEquals(2, s["failure"])
        assertEquals(25L, s["avgLatencyMs"])
        assertEquals(0.5, s["successRate"])

        @Suppress("UNCHECKED_CAST")
        val byCategory = s["failureByCategory"] as Map<String, Int>
        assertEquals(1, byCategory["TIMEOUT"])
        assertEquals(1, byCategory["NETWORK"])
    }

    @Test
    fun summary_returnsZeroStatsWhenNoData() {
        val s = HeartbeatHistoryStore.summary()
        assertEquals(0, s["total"])
        assertEquals(0, s["success"])
        assertEquals(0, s["failure"])
        assertEquals(0L, s["avgLatencyMs"])
        assertEquals(0.0, s["successRate"])

        @Suppress("UNCHECKED_CAST")
        val byCategory = s["failureByCategory"] as Map<String, Int>
        assertTrue(byCategory.isEmpty())
    }

    @Test
    fun summary_excludesEntriesOutsideWindow() {
        val now = System.currentTimeMillis()
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now, 10L, true))           // in window
        HeartbeatHistoryStore.record(HeartbeatHistoryStore.Entry(now - 100_000L, 10L, true)) // out of 1s window

        val s = HeartbeatHistoryStore.summary(windowMs = 1_000L)
        assertEquals(1, s["total"])
    }

    @Test
    fun record_thenLoad_preservesAllFields() {
        // 回归保护：序列化/反序列化要保留 timestamp/latencyMs/success/errorCategory
        val original = HeartbeatHistoryStore.Entry(
            timestamp = 1718000000000L,
            latencyMs = 12345L,
            success = false,
            errorCategory = "TOOL_FAILED",
        )
        HeartbeatHistoryStore.record(original)
        val loaded = HeartbeatHistoryStore.load().single()
        assertEquals(original, loaded)
    }
}
