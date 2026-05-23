// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务回执管理器 — 本地闭环核心，管理回执生成、缓存和模拟上报

package io.agents.pokeclaw.cloudnode

import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 任务回执管理器。
 *
 * 职责：
 * 1. 将执行状态流折叠为最终回执
 * 2. 管理回执的本地缓存（离线场景）
 * 3. 生成模拟云端响应和经历上报载荷
 * 4. 提供本地闭环执行证据
 *
 * 此组件不依赖真实云端，用于 DYQ-28 端侧独立验收。
 */
class CloudTaskReceiptManager(
    private val deviceId: String,
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
    private val offlineCache: CloudTaskOfflineReceiptCache = CloudTaskOfflineReceiptCache(),
    private val retryPolicy: CloudTaskRetryPolicy = CloudTaskRetryPolicy(),
) {
    private val TAG = "PokeClaw/ReceiptManager"

    /** 本地闭环执行统计 */
    data class LocalLoopStats(
        val totalExecuted: Int = 0,
        val succeeded: Int = 0,
        val failed: Int = 0,
        val cachedOffline: Int = 0,
        val retried: Int = 0,
    )

    private val _stats = MutableStateFlow(LocalLoopStats())
    val stats: StateFlow<LocalLoopStats> = _stats

    private val _pendingReceipts = MutableStateFlow<List<CachedCloudTaskReceipt>>(emptyList())
    val pendingReceipts: StateFlow<List<CachedCloudTaskReceipt>> = _pendingReceipts

    /**
     * 执行本地闭环任务。
     *
     * @param taskId 任务编号
     * @param instruction 任务指令
     * @param traceId 追踪编号（可选）
     * @param uploadOnline 是否在线上报（false 则进入离线缓存）
     * @param executor 实际执行器
     * @return 本地闭环执行结果
     */
    fun executeLocalLoop(
        taskId: String,
        instruction: String,
        traceId: String? = null,
        timeoutMillis: Long = 30000L,
        uploadOnline: Boolean = false,
        executor: (CloudExecutorTask) -> CloudTaskExecutionResult,
    ): CloudExecutorLocalRun {
        XLog.i(TAG, "executeLocalLoop: 开始本地闭环执行 taskId=$taskId, uploadOnline=$uploadOnline")

        val task = CloudExecutorTask(
            taskId = taskId,
            deviceId = deviceId,
            instruction = instruction,
            traceId = traceId,
            issuedAtMillis = clock.nowMillis(),
            timeoutMillis = timeoutMillis,
        )

        // 1. 模拟器驱动状态流
        val simulator = CloudExecutorNodeSimulator(clock)
        val reports = simulator.simulate(task, executor)

        // 2. 状态流折叠为回执
        val requestId = generateRequestId()
        val receipt = CloudTaskReceipt.fromReports(requestId, reports)

        XLog.i(TAG, "executeLocalLoop: 任务执行完成 taskId=$taskId, status=${receipt.finalStatus}, retryable=${receipt.retryable}")

        // 3. 计算重试计划
        val retryPlan = computeRetryPlan(receipt)

        // 4. 离线缓存处理
        val cached = if (!uploadOnline) {
            val cachedReceipt = offlineCache.cache(receipt, receipt.occurredAtMillis)
            _pendingReceipts.value = offlineCache.dueReceipts(Long.MAX_VALUE)
            XLog.w(TAG, "executeLocalLoop: 回执进入离线缓存 requestId=$requestId")
            true
        } else {
            XLog.i(TAG, "executeLocalLoop: 回执在线上报 requestId=$requestId")
            false
        }

        // 5. 更新统计
        updateStats(receipt, cached)

        // 6. 生成模拟载荷
        val mockCloudPayload = generateMockCloudPayload(receipt)
        val experiencePayload = generateExperiencePayload(receipt, retryPlan)

        // 7. 记录本地证据
        logExecutionEvidence(task, reports, receipt, cached, retryPlan)

        return CloudExecutorLocalRun(
            reports = reports,
            receipt = receipt,
            cachedForOfflineUpload = cached,
            retryPlan = retryPlan,
            mockCloudPayload = mockCloudPayload,
            experiencePayload = experiencePayload,
        )
    }

    /**
     * 尝试上报离线缓存的回执。
     *
     * @param uploadFn 实际上报函数，返回是否成功
     * @return 成功上报数量
     */
    fun flushOfflineCache(uploadFn: (CloudTaskReceipt) -> Boolean): Int {
        val now = clock.nowMillis()
        val dueReceipts = offlineCache.dueReceipts(now)

        if (dueReceipts.isEmpty()) {
            return 0
        }

        XLog.i(TAG, "flushOfflineCache: 尝试上报 ${dueReceipts.size} 个离线回执")

        var successCount = 0
        dueReceipts.forEach { cached ->
            val success = uploadFn(cached.receipt)
            if (success) {
                offlineCache.markUploaded(cached.requestId)
                successCount++
                XLog.i(TAG, "flushOfflineCache: 回执上报成功 requestId=${cached.requestId}")
            } else {
                offlineCache.markUploadFailed(cached.requestId, now)
                XLog.w(TAG, "flushOfflineCache: 回执上报失败 requestId=${cached.requestId}, retryCount=${cached.retryCount + 1}")
            }
        }

        _pendingReceipts.value = offlineCache.dueReceipts(Long.MAX_VALUE)
        return successCount
    }

    /**
     * 获取本地闭环执行证据日志。
     */
    fun getLocalLoopEvidence(): LocalLoopEvidence {
        return LocalLoopEvidence(
            timestamp = clock.nowMillis(),
            deviceId = deviceId,
            stats = _stats.value,
            pendingCount = offlineCache.size(),
            sampleReceipts = offlineCache.dueReceipts(Long.MAX_VALUE).take(3).map { it.receipt },
        )
    }

    private fun computeRetryPlan(receipt: CloudTaskReceipt): CloudTaskRetryPlan? {
        return if (!receipt.finalStatus.isSuccess() && receipt.retryable) {
            retryPolicy.nextPlan(currentAttempt = 0, nowMillis = receipt.occurredAtMillis)
        } else {
            null
        }
    }

    private fun CloudTaskStatus.isSuccess(): Boolean = this == CloudTaskStatus.SUCCEEDED

    private fun updateStats(receipt: CloudTaskReceipt, cached: Boolean) {
        _stats.value = _stats.value.copy(
            totalExecuted = _stats.value.totalExecuted + 1,
            succeeded = _stats.value.succeeded + if (receipt.finalStatus == CloudTaskStatus.SUCCEEDED) 1 else 0,
            failed = _stats.value.failed + if (receipt.finalStatus == CloudTaskStatus.FAILED) 1 else 0,
            cachedOffline = _stats.value.cachedOffline + if (cached) 1 else 0,
        )
    }

    private fun generateRequestId(): String {
        return "local-${clock.nowMillis()}-${(1000..9999).random()}"
    }

    private fun generateMockCloudPayload(receipt: CloudTaskReceipt): Map<String, String> {
        val status = when (receipt.finalStatus) {
            CloudTaskStatus.SUCCEEDED -> "SUCCESS"
            CloudTaskStatus.CANCELLED -> "CANCELLED"
            CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING -> "RUNNING"
            CloudTaskStatus.FAILED -> "FAILED"
        }
        return buildMap {
            put("requestId", receipt.requestId)
            put("taskUuid", receipt.taskId)
            put("deviceId", receipt.deviceId)
            receipt.traceId?.let { put("traceId", it) }
            put("status", status)
            put("accepted", receipt.accepted.toString())
            put("recoverable", receipt.retryable.toString())
            put("errorCode", receipt.errorCode.name)
            receipt.message?.let { put("result", it.take(2048)) }
            if (receipt.artifacts.isNotEmpty()) {
                put("evidenceRefs", receipt.artifacts.joinToString(","))
            }
            put("occurredAtMillis", receipt.occurredAtMillis.toString())
            put("mockCloudVersion", "1.0")
        }
    }

    private fun generateExperiencePayload(
        receipt: CloudTaskReceipt,
        retryPlan: CloudTaskRetryPlan?,
    ): Map<String, String> {
        val outcome = when (receipt.finalStatus) {
            CloudTaskStatus.SUCCEEDED -> "SUCCESS"
            CloudTaskStatus.CANCELLED -> "CANCELLED"
            CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING -> "RUNNING"
            CloudTaskStatus.FAILED -> "FAILED"
        }
        val lessonType = when {
            receipt.finalStatus == CloudTaskStatus.SUCCEEDED -> "TASK_EXECUTION_SUCCESS"
            receipt.finalStatus == CloudTaskStatus.FAILED && receipt.retryable -> "TASK_EXECUTION_RETRYABLE_FAILURE"
            receipt.finalStatus == CloudTaskStatus.FAILED -> "TASK_EXECUTION_FAILURE"
            else -> "TASK_EXECUTION_STATE"
        }
        return buildMap {
            put("taskUuid", receipt.taskId)
            put("deviceId", receipt.deviceId)
            receipt.traceId?.let { put("traceId", it) }
            put("lessonType", lessonType)
            put("outcome", outcome)
            put("summary", receipt.message?.take(2048) ?: outcome)
            put("errorCode", receipt.errorCode.name)
            put("recoverable", receipt.retryable.toString())
            put("occurredAtMillis", receipt.occurredAtMillis.toString())
            if (receipt.artifacts.isNotEmpty()) {
                put("evidenceRefs", receipt.artifacts.joinToString(","))
            }
            retryPlan?.let {
                put("retryNextAttempt", it.nextAttempt.toString())
                put("retryNextAttemptAtMillis", it.nextAttemptAtMillis.toString())
                put("retryDelayMillis", it.delayMillis.toString())
            }
            put("experienceVersion", "1.0")
        }
    }

    private fun logExecutionEvidence(
        task: CloudExecutorTask,
        reports: List<CloudTaskStatusReport>,
        receipt: CloudTaskReceipt,
        cached: Boolean,
        retryPlan: CloudTaskRetryPlan?,
    ) {
        XLog.i(TAG, "=== 本地闭环执行证据 ===")
        XLog.i(TAG, "设备编号: $deviceId")
        XLog.i(TAG, "任务编号: ${task.taskId}")
        XLog.i(TAG, "指令摘要: ${task.instruction.take(50)}")
        XLog.i(TAG, "状态流转: ${reports.joinToString(" -> ") { it.status.name }}")
        XLog.i(TAG, "最终状态: ${receipt.finalStatus}")
        XLog.i(TAG, "是否可重试: ${receipt.retryable}")
        XLog.i(TAG, "离线缓存: $cached")
        retryPlan?.let {
            XLog.i(TAG, "重试计划: attempt=${it.nextAttempt}, delay=${it.delayMillis}ms")
        }
        XLog.i(TAG, "========================")
    }

    /**
     * 本地闭环执行证据。
     */
    data class LocalLoopEvidence(
        val timestamp: Long,
        val deviceId: String,
        val stats: LocalLoopStats,
        val pendingCount: Int,
        val sampleReceipts: List<CloudTaskReceipt>,
    )
}
