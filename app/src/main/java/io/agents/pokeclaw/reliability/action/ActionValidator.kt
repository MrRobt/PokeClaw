// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.reliability.action

import io.agents.pokeclaw.tool.BaseTool
import io.agents.pokeclaw.tool.ToolParameter
import io.agents.pokeclaw.tool.ToolResult

/**
 * 工具动作校验器。
 *
 * 阶段一只做不会改变架构的硬拦截：工具存在、动作名合法、必填参数存在、参数类型粗校验、
 * wait_after 边界。校验失败时由 ToolRegistry 直接返回 INVALID_ACTION，保证真实工具不会被调用。
 */
object ActionValidator {
    private const val MAX_WAIT_AFTER_MS = 10_000L

    data class ValidationResult(
        val isValid: Boolean,
        val errorType: ToolResult.ErrorType = ToolResult.ErrorType.NONE,
        val message: String = "OK"
    ) {
        companion object {
            val OK = ValidationResult(true)
            fun invalid(message: String, errorType: ToolResult.ErrorType = ToolResult.ErrorType.INVALID_ACTION) =
                ValidationResult(false, errorType, message)
        }
    }

    fun validate(action: ReliableAction, tool: BaseTool?): ValidationResult {
        if (action.toolName.isBlank()) {
            return ValidationResult.invalid("Invalid action: blank tool name")
        }
        if (tool == null) {
            return ValidationResult.invalid("Unknown tool: ${action.toolName}", ToolResult.ErrorType.UNKNOWN_TOOL)
        }

        val parameters = tool.getParametersWithWaitAfter()
        val requiredMissing = parameters
            .filter { it.isRequired }
            .map { it.name }
            .filter { !action.parameters.containsKey(it) || action.parameters[it] == null }
        if (requiredMissing.isNotEmpty()) {
            return ValidationResult.invalid(
                "Invalid action '${action.toolName}': missing required parameter(s): ${requiredMissing.joinToString()}"
            )
        }

        action.parameters["wait_after"]?.let { waitAfter ->
            val waitMs = waitAfter.asLong()
            if (waitMs == null || waitMs !in 0..MAX_WAIT_AFTER_MS) {
                return ValidationResult.invalid(
                    "Invalid action '${action.toolName}': wait_after must be 0..$MAX_WAIT_AFTER_MS ms"
                )
            }
        }

        val byName = parameters.associateBy { it.name }
        action.parameters.forEach { (name, value) ->
            val spec = byName[name] ?: return@forEach
            if (!matchesType(value, spec)) {
                return ValidationResult.invalid(
                    "Invalid action '${action.toolName}': parameter '$name' expects ${spec.type}, got ${value::class.java.simpleName}"
                )
            }
        }

        return ValidationResult.OK
    }

    private fun matchesType(value: Any, spec: ToolParameter): Boolean {
        return when (spec.type.lowercase()) {
            "string" -> true // 现有工具大量使用 toString()，只要求非空必填已由上层处理。
            "integer", "int", "long" -> value.asLong() != null
            "number", "float", "double" -> value.toString().toDoubleOrNull() != null
            "boolean", "bool" -> value is Boolean || value.toString().lowercase() in setOf("true", "false")
            "array", "list" -> value is Collection<*> || value.javaClass.isArray
            "object", "map" -> value is Map<*, *>
            else -> true
        }
    }

    // NOTE: must NOT be named toLongOrNull — a private Any?.toLongOrNull() shadows
    // kotlin.text.String.toLongOrNull() inside its own body and self-recurses (StackOverflow).
    private fun Any?.asLong(): Long? = when (this) {
        is Number -> toLong()
        else -> this?.toString()?.toLongOrNull()
    }
}
