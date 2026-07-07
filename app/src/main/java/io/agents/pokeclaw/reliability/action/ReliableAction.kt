// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.reliability.action

/**
 * 可靠执行阶段一动作协议。
 *
 * 当前保持为轻量包装：不替代现有 BaseTool/ToolRegistry，只把模型或确定性路由
 * 即将执行的工具调用统一描述为可校验、可记录的动作。
 */
data class ReliableAction(
    val actionId: String,
    val toolName: String,
    val parameters: Map<String, Any>,
    val source: Source = Source.AGENT,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    enum class Source {
        AGENT,
        DIRECT_TOOL,
        DEBUG,
        UNKNOWN
    }

    companion object {
        fun fromToolCall(
            toolName: String,
            parameters: Map<String, Any>,
            source: Source = Source.AGENT,
            actionId: String = "${source.name.lowercase()}-${System.currentTimeMillis()}-${toolName.hashCode()}"
        ): ReliableAction = ReliableAction(
            actionId = actionId,
            toolName = toolName.trim(),
            parameters = parameters,
            source = source
        )
    }
}
