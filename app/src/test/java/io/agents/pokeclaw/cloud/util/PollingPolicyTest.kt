// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 PollingPolicy 退避策略测试

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingPolicyTest {

    private val policy = PollingPolicy(totalTimeoutMillis = 5 * 60 * 1000L)

    @Test
    fun first5AttemptsUse500msInterval() {
        for (attempt in 0..4) {
            val delay = policy.nextDelayMillis(attempt, elapsedSinceStart = 0L)
            assertEquals(500L, delay)
        }
    }

    @Test
    fun next5AttemptsUse1000msInterval() {
        for (attempt in 5..9) {
            val delay = policy.nextDelayMillis(attempt, elapsedSinceStart = 0L)
            assertEquals(1000L, delay)
        }
    }

    @Test
    fun attemptsAfter10CapAt30s() {
        val delay = policy.nextDelayMillis(attempt = 10, elapsedSinceStart = 0L)
        assertEquals(30_000L, delay)
    }

    @Test
    fun expiredWhenElapsedExceeds5min() {
        val elapsed = 5 * 60 * 1000L + 1
        assertEquals(true, policy.isExpired(elapsed))
    }

    @Test
    fun notExpiredWhenElapsedUnder5min() {
        val elapsed = 5 * 60 * 1000L - 1
        assertEquals(false, policy.isExpired(elapsed))
    }

    @Test
    fun shouldStopOnTerminalStatus() {
        assertEquals(true, policy.shouldStop("SUCCESS"))
        assertEquals(true, policy.shouldStop("FAILED"))
        assertEquals(true, policy.shouldStop("CANCELLED"))
        assertEquals(true, policy.shouldStop("TIMEOUT"))
        assertEquals(false, policy.shouldStop("RUNNING"))
    }
}