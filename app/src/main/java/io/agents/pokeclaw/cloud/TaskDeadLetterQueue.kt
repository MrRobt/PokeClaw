// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务死信队列：超过 [TaskRetryQueue.MAX_ATTEMPTS] 次重试仍失败的任务入队，
 * 等待 UI 展示 + 人工介入（重试 / 删除 / 联系支持）。
 *
 * 设计：与 TaskRetryQueue 同样的 ConcurrentHashMap 实现，
 * 但保留完整错误历史（含所有 attempts 的错误），便于审计。
 */
class TaskDeadLetterQueue private constructor() {

    companion object {
        private const val TAG = "PokeClaw/DeadLetter"

        @Volatile
        private var instance: TaskDeadLetterQueue? = null

        fun getInstance(): TaskDeadLetterQueue = instance ?: synchronized(this) {
            instance ?: TaskDeadLetterQueue().also { instance = it }
        }
    }

    /** 死信条目。 */
    data class DeadLetter(
        val taskUuid: String,
        val command: String,
        val attempts: Int,
        val lastError: String,
        val history: List<String>,  // 历史错误（按时间顺序）
        val deadAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<String, DeadLetter>()

    /**
     * 加入死信队列。
     */
    fun enqueue(
        taskUuid: String,
        command: String,
        attempts: Int,
        lastError: String,
        history: List<String> = emptyList(),
    ) {
        val entry = DeadLetter(
            taskUuid = taskUuid,
            command = command,
            attempts = attempts,
            lastError = lastError.take(200),
            history = history,
            deadAtMillis = System.currentTimeMillis(),
        )
        entries[taskUuid] = entry
        XLog.w(TAG, "enqueue: task=$taskUuid attempts=$attempts lastError=$lastError")
    }

    /**
     * 人工重试：从死信队列移除并返回命令（供调用方重新派发）。
     */
    fun retry(taskUuid: String): DeadLetter? {
        val entry = entries.remove(taskUuid)
        if (entry != null) {
            XLog.i(TAG, "retry: task=$taskUuid 已从死信队列移除，等待重新派发")
        }
        return entry
    }

    /**
     * 人工删除。
     */
    fun discard(taskUuid: String): Boolean {
        val removed = entries.remove(taskUuid) != null
        if (removed) XLog.i(TAG, "discard: task=$taskUuid 已从死信队列删除")
        return removed
    }

    fun size(): Int = entries.size
    fun snapshot(): List<DeadLetter> = entries.values.sortedByDescending { it.deadAtMillis }
    fun get(taskUuid: String): DeadLetter? = entries[taskUuid]
    fun clear() {
        entries.clear()
        XLog.i(TAG, "clear: 死信队列已清空")
    }
}
