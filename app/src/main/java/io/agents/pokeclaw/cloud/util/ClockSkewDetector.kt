// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 serverTime 时钟漂移检测 — R7 实施

package io.agents.pokeclaw.cloud.util

class ClockSkewDetector {
    /**
     * 时钟漂移状态机：
     * - NORMAL: |delta| <= threshold（默认 4min）— 仅日志 + HMAC 仍用本地时间
     * - WARN: threshold < |delta| <= 2*threshold — 日志告警，HMAC 切换为 serverTime 基准
     * - OFFSET: |delta| > 2*threshold — 严重偏移，HMAC 仍以 serverTime 基准，但应触发设备重校时
     */
    enum class SkewState { NORMAL, WARN, OFFSET }

    data class SkewResult(
        val state: SkewState,
        val deltaMillis: Long,
    ) {
        val offsetMillis: Long get() = deltaMillis

        /** 是否应将 HMAC 签名基准从本地时间切换为 serverTime（即 WARN 或 OFFSET）。 */
        val shouldUseServerTime: Boolean
            get() = state == SkewState.WARN || state == SkewState.OFFSET
    }

    @Volatile private var lastResult: SkewResult = SkewResult(SkewState.NORMAL, 0L)

    fun compare(localNow: Long, serverTime: Long, thresholdMillis: Long): SkewResult {
        val delta = localNow - serverTime
        val abs = if (delta < 0) -delta else delta
        val state = when {
            abs > 2 * thresholdMillis -> SkewState.OFFSET
            abs > thresholdMillis -> SkewState.WARN
            else -> SkewState.NORMAL
        }
        return SkewResult(state, delta)
    }

    fun update(localNow: Long, serverTime: Long, thresholdMillis: Long): SkewResult {
        val result = compare(localNow, serverTime, thresholdMillis)
        // 任何非 NORMAL 状态都记忆：WARN/OFFSET 都应影响后续 HMAC 签名基准
        if (result.state != SkewState.NORMAL) {
            lastResult = result
        }
        return result
    }

    fun current(): SkewResult = lastResult
}