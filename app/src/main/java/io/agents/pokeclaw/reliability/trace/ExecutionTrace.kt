// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.reliability.trace

import io.agents.pokeclaw.reliability.action.ReliableAction
import io.agents.pokeclaw.reliability.action.ReliableActionResult
import io.agents.pokeclaw.tool.ToolResult
import io.agents.pokeclaw.utils.XLog

/**
 * 阶段一最小执行轨迹。
 *
 * 以内存快照 + logcat 为闭环，不引入存储/网络依赖。每次任务开始清空轨迹，
 * 每次工具动作记录校验、执行、结果，任务结束时输出摘要供复盘。
 */
object ExecutionTrace {
    private const val TAG = "ExecutionTrace"
    private const val MAX_EVENTS = 200
    private val lock = Any()
    private val events = ArrayList<Event>()

    data class Event(
        val timestampMs: Long,
        val phase: Phase,
        val actionId: String? = null,
        val toolName: String? = null,
        val success: Boolean? = null,
        val errorType: ToolResult.ErrorType = ToolResult.ErrorType.NONE,
        val message: String = ""
    )

    enum class Phase {
        TASK_START,
        ACTION_VALIDATION,
        ACTION_EXECUTION_START,
        ACTION_RESULT,
        TASK_END
    }

    fun startTask(task: String, messageId: String) {
        synchronized(lock) {
            events.clear()
            appendLocked(Event(
                timestampMs = System.currentTimeMillis(),
                phase = Phase.TASK_START,
                message = "messageId=$messageId task=${task.take(120)}"
            ))
        }
        XLog.i(TAG, "startTask: messageId=$messageId task=${task.take(120)}")
    }

    fun recordValidation(action: ReliableAction, valid: Boolean, message: String, errorType: ToolResult.ErrorType) {
        record(Event(
            timestampMs = System.currentTimeMillis(),
            phase = Phase.ACTION_VALIDATION,
            actionId = action.actionId,
            toolName = action.toolName,
            success = valid,
            errorType = errorType,
            message = message
        ))
    }

    fun recordExecutionStart(action: ReliableAction) {
        record(Event(
            timestampMs = System.currentTimeMillis(),
            phase = Phase.ACTION_EXECUTION_START,
            actionId = action.actionId,
            toolName = action.toolName,
            message = "params=${action.parameters}"
        ))
    }

    fun recordResult(result: ReliableActionResult) {
        record(Event(
            timestampMs = result.finishedAtMs,
            phase = Phase.ACTION_RESULT,
            actionId = result.action.actionId,
            toolName = result.action.toolName,
            success = result.success,
            errorType = result.errorType,
            message = "durationMs=${result.durationMs} ${result.message.take(240)}"
        ))
    }

    fun finishTask(status: String, finalMessage: String = "") {
        val summary: String
        synchronized(lock) {
            appendLocked(Event(
                timestampMs = System.currentTimeMillis(),
                phase = Phase.TASK_END,
                success = status.equals("success", ignoreCase = true),
                message = "status=$status ${finalMessage.take(160)}"
            ))
            summary = buildSummaryLocked()
        }
        XLog.i(TAG, "finishTask: $summary")
    }

    fun snapshot(): List<Event> = synchronized(lock) { events.toList() }

    fun summary(): String = synchronized(lock) { buildSummaryLocked() }

    private fun record(event: Event) {
        synchronized(lock) { appendLocked(event) }
        XLog.i(
            TAG,
            "${event.phase}: action=${event.actionId}/${event.toolName} success=${event.success} errorType=${event.errorType} ${event.message}"
        )
    }

    private fun appendLocked(event: Event) {
        if (events.size >= MAX_EVENTS) events.removeAt(0)
        events.add(event)
    }

    private fun buildSummaryLocked(): String {
        val actionResults = events.filter { it.phase == Phase.ACTION_RESULT }
        val failed = actionResults.count { it.success == false }
        val validationFailed = events.count { it.phase == Phase.ACTION_VALIDATION && it.success == false }
        val last = events.lastOrNull()?.message.orEmpty()
        return "events=${events.size}, actions=${actionResults.size}, failed=$failed, validationFailed=$validationFailed, last=$last"
    }
}
