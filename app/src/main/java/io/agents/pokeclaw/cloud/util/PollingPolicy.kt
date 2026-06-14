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
 * @param totalTimeoutMillis Total polling budget in milliseconds (default 5 min).
 */
class PollingPolicy(private val totalTimeoutMillis: Long) {

    companion object {
        private val TERMINAL_STATUSES = setOf("SUCCESS", "FAILED", "CANCELLED", "TIMEOUT")
    }

    /**
     * Returns the next delay in milliseconds before the next poll attempt.
     *
     * @param elapsedSinceStart Elapsed time since polling started (milliseconds).
     *                           Not used in this implementation; the caller will use
     *                           [isExpired] for timeout enforcement.
     * @return Recommended delay before next poll, capped at 30 000 ms.
     */
    @Suppress("UNUSED_PARAMETER")
    fun nextDelayMillis(attempt: Int, elapsedSinceStart: Long): Long {
        val base = when {
            attempt < 5  -> 500L
            attempt < 10 -> 1_000L
            else         -> 2_000L
        }
        return base.coerceAtMost(30_000L)
    }

    /** Returns true when the status is terminal (SUCCESS, FAILED, CANCELLED, TIMEOUT). */
    fun shouldStop(status: String): Boolean = TERMINAL_STATUSES.contains(status)

    /** Returns true when the elapsed time exceeds the total polling budget. */
    fun isExpired(elapsedSinceStart: Long): Boolean = elapsedSinceStart >= totalTimeoutMillis
}