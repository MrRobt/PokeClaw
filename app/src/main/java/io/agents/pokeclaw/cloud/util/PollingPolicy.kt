// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 PollingPolicy 500ms→1s→2s 退避 + 30s 上限 + 5min 超时

package io.agents.pokeclaw.cloud.util

/**
 * Polling policy with 3-phase exponential backoff and total timeout.
 *
 * Phase 1: first 5 attempts use 500 ms interval.
 * Phase 2: next 5 attempts use 1000 ms interval.
 * Phase 3: subsequent attempts cap at 2000 ms interval (hard cap at 30 s per attempt).
 *
 * Total timeout is enforced via [isExpired]: polling stops when [elapsedSinceStart]
 * exceeds the configured [totalTimeoutMillis] (default 5 min).
 *
 * Terminal statuses ([shouldStop] returns true):
 * SUCCESS, FAILED, CANCELLED, TIMEOUT.
 *
 * @param phase1IntervalMillis Interval for phase 1 (default 500 ms).
 * @param phase1MaxAttempts Number of attempts in phase 1 (default 5).
 * @param phase2IntervalMillis Interval for phase 2 (default 1000 ms).
 * @param phase2MaxAttempts Number of attempts in phase 2 (default 5).
 * @param phase3IntervalMillis Interval for phase 3 (default 2000 ms).
 * @param maxIntervalMillis Cap for phase 3 interval (default 30 000 ms).
 * @param totalTimeoutMillis Total polling budget in milliseconds (default 5 min).
 */
class PollingPolicy(
    private val phase1IntervalMillis: Long = 500L,
    private val phase1MaxAttempts: Int = 5,
    private val phase2IntervalMillis: Long = 1_000L,
    private val phase2MaxAttempts: Int = 5,
    private val phase3IntervalMillis: Long = 2_000L,
    private val maxIntervalMillis: Long = 30_000L,
    private val totalTimeoutMillis: Long = 5 * 60 * 1_000L,
) {

    companion object {
        private val TERMINAL_STATUSES = setOf("SUCCESS", "FAILED", "CANCELLED", "TIMEOUT")
    }

    /**
     * Returns the next delay in milliseconds before the next poll attempt.
     *
     * @param attemptIndex Zero-based attempt index.
     * @param elapsedSinceStart Elapsed time since polling started (milliseconds).
     *                           Not used in this implementation; the caller will use
     *                           [isExpired] for timeout enforcement.
     * @return Recommended delay before next poll.
     */
    @Suppress("UNUSED_PARAMETER")
    fun nextDelayMillis(attemptIndex: Int, elapsedSinceStart: Long): Long {
        return when {
            attemptIndex <= phase1MaxAttempts -> phase1IntervalMillis
            attemptIndex <= phase1MaxAttempts + phase2MaxAttempts -> phase2IntervalMillis
            else -> phase3IntervalMillis.coerceAtMost(maxIntervalMillis)
        }
    }

    /** Returns true when the status is terminal (SUCCESS, FAILED, CANCELLED, TIMEOUT). */
    fun shouldStop(status: String): Boolean = TERMINAL_STATUSES.contains(status)

    /** Returns true when the elapsed time exceeds the total polling budget. */
    fun isExpired(elapsedSinceStart: Long): Boolean = elapsedSinceStart > totalTimeoutMillis
}