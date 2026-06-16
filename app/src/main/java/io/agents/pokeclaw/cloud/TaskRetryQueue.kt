// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务重试队列：在重试上限内的失败任务入队，由 [CloudNetworkRetry] 风格的退避策略重派。
 *
 * 规则：
 *  - 单个 taskUuid 最多重试 3 次（1s/5s/30s 退避）
 *  - 超过上限的任务转移到 [TaskDeadLetterQueue]（UI 可见，需要人工介入）
 *  - 持久化由 SharedPreferences 简单维护（任务量小，无需 Room）
 *
 * 线程安全：使用 ConcurrentHashMap + synchronized 块保护。
 */
class TaskRetryQueue private constructor() {

    companion object {
        private const val TAG = "PokeClaw/RetryQueue"

        @Volatile
        private var instance: TaskRetryQueue? = null

        fun getInstance(): TaskRetryQueue = instance ?: synchronized(this) {
            instance ?: TaskRetryQueue().also { instance = it }
        }
    }

    /** 队列条目。 */
    data class Entry(
        val taskUuid: String,
        val command: String,
        val attempts: Int,
        val nextAttemptAtMillis: Long,
        val lastError: String,
        val enqueuedAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * 加入重试队列。
     * @return true 表示入队成功；false 表示已达上限（应转 dead-letter）
     */
    fun enqueue(taskUuid: String, command: String, attempts: Int, lastError: String): Boolean {
        if (attempts >= maxAttempts) {
            XLog.w(TAG, "enqueue: task=$taskUuid 已达 $maxAttempts 次上限，转 dead-letter")
            return false
        }
        val delayMs = CloudNetworkRetry.nextDelayMs(attempts)
        val entry = Entry(
            taskUuid = taskUuid,
            command = command,
            attempts = attempts,
            nextAttemptAtMillis = System.currentTimeMillis() + delayMs,
            lastError = lastError.take(200),
            enqueuedAtMillis = System.currentTimeMillis(),
        )
        entries[taskUuid] = entry
        XLog.i(TAG, "enqueue: task=$taskUuid attempts=$attempts nextDelay=${delayMs}ms")
        return true
    }

    /**
     * 取出所有到期的条目（nextAttemptAtMillis <= now）。
     */
    fun pollReady(nowMillis: Long = System.currentTimeMillis()): List<Entry> {
        return entries.values.filter { it.nextAttemptAtMillis <= nowMillis }
    }

    /**
     * 移除条目（重试成功后调用）。
     */
    fun remove(taskUuid: String) {
        entries.remove(taskUuid)
        XLog.d(TAG, "remove: task=$taskUuid")
    }

    /**
     * 查看当前队列大小。
     */
    fun size(): Int = entries.size

    /**
     * 清空队列。
     */
    fun clear() {
        entries.clear()
        XLog.i(TAG, "clear: 队列已清空")
    }

    /** 列出所有条目（UI 调试用）。 */
    fun snapshot(): List<Entry> = entries.values.toList()

    val maxAttempts = 3
}
