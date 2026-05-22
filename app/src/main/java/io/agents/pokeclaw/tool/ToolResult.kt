// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

class ToolResult private constructor(
    val isSuccess: Boolean,
    val data: String?,
    val error: String?,
    val errorType: ErrorType
) {
    /** 标准工具错误分类，供可靠执行轨迹和后续回执复用。 */
    enum class ErrorType {
        NONE,
        INVALID_ACTION,
        UNKNOWN_TOOL,
        MISSING_PERMISSION,
        TARGET_NOT_FOUND,
        ACCESSIBILITY_UNAVAILABLE,
        SYSTEM_DIALOG_BLOCKED,
        TIMEOUT,
        TOOL_EXCEPTION,
        UNKNOWN
    }

    companion object {
        @JvmStatic
        fun success(data: String): ToolResult = ToolResult(true, data, null, ErrorType.NONE)

        @JvmStatic
        fun error(error: String): ToolResult = ToolResult(false, null, error, classify(error))

        @JvmStatic
        fun error(error: String, errorType: ErrorType): ToolResult = ToolResult(false, null, error, errorType)

        @JvmStatic
        fun classify(error: String?): ErrorType {
            val normalized = error.orEmpty().lowercase()
            return when {
                normalized.isBlank() -> ErrorType.UNKNOWN
                normalized.contains("invalid action") || normalized.contains("invalid parameter") || normalized.contains("missing required") -> ErrorType.INVALID_ACTION
                normalized.contains("unknown tool") -> ErrorType.UNKNOWN_TOOL
                normalized.contains("permission") || normalized.contains("denied") -> ErrorType.MISSING_PERMISSION
                normalized.contains("not found") || normalized.contains("no results") || normalized.contains("cannot find") -> ErrorType.TARGET_NOT_FOUND
                normalized.contains("accessibility") || normalized.contains("no active window") || normalized.contains("active window") -> ErrorType.ACCESSIBILITY_UNAVAILABLE
                normalized.contains("system dialog") || normalized.contains("blocked") -> ErrorType.SYSTEM_DIALOG_BLOCKED
                normalized.contains("timeout") || normalized.contains("timed out") -> ErrorType.TIMEOUT
                normalized.contains("exception") || normalized.contains("crash") || normalized.contains("failed:") -> ErrorType.TOOL_EXCEPTION
                else -> ErrorType.UNKNOWN
            }
        }
    }

    override fun toString(): String = if (isSuccess) {
        "ToolResult{success=true, data='$data'}"
    } else {
        "ToolResult{success=false, errorType=$errorType, error='$error'}"
    }
}
