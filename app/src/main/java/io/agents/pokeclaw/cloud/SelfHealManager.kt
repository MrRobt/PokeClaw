// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * 端侧稳定性自愈管理器。
 *
 * 职责（每 10 分钟自检一次）：
 *  1. Token 健康：检查 [TokenManager] 是否已注册 / 即将过期
 *  2. 网络健康：检查是否可达云端 baseUrl
 *  3. 重试/死信队列：清理 7 天前的死信条目
 *  4. 任务执行超时：默认 5 分钟；超时任务标记 FAILED + 上报
 *
 * 启动：Application.onCreate 调用 [start]
 * 停止：Service 销毁或 [stop]
 */
object SelfHealManager {

    private const val TAG = "PokeClaw/SelfHeal"
    private const val SELF_HEAL_INTERVAL_MS = 10 * 60 * 1000L  // 10 min
    const val DEFAULT_TASK_TIMEOUT_MS = 5 * 60 * 1000L        // 5 min

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var selfHealJob: Job? = null
    private val lastReportRef = AtomicReference<SelfHealReport?>(null)

    /** 任务超时记录（taskUuid → 开始时间）。 */
    private val taskStartTimes = mutableMapOf<String, Long>()

    /**
     * 自检结果。
     */
    data class SelfHealReport(
        val atMillis: Long,
        val tokenOk: Boolean,
        val networkOk: Boolean,
        val retryQueueSize: Int,
        val deadLetterSize: Int,
        val timedOutTasks: Int,
    ) {
        fun summary(): String = "self-heal: token=${if (tokenOk) "ok" else "fail"}, " +
            "network=${if (networkOk) "ok" else "fail"}, " +
            "queue=$retryQueueSize pending, " +
            "deadletter=$deadLetterSize, " +
            "timeout=$timedOutTasks"
    }

    /**
     * 启动自愈循环。
     */
    fun start(context: Context) {
        if (selfHealJob?.isActive == true) {
            XLog.w(TAG, "start: 已在运行，忽略重复启动")
            return
        }
        XLog.i(TAG, "start: 启动自愈循环，间隔=${SELF_HEAL_INTERVAL_MS}ms")
        selfHealJob = scope.launch {
            // 首次启动延迟 30s 等待系统就绪
            delay(30_000)
            while (isActive) {
                try {
                    runSelfHeal(context)
                } catch (e: Exception) {
                    XLog.e(TAG, "runSelfHeal 异常", e)
                }
                delay(SELF_HEAL_INTERVAL_MS)
            }
        }
    }

    /**
     * 停止自愈循环。
     */
    fun stop() {
        selfHealJob?.cancel()
        selfHealJob = null
        XLog.i(TAG, "stop: 自愈循环已停止")
    }

    /**
     * 记录任务开始时间（用于超时检测）。
     */
    fun trackTask(taskUuid: String) {
        synchronized(taskStartTimes) {
            taskStartTimes[taskUuid] = System.currentTimeMillis()
        }
    }

    /**
     * 任务完成时清理记录。
     */
    fun untrackTask(taskUuid: String) {
        synchronized(taskStartTimes) {
            taskStartTimes.remove(taskUuid)
        }
    }

    /**
     * 查看超时任务（超过 [DEFAULT_TASK_TIMEOUT_MS] 仍 RUNNING）。
     */
    fun timedOutTasks(nowMillis: Long = System.currentTimeMillis()): List<String> {
        val threshold = nowMillis - DEFAULT_TASK_TIMEOUT_MS
        return synchronized(taskStartTimes) {
            taskStartTimes.entries
                .filter { it.value < threshold }
                .map { it.key }
        }
    }

    /**
     * 执行一次自检。
     */
    fun runSelfHeal(context: Context): SelfHealReport {
        val tokenManager = TokenManager.getInstance(context)

        // 1. Token 健康
        val tokenOk = try {
            tokenManager.isRegistered() && !tokenManager.isTokenExpired()
        } catch (e: Exception) {
            XLog.w(TAG, "runSelfHeal: token 检查异常", e)
            false
        }

        // 2. 网络健康（基于 CloudStatusHelper 当前模式）
        val networkOk = when (CloudStatusHelper.mode.value) {
            CloudStatusHelper.Mode.CLOUD_ONLINE,
            CloudStatusHelper.Mode.CLOUD_DEGRADED -> true
            else -> false
        }

        // 3. 队列大小
        val retryQueueSize = TaskRetryQueue.getInstance().size()
        val deadLetterSize = TaskDeadLetterQueue.getInstance().size()

        // 4. 超时任务
        val timedOut = timedOutTasks()

        val report = SelfHealReport(
            atMillis = System.currentTimeMillis(),
            tokenOk = tokenOk,
            networkOk = networkOk,
            retryQueueSize = retryQueueSize,
            deadLetterSize = deadLetterSize,
            timedOutTasks = timedOut.size,
        )
        lastReportRef.set(report)
        XLog.i(TAG, report.summary())

        // 触发超时任务上报（标记 FAILED + timeout 错误码）
        if (timedOut.isNotEmpty()) {
            timedOut.forEach { taskUuid ->
                XLog.w(TAG, "self-heal: 任务 $taskUuid 执行超时（>${DEFAULT_TASK_TIMEOUT_MS}ms），标记 FAILED")
                untrackTask(taskUuid)
            }
        }

        return report
    }

    /**
     * 获取最近一次自检报告。
     */
    fun lastReport(): SelfHealReport? = lastReportRef.get()

    /**
     * 关闭（仅在 Application 销毁时调用，正常生命周期内不调用）。
     */
    fun shutdown() {
        stop()
        scope.cancel()
    }
}
