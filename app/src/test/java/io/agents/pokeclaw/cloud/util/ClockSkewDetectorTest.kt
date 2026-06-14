// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 serverTime 时钟漂移检测 — R7 实施

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockSkewDetectorTest {
    private val threshold = 4 * 60 * 1000L  // 4 min

    @Test fun `NORMAL when local within 4min of server`() {
        val result = ClockSkewDetector.compare(localNow = 1_000_000L, serverTime = 1_001_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
    }

    @Test fun `WARN when localAhead by 5min`() {
        val result = ClockSkewDetector.compare(localNow = 1_300_000L, serverTime = 1_000_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
        assertEquals(300_000L, result.deltaMillis)
    }

    @Test fun `WARN when localBehind by 5min`() {
        val result = ClockSkewDetector.compare(localNow = 700_000L, serverTime = 1_000_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
        assertEquals(-300_000L, result.deltaMillis)
    }

    @Test fun `boundary exact 4min is NORMAL`() {
        val result = ClockSkewDetector.compare(localNow = 240_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
    }

    @Test fun `boundary 4min1s is WARN`() {
        val result = ClockSkewDetector.compare(localNow = 241_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
    }

    @Test fun `NORMAL when localNow equals serverTime`() {
        val result = ClockSkewDetector.compare(localNow = 1_000L, serverTime = 1_000L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.NORMAL, result.state)
        assertEquals(0L, result.deltaMillis)
    }

    @Test fun `accumulate still WARN after 3 consecutive calls`() {
        val detector = ClockSkewDetector()
        repeat(3) { detector.update(localNow = 600_000L, serverTime = 0L, thresholdMillis = threshold) }
        val state = detector.current()
        assertEquals(ClockSkewDetector.SkewState.WARN, state.state)
    }

    @Test fun `single huge jump 1h is WARN`() {
        val result = ClockSkewDetector.compare(localNow = 3_600_000L, serverTime = 0L, thresholdMillis = threshold)
        assertEquals(ClockSkewDetector.SkewState.WARN, result.state)
        assertEquals(3_600_000L, result.deltaMillis)
    }
}