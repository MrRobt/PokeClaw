// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 PollingPolicy 退避策略测试

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun attemptsAfter10UsePhase3Interval() {
        // phase3 default = 2000ms, maxIntervalMillis (30s) is the hard cap for any future growth
        val delay = policy.nextDelayMillis(attemptIndex = 10, elapsedSinceStart = 0L)
        assertEquals(2_000L, delay)
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

    // --- isExpired boundary semantics (strict >, not >=) ---

    @Test
    fun `isExpired at exactly totalTimeoutMillis is false (strict greater-than)`() {
        val p = PollingPolicy(totalTimeoutMillis = 10_000L)
        assertFalse("exactly equal must not be expired", p.isExpired(10_000L))
    }

    @Test
    fun `isExpired at totalTimeoutMillis + 1 is true`() {
        val p = PollingPolicy(totalTimeoutMillis = 10_000L)
        assertTrue(p.isExpired(10_001L))
    }

    @Test
    fun `isExpired at zero elapsed is false`() {
        assertFalse(policy.isExpired(0L))
    }

    @Test
    fun `isExpired with negative elapsed is false`() {
        assertFalse(policy.isExpired(-1L))
        assertFalse(policy.isExpired(-999_999L))
    }

    // --- phase boundary precision ---

    @Test
    fun `attemptIndex exactly at phase1MaxAttempts transitions to phase 2`() {
        val p = PollingPolicy(phase1MaxAttempts = 3)
        // 0,1,2 → phase 1 (500ms); 3+ → phase 2
        assertEquals(500L, p.nextDelayMillis(2, 0L))
        assertEquals(1_000L, p.nextDelayMillis(3, 0L))
        assertEquals(1_000L, p.nextDelayMillis(4, 0L))
    }

    @Test
    fun `attemptIndex exactly at phase1+phase2 transitions to phase 3`() {
        val p = PollingPolicy(phase1MaxAttempts = 2, phase2MaxAttempts = 2)
        // 0,1 → phase 1; 2,3 → phase 2; 4+ → phase 3
        assertEquals(500L, p.nextDelayMillis(0, 0L))
        assertEquals(500L, p.nextDelayMillis(1, 0L))
        assertEquals(1_000L, p.nextDelayMillis(2, 0L))
        assertEquals(1_000L, p.nextDelayMillis(3, 0L))
        assertEquals(2_000L, p.nextDelayMillis(4, 0L))
        assertEquals(2_000L, p.nextDelayMillis(5, 0L))
    }

    // --- customizable intervals and max cap ---

    @Test
    fun `custom intervals are honored`() {
        val p = PollingPolicy(
            phase1IntervalMillis = 100L,
            phase1MaxAttempts = 2,
            phase2IntervalMillis = 250L,
            phase2MaxAttempts = 2,
            phase3IntervalMillis = 400L,
        )
        assertEquals(100L, p.nextDelayMillis(0, 0L))
        assertEquals(100L, p.nextDelayMillis(1, 0L))
        assertEquals(250L, p.nextDelayMillis(2, 0L))
        assertEquals(250L, p.nextDelayMillis(3, 0L))
        assertEquals(400L, p.nextDelayMillis(4, 0L))
    }

    @Test
    fun `phase3 interval is capped at maxIntervalMillis`() {
        // phase3 = 60s, max cap = 5s → nextDelay must clamp to 5s
        val p = PollingPolicy(
            phase3IntervalMillis = 60_000L,
            maxIntervalMillis = 5_000L,
        )
        assertEquals(5_000L, p.nextDelayMillis(20, 0L))
        assertEquals(5_000L, p.nextDelayMillis(100, 0L))
    }

    @Test
    fun `phase3 interval is returned as-is when below max cap`() {
        val p = PollingPolicy(
            phase3IntervalMillis = 2_500L,
            maxIntervalMillis = 10_000L,
        )
        assertEquals(2_500L, p.nextDelayMillis(20, 0L))
    }

    // --- nextDelayMillis stability / no overflow ---

    @Test
    fun `nextDelayMillis returns phase3 for very large attemptIndex`() {
        // 0..4 phase1, 5..9 phase2, 10+ phase3
        val delay = policy.nextDelayMillis(attemptIndex = Int.MAX_VALUE, elapsedSinceStart = 0L)
        assertEquals(2_000L, delay)
    }

    // --- shouldStop for non-terminal / unknown statuses ---

    @Test
    fun `shouldStop returns false for PENDING status`() {
        assertFalse(policy.shouldStop("PENDING"))
    }

    @Test
    fun `shouldStop is case-sensitive (lowercase terminal returns false)`() {
        assertFalse(policy.shouldStop("success"))
        assertFalse(policy.shouldStop("failed"))
        assertFalse(policy.shouldStop("Timeout"))
    }

    @Test
    fun `shouldStop returns false for empty string and unknown status`() {
        assertFalse(policy.shouldStop(""))
        assertFalse(policy.shouldStop("UNKNOWN_STATE"))
        assertFalse(policy.shouldStop("IN_PROGRESS"))
    }

    @Test
    fun `shouldStop returns true for all 4 terminal statuses explicitly`() {
        for (s in listOf("SUCCESS", "FAILED", "CANCELLED", "TIMEOUT")) {
            assertTrue("expected shouldStop($s) = true", policy.shouldStop(s))
        }
    }
}
