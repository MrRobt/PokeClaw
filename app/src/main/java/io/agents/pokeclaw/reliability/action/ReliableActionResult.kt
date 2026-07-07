// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.reliability.action

import io.agents.pokeclaw.tool.ToolResult

/** 标准化动作执行结果，承接 ToolResult 的错误分类。 */
data class ReliableActionResult(
    val action: ReliableAction,
    val success: Boolean,
    val message: String,
    val errorType: ToolResult.ErrorType = ToolResult.ErrorType.NONE,
    val startedAtMs: Long,
    val finishedAtMs: Long = System.currentTimeMillis()
) {
    val durationMs: Long = (finishedAtMs - startedAtMs).coerceAtLeast(0)

    companion object {
        fun fromToolResult(
            action: ReliableAction,
            result: ToolResult,
            startedAtMs: Long,
            finishedAtMs: Long = System.currentTimeMillis()
        ): ReliableActionResult = ReliableActionResult(
            action = action,
            success = result.isSuccess,
            message = if (result.isSuccess) result.data.orEmpty() else result.error.orEmpty(),
            errorType = result.errorType,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs
        )
    }
}
