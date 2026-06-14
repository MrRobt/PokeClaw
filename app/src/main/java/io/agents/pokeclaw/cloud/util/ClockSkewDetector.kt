// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 serverTime 时钟漂移检测 — R7 实施

package io.agents.pokeclaw.cloud.util

class ClockSkewDetector {
    enum class SkewState { NORMAL, WARN }

    data class SkewResult(
        val state: SkewState,
        val deltaMillis: Long,
    ) {
        val offsetMillis: Long get() = deltaMillis
    }

    @Volatile private var lastResult: SkewResult = SkewResult(SkewState.NORMAL, 0L)

    fun compare(localNow: Long, serverTime: Long, thresholdMillis: Long): SkewResult {
        val delta = localNow - serverTime
        val abs = if (delta < 0) -delta else delta
        val state = if (abs > thresholdMillis) SkewState.WARN else SkewState.NORMAL
        return SkewResult(state, delta)
    }

    fun update(localNow: Long, serverTime: Long, thresholdMillis: Long): SkewResult {
        val result = compare(localNow, serverTime, thresholdMillis)
        if (result.state == SkewState.WARN) {
            lastResult = result
        }
        return result
    }

    fun current(): SkewResult = lastResult
}