// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog

/**
 * 云端下发的「白名单控制动作」数据类型。
 *
 * 用途：云端通过 PendingTaskItem.command 字段下发一段 JSON 指令，
 * 端侧解析为 [CloudControlAction] 并映射到本地 Tool（tap/swipe/input_text/system_key）执行。
 *
 * 协议（与云端契约 device.openapi.yaml 对齐）：
 *  {
 *    "type": "TAP" | "SWIPE" | "INPUT_TEXT" | "BACK" | "HOME" | "RECENT_APPS" | "OPEN_NOTIFICATION",
 *    "x": 540,                    // TAP 必填
 *    "y": 1200,                   // TAP 必填
 *    "x1": 100, "y1": 500,        // SWIPE 起点
 *    "x2": 100, "y2": 1500,       // SWIPE 终点
 *    "durationMs": 300,           // SWIPE 可选，默认 300ms
 *    "text": "hello",             // INPUT_TEXT 必填
 *    "reason": "..."              // 可选，审计/日志
 *  }
 */
enum class ControlActionType {
    TAP,
    SWIPE,
    INPUT_TEXT,
    BACK,
    HOME,
    RECENT_APPS,
    OPEN_NOTIFICATION,
    UNKNOWN;

    companion object {
        fun fromString(s: String?): ControlActionType {
            if (s.isNullOrBlank()) return UNKNOWN
            return values().firstOrNull { it.name.equals(s, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class CloudControlAction(
    val type: ControlActionType,
    val x: Int? = null,
    val y: Int? = null,
    val x1: Int? = null,
    val y1: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Int? = null,
    val text: String? = null,
    val reason: String? = null,
) {
    /**
     * 验证参数是否合法（白名单 7 种动作，每种有不同必填字段）。
     */
    fun validate(): ValidationResult {
        return when (type) {
            ControlActionType.TAP -> {
                if (x == null || y == null) ValidationResult.Invalid("TAP 需要 x/y")
                else if (x !in 0..10000 || y !in 0..10000) ValidationResult.Invalid("TAP 坐标超出范围")
                else ValidationResult.Valid
            }
            ControlActionType.SWIPE -> {
                if (x1 == null || y1 == null || x2 == null || y2 == null)
                    ValidationResult.Invalid("SWIPE 需要 x1/y1/x2/y2")
                else if (x1 !in 0..10000 || y1 !in 0..10000 || x2 !in 0..10000 || y2 !in 0..10000)
                    ValidationResult.Invalid("SWIPE 坐标超出范围")
                else if (durationMs != null && (durationMs < 50 || durationMs > 10_000))
                ValidationResult.Invalid("SWIPE 时长需在 50-10000ms")
                else ValidationResult.Valid
            }
            ControlActionType.INPUT_TEXT -> {
                if (text.isNullOrEmpty()) ValidationResult.Invalid("INPUT_TEXT 需要 text")
                else if (text.length > 4096) ValidationResult.Invalid("INPUT_TEXT 长度超过 4096")
                else ValidationResult.Valid
            }
            ControlActionType.BACK,
            ControlActionType.HOME,
            ControlActionType.RECENT_APPS,
            ControlActionType.OPEN_NOTIFICATION -> ValidationResult.Valid
            ControlActionType.UNKNOWN -> ValidationResult.Invalid("不支持的 action type")
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}

/**
 * 轻量级 JSON 解析器：避免引入完整 JSON 库到云端执行路径。
 *
 * 接受以下两种格式：
 *  1) 纯 JSON 字符串（标准格式）
 *  2) "control:{...json...}" 前缀（兼容旧版）
 *
 * 解析失败返回 null，由调用方决定是否 TASK_REJECTED。
 */
object CloudControlActionParser {

    private const val TAG = "PokeClaw/ControlActionParser"
    private const val CONTROL_PREFIX = "control:"

    /**
     * 解析 [PendingTaskItem.command] 为 [CloudControlAction]，非控制指令返回 null。
     */
    fun parse(command: String?): CloudControlAction? {
        if (command.isNullOrBlank()) return null
        val trimmed = command.trim()
        val json = if (trimmed.startsWith(CONTROL_PREFIX, ignoreCase = true)) {
            trimmed.substring(CONTROL_PREFIX.length).trim()
        } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed
        } else {
            return null
        }
        return try {
            parseJson(json)
        } catch (e: Exception) {
            XLog.w(TAG, "parse: 解析失败: ${e.message}")
            null
        }
    }

    /**
     * 极简 JSON 解析（仅支持扁平键值 + 数字 / 字符串）。
     */
    private fun parseJson(json: String): CloudControlAction? {
        val map = mutableMapOf<String, String>()
        // 去掉首尾花括号
        val body = json.trim().removePrefix("{").removeSuffix("}").trim()
        if (body.isEmpty()) return null

        // 简易分割：尊重双引号包裹的字符串
        var i = 0
        val n = body.length
        while (i < n) {
            // 跳过空白和逗号
            while (i < n && (body[i] == ' ' || body[i] == ',' || body[i] == '\n' || body[i] == '\t')) i++
            if (i >= n) break
            // 读 key
            val keyEnd = findKeyEnd(body, i)
            if (keyEnd < 0) return null
            val key = body.substring(i, keyEnd).trim().removeSurrounding("\"")
            i = keyEnd
            // 跳过冒号
            while (i < n && (body[i] == ':' || body[i] == ' ')) i++
            if (i >= n) return null
            // 读 value
            val (value, nextPos) = readValue(body, i)
            if (value == null) return null
            map[key] = value
            i = nextPos
        }
        val type = ControlActionType.fromString(map["type"])
        if (type == ControlActionType.UNKNOWN) return null

        return CloudControlAction(
            type = type,
            x = map["x"]?.toIntOrNull(),
            y = map["y"]?.toIntOrNull(),
            x1 = map["x1"]?.toIntOrNull(),
            y1 = map["y1"]?.toIntOrNull(),
            x2 = map["x2"]?.toIntOrNull(),
            y2 = map["y2"]?.toIntOrNull(),
            durationMs = map["durationMs"]?.toIntOrNull(),
            text = map["text"]?.let { unescapeJsonString(it) },
            reason = map["reason"]?.let { unescapeJsonString(it) },
        )
    }

    private fun findKeyEnd(body: String, start: Int): Int {
        var i = start
        if (i < body.length && body[i] == '"') {
            i++
            while (i < body.length && body[i] != '"') {
                if (body[i] == '\\' && i + 1 < body.length) i += 2 else i++
            }
            if (i < body.length) i++
        } else {
            while (i < body.length && body[i] != ':') i++
        }
        return i
    }

    private fun readValue(body: String, start: Int): Pair<String?, Int> {
        if (start >= body.length) return null to start
        return if (body[start] == '"') {
            // 字符串值
            var i = start + 1
            val sb = StringBuilder()
            while (i < body.length && body[i] != '"') {
                if (body[i] == '\\' && i + 1 < body.length) {
                    sb.append(body[i + 1])
                    i += 2
                } else {
                    sb.append(body[i])
                    i++
                }
            }
            if (i < body.length) i++ // skip closing quote
            sb.toString() to i
        } else {
            // 数字 / null / true / false
            var i = start
            while (i < body.length && body[i] != ',' && body[i] != '}' && body[i] != ' ' && body[i] != '\n') i++
            body.substring(start, i).trim() to i
        }
    }

    private fun unescapeJsonString(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}
