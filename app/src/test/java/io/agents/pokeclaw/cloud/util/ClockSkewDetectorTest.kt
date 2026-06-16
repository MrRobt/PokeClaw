// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 serverTime 时钟漂移检测 — R7 实施

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSkewDetectorTest {
    private val threshold = 4 * 60 * 1000L  // 4 min

    @Test fun `NORMAL when local within 4min of server`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 1_000_000L, serverTime = 1_001_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
    }

    @Test fun `WARN when localAhead by 5min`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
        assertEquals(300_000L, result.deltaMillis)
    }

    @Test fun `WARN when localBehind by 5min`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 700_000L, serverTime = 1_000_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
        assertEquals(-300_000L, result.deltaMillis)
    }

    @Test fun `boundary exact 4min is NORMAL`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 240_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
    }

    @Test fun `boundary 4min1s is WARN`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 241_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
    }

    @Test fun `NORMAL when localNow equals serverTime`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 1_000L, serverTime = 1_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
        assertEquals(0L, result.deltaMillis)
    }

    @Test fun `accumulate still OFFSET after 3 consecutive calls (10min)`() {
        val detector = ClockSkewDetector()
        repeat(3) { detector.update(localNow = 600_000L, serverTime = 0L, thresholdMillis = threshold) }
        val state = detector.current()
        assertEquals(ClockSkewDetector.SkewState.OFFSET, state.state)
    }

    @Test fun `single huge jump 1h is OFFSET`() {
        val detector = ClockSkewDetector()
        val result = detector.compare(localNow = 3_600_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.OFFSET, result.state)
        assertEquals(3_600_000L, result.deltaMillis)
    }

    @Test fun `OFFSET state shouldUseServerTime is true (HMAC switches to serverTime)`() {
        val detector = ClockSkewDetector()
        detector.update(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = threshold) // 5min WARN
        assertEquals(ClockSkewDetector.SkewState.WARN, detector.current().state)
        assertEquals(true, detector.current().shouldUseServerTime)
        detector.update(localNow = 2_000_000L, serverTime = 1_000_000L, thresholdMillis = threshold) // 16min OFFSET
        assertEquals(ClockSkewDetector.SkewState.OFFSET, detector.current().state)
        assertEquals(true, detector.current().shouldUseServerTime)
    }

    // --- 扩展覆盖 ---

    @Test fun `NORMAL 状态 shouldUseServerTime 为 false`() {
        val detector = ClockSkewDetector()
        detector.update(localNow = 1_000L, serverTime = 1_001L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, detector.current().state)
        assertFalse(detector.current().shouldUseServerTime)
    }

    @Test fun `NORMAL 状态不覆盖 lastResult 仍为之前 WARN OFFSET`() {
        // 关键契约：NORMAL 不"复位" lastResult，让 HMAC 持续用 serverTime 基准
        val detector = ClockSkewDetector()
        detector.update(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = threshold) // WARN
        assertEquals(ClockSkewDetector.SkewState.WARN, detector.current().state)
        detector.update(localNow = 1_001_000L, serverTime = 1_000_000L, thresholdMillis = threshold) // 1s → NORMAL
        assertEquals("lastResult 不应被 NORMAL 覆盖", ClockSkewDetector.SkewState.WARN, detector.current().state)
    }

    @Test fun `current 初始为 NORMAL delta=0`() {
        val detector = ClockSkewDetector()
        assertEquals(ClockSkewDetector.SkewState.NORMAL, detector.current().state)
        assertEquals(0L, detector.current().deltaMillis)
        assertFalse(detector.current().shouldUseServerTime)
    }

    @Test fun `SkewState 枚举数量为 3 且 name 固定`() {
        assertEquals(3, ClockSkewDetector.SkewState.values().size)
        assertEquals("NORMAL", ClockSkewDetector.SkewState.NORMAL.name)
        assertEquals("WARN", ClockSkewDetector.SkewState.WARN.name)
        assertEquals("OFFSET", ClockSkewDetector.SkewState.OFFSET.name)
    }

    @Test fun `SkewResult data class equality offsetMillis 等于 deltaMillis`() {
        val r1 = ClockSkewDetector.SkewResult(ClockSkewDetector.SkewState.WARN, 123L)
        val r2 = ClockSkewDetector.SkewResult(ClockSkewDetector.SkewState.WARN, 123L)
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
        assertEquals(r1.deltaMillis, r1.offsetMillis)
        val r3 = ClockSkewDetector.SkewResult(ClockSkewDetector.SkewState.NORMAL, -50L)
        assertEquals(-50L, r3.offsetMillis)
        assertNotEquals(r1, r3)
    }

    @Test fun `custom threshold 1 小时 5min 偏差判 NORMAL`() {
        // threshold 放大 → 5min (300_000) 落入 NORMAL
        val detector = ClockSkewDetector()
        val big = 60 * 60 * 1000L  // 1h
        val r = detector.compare(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = big)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, r.state)
    }

    @Test fun `custom threshold 1 分钟 5min 偏差判 OFFSET`() {
        // threshold 缩小 → 5min (300_000) 远超 2*60s = 120s → OFFSET
        val detector = ClockSkewDetector()
        val small = 60 * 1000L  // 1 min
        val r = detector.compare(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = small)
        assertEquals(ClockSkewDetector.SkewState.OFFSET, r.state)
    }

    @Test fun `边界 2 倍 threshold 是 NORMAL 大于 2 倍才是 OFFSET`() {
        // 2*threshold 严格 > 才 OFFSET
        val detector = ClockSkewDetector()
        // delta = 2*threshold → 不严格大于 → WARN
        val r1 = detector.compare(localNow = 2 * threshold, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, r1.state)
        // delta = 2*threshold + 1 → OFFSET
        val r2 = detector.compare(localNow = 2 * threshold + 1, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.OFFSET, r2.state)
    }

    @Test fun `compare 返回值 不修改 current 状态`() {
        // compare 是纯函数，不应影响 detector 内部 lastResult
        val detector = ClockSkewDetector()
        // 先制造 OFFSET 状态
        detector.update(localNow = 3_600_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.OFFSET, detector.current().state)
        // compare 一个 NORMAL 状态 — 不影响 current
        detector.compare(localNow = 1_000L, serverTime = 1_000L, thresholdMillis = threshold)
        assertEquals("compare 不应修改 current", ClockSkewDetector.SkewState.OFFSET, detector.current().state)
    }

    @Test fun `delta 0 视为 NORMAL 且 shouldUseServerTime false`() {
        val detector = ClockSkewDetector()
        val r = detector.compare(localNow = 100L, serverTime = 100L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, r.state)
        assertEquals(0L, r.deltaMillis)
        assertFalse(r.shouldUseServerTime)
    }
}