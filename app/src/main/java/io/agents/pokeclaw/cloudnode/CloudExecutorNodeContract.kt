// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云执行节点最小闭环契约：接收任务、执行、上报状态与错误。

package io.agents.pokeclaw.cloudnode

/**
 * 云端下发给端侧执行节点的最小任务模型。
 * 字段保持纯数据形态，方便后续映射到实际接口或本地调试广播。
 */
data class CloudExecutorTask(
    val taskId: String,
    val deviceId: String,
    val instruction: String,
    val traceId: String? = null,
    val issuedAtMillis: Long,
    val timeoutMillis: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** 端侧任务状态，只表达跨端协议需要稳定识别的状态。 */
enum class CloudTaskStatus {
    RECEIVED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** 错误分类用于云端判断是否可重试、是否需要引导用户补权限。 */
enum class CloudTaskErrorCode {
    NONE,
    PERMISSION_MISSING,
    NETWORK_UNAVAILABLE,
    TASK_REJECTED,
    TOOL_FAILED,
    EXECUTION_TIMEOUT,
    // 需要人工完成登录/账号验证（登录态过期、未登录、二次验证等）。
    AUTH_REQUIRED,
    // 平台风控拦截（验证码、安全验证、滑块、频控封禁等），需暂停并人工介入。
    RISK_CONTROL,
    // 设备电量低于安全阈值，任务派发前置拒绝执行。
    LOW_BATTERY,
    // 端侧存储空间不足，无法下载 APK / 写入证据。
    STORAGE_FULL,
    UNKNOWN,
}

/** 端侧执行完成后的规范化结果。 */
data class CloudTaskExecutionResult(
    val success: Boolean,
    val message: String,
    val errorCode: CloudTaskErrorCode = CloudTaskErrorCode.NONE,
    val retryable: Boolean = false,
    val artifacts: List<String> = emptyList(),
) {
    companion object {
        fun success(message: String, artifacts: List<String> = emptyList()): CloudTaskExecutionResult =
            CloudTaskExecutionResult(
                success = true,
                message = message,
                artifacts = artifacts,
            )

        fun failure(
            message: String,
            errorCode: CloudTaskErrorCode = CloudTaskErrorCode.UNKNOWN,
            retryable: Boolean = false,
            artifacts: List<String> = emptyList(),
        ): CloudTaskExecutionResult = CloudTaskExecutionResult(
            success = false,
            message = message,
            errorCode = errorCode,
            retryable = retryable,
            artifacts = artifacts,
        )
    }
}

/** 单次状态上报载荷，后续可直接序列化为云端回传接口请求体。 */
data class CloudTaskStatusReport(
    val taskId: String,
    val deviceId: String,
    val traceId: String?,
    val status: CloudTaskStatus,
    val occurredAtMillis: Long,
    val message: String? = null,
    val errorCode: CloudTaskErrorCode = CloudTaskErrorCode.NONE,
    val retryable: Boolean = false,
    val artifacts: List<String> = emptyList(),
) {
    fun isTerminal(): Boolean = status in setOf(
        CloudTaskStatus.SUCCEEDED,
        CloudTaskStatus.FAILED,
        CloudTaskStatus.CANCELLED,
    )
}

/**
 * 端侧对云端任务请求的本地回执。
 *
 * 真实云端恢复前，此结构用于本地状态机、模拟云端响应和离线缓存之间传递稳定字段；
 * 后续可直接映射为 result 上报或经历上报样本。
 */
data class CloudTaskReceipt(
    val requestId: String,
    val taskId: String,
    val deviceId: String,
    val traceId: String?,
    val accepted: Boolean,
    val finalStatus: CloudTaskStatus,
    val retryable: Boolean,
    val errorCode: CloudTaskErrorCode = CloudTaskErrorCode.NONE,
    val message: String? = null,
    val artifacts: List<String> = emptyList(),
    val occurredAtMillis: Long,
) {
    companion object {
        fun fromReports(requestId: String, reports: List<CloudTaskStatusReport>): CloudTaskReceipt {
            require(requestId.isNotBlank()) { "请求编号不能为空" }
            require(reports.isNotEmpty()) { "状态报告不能为空" }
            val first = reports.first()
            val finalReport = reports.last()
            return CloudTaskReceipt(
                requestId = requestId,
                taskId = finalReport.taskId,
                deviceId = finalReport.deviceId,
                traceId = finalReport.traceId ?: first.traceId,
                accepted = reports.any { it.status == CloudTaskStatus.RECEIVED },
                finalStatus = finalReport.status,
                retryable = finalReport.retryable,
                errorCode = finalReport.errorCode,
                message = finalReport.message,
                artifacts = finalReport.artifacts,
                occurredAtMillis = finalReport.occurredAtMillis,
            )
        }
    }
}

/** 端侧本地重试策略：只负责可重试失败的下一次执行时间计算。 */
data class CloudTaskRetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 1000L,
    val maxDelayMillis: Long = 30_000L,
) {
    fun delayMillis(nextAttempt: Int): Long {
        val safeAttempt = nextAttempt.coerceAtLeast(1)
        val multiplier = 1L shl (safeAttempt - 1).coerceAtMost(10)
        return (baseDelayMillis.coerceAtLeast(0L) * multiplier).coerceAtMost(maxDelayMillis.coerceAtLeast(0L))
    }

    fun nextPlan(currentAttempt: Int, nowMillis: Long): CloudTaskRetryPlan? {
        val nextAttempt = currentAttempt + 1
        if (nextAttempt > maxAttempts.coerceAtLeast(1)) return null
        val delayMillis = delayMillis(nextAttempt)
        return CloudTaskRetryPlan(
            nextAttempt = nextAttempt,
            nextAttemptAtMillis = nowMillis + delayMillis,
            delayMillis = delayMillis,
        )
    }
}

/** 本地闭环输出的可重试计划，供调试日志、离线缓存和后续云端调度对齐。 */
data class CloudTaskRetryPlan(
    val nextAttempt: Int,
    val nextAttemptAtMillis: Long,
    val delayMillis: Long,
)

/** 离线缓存中的回执事件。 */
data class CachedCloudTaskReceipt(
    val requestId: String,
    val receipt: CloudTaskReceipt,
    val cachedAtMillis: Long,
    val retryCount: Int = 0,
    val nextAttemptAtMillis: Long = cachedAtMillis,
)

/**
 * 端侧回执离线缓存。
 *
 * 该缓存是纯内存实现，用于 JVM 单测和本地闭环样例；Android 运行时持久化仍由 CloudEventQueue 负责。
 */
class CloudTaskOfflineReceiptCache(
    private val maxSize: Int = 100,
    private val retryPolicy: CloudTaskRetryPolicy = CloudTaskRetryPolicy(),
) {
    private val receipts = linkedMapOf<String, CachedCloudTaskReceipt>()

    fun cache(receipt: CloudTaskReceipt, nowMillis: Long): CachedCloudTaskReceipt {
        val cached = CachedCloudTaskReceipt(
            requestId = receipt.requestId,
            receipt = receipt,
            cachedAtMillis = nowMillis,
        )
        receipts[receipt.requestId] = cached
        trimToMaxSize()
        return cached
    }

    fun dueReceipts(nowMillis: Long): List<CachedCloudTaskReceipt> {
        return receipts.values
            .filter { it.nextAttemptAtMillis <= nowMillis }
            .sortedBy { it.cachedAtMillis }
    }

    fun markUploadFailed(requestId: String, nowMillis: Long): CachedCloudTaskReceipt? {
        val existing = receipts[requestId] ?: return null
        val nextRetryCount = existing.retryCount + 1
        val updated = existing.copy(
            retryCount = nextRetryCount,
            nextAttemptAtMillis = nowMillis + retryPolicy.delayMillis(nextRetryCount),
        )
        receipts[requestId] = updated
        return updated
    }

    fun markUploaded(requestId: String): Boolean {
        return receipts.remove(requestId) != null
    }

    fun size(): Int = receipts.size

    fun clear() {
        receipts.clear()
    }

    private fun trimToMaxSize() {
        val safeMax = maxSize.coerceAtLeast(1)
        while (receipts.size > safeMax) {
            val firstKey = receipts.keys.firstOrNull() ?: return
            receipts.remove(firstKey)
        }
    }
}

/** 本地闭环单次执行结果，包含状态流、回执、离线缓存标记和模拟云端载荷。 */
data class CloudExecutorLocalRun(
    val reports: List<CloudTaskStatusReport>,
    val receipt: CloudTaskReceipt,
    val cachedForOfflineUpload: Boolean,
    val retryPlan: CloudTaskRetryPlan?,
    val mockCloudPayload: Map<String, String>,
    val experiencePayload: Map<String, String>,
)

/**
 * 本地端云闭环执行器。
 *
 * 用于真实云端联调恢复前的端侧独立验收：任务接收、状态机推进、回执折叠、
 * 可重试失败计划、离线上报缓存和模拟云端 payload 全部在本地完成。
 */
class CloudExecutorLocalClosedLoop(
    private val deviceId: String,
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
    val offlineCache: CloudTaskOfflineReceiptCache = CloudTaskOfflineReceiptCache(),
    private val retryPolicy: CloudTaskRetryPolicy = CloudTaskRetryPolicy(),
    private val requestIdProvider: () -> String = { "local-${clock.nowMillis()}" },
) {
    private val simulator = CloudExecutorNodeSimulator(clock)

    fun execute(
        taskId: String,
        instruction: String,
        traceId: String? = null,
        timeoutMillis: Long? = null,
        uploadOnline: Boolean,
        executor: (CloudExecutorTask) -> CloudTaskExecutionResult,
    ): CloudExecutorLocalRun {
        val task = CloudExecutorTask(
            taskId = taskId,
            deviceId = deviceId,
            instruction = instruction,
            traceId = traceId,
            issuedAtMillis = clock.nowMillis(),
            timeoutMillis = timeoutMillis,
        )
        val reports = simulator.simulate(task, executor)
        val receipt = CloudTaskReceipt.fromReports(requestIdProvider(), reports)
        val nowMillis = receipt.occurredAtMillis
        val retryPlan = if (!receipt.finalStatus.isSuccess() && receipt.retryable) {
            retryPolicy.nextPlan(currentAttempt = 0, nowMillis = nowMillis)
        } else {
            null
        }
        val cached = !uploadOnline
        if (cached) {
            offlineCache.cache(receipt, nowMillis)
        }
        return CloudExecutorLocalRun(
            reports = reports,
            receipt = receipt,
            cachedForOfflineUpload = cached,
            retryPlan = retryPlan,
            mockCloudPayload = receipt.toMockCloudPayload(),
            experiencePayload = receipt.toExperiencePayload(retryPlan),
        )
    }

    private fun CloudTaskStatus.isSuccess(): Boolean = this == CloudTaskStatus.SUCCEEDED

    private fun CloudTaskReceipt.toMockCloudPayload(): Map<String, String> {
        val status = when (finalStatus) {
            CloudTaskStatus.SUCCEEDED -> "SUCCESS"
            CloudTaskStatus.CANCELLED -> "CANCELLED"
            CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING -> "RUNNING"
            CloudTaskStatus.FAILED -> "FAILED"
        }
        return buildMap {
            put("requestId", requestId)
            put("taskUuid", taskId)
            put("deviceId", deviceId)
            traceId?.let { put("traceId", it) }
            put("status", status)
            put("accepted", accepted.toString())
            put("recoverable", retryable.toString())
            put("errorCode", errorCode.name)
            message?.let { put("result", it.take(2048)) }
            if (artifacts.isNotEmpty()) {
                put("evidenceRefs", artifacts.joinToString(","))
            }
            put("occurredAtMillis", occurredAtMillis.toString())
        }
    }

    private fun CloudTaskReceipt.toExperiencePayload(retryPlan: CloudTaskRetryPlan?): Map<String, String> {
        val outcome = when (finalStatus) {
            CloudTaskStatus.SUCCEEDED -> "SUCCESS"
            CloudTaskStatus.CANCELLED -> "CANCELLED"
            CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING -> "RUNNING"
            CloudTaskStatus.FAILED -> "FAILED"
        }
        val lessonType = when {
            finalStatus == CloudTaskStatus.SUCCEEDED -> "TASK_EXECUTION_SUCCESS"
            finalStatus == CloudTaskStatus.FAILED && retryable -> "TASK_EXECUTION_RETRYABLE_FAILURE"
            finalStatus == CloudTaskStatus.FAILED -> "TASK_EXECUTION_FAILURE"
            else -> "TASK_EXECUTION_STATE"
        }
        return buildMap {
            put("taskUuid", taskId)
            put("deviceId", deviceId)
            traceId?.let { put("traceId", it) }
            put("lessonType", lessonType)
            put("outcome", outcome)
            put("summary", message?.take(2048) ?: outcome)
            put("errorCode", errorCode.name)
            put("recoverable", retryable.toString())
            put("occurredAtMillis", occurredAtMillis.toString())
            if (artifacts.isNotEmpty()) {
                put("evidenceRefs", artifacts.joinToString(","))
            }
            retryPlan?.let {
                put("retryNextAttempt", it.nextAttempt.toString())
                put("retryNextAttemptAtMillis", it.nextAttemptAtMillis.toString())
                put("retryDelayMillis", it.delayMillis.toString())
            }
        }
    }
}

/**
 * 可替换时钟，保证本地模拟和单元测试不依赖真实时间。
 */
interface CloudExecutorClock {
    fun nowMillis(): Long
}

object SystemCloudExecutorClock : CloudExecutorClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * 本地模拟器：不触网、不改手机状态，只验证端侧节点的状态流顺序与错误回传载荷。
 */
class CloudExecutorNodeSimulator(
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
) {
    fun simulate(task: CloudExecutorTask, executor: () -> CloudTaskExecutionResult): List<CloudTaskStatusReport> {
        return simulate(task, executor = { _: CloudExecutorTask -> executor() })
    }

    fun simulate(
        task: CloudExecutorTask,
        executor: (CloudExecutorTask) -> CloudTaskExecutionResult,
    ): List<CloudTaskStatusReport> {
        require(task.taskId.isNotBlank()) { "任务编号不能为空" }
        require(task.deviceId.isNotBlank()) { "设备编号不能为空" }
        require(task.instruction.isNotBlank()) { "任务指令不能为空" }

        val reports = mutableListOf<CloudTaskStatusReport>()
        reports += task.report(
            status = CloudTaskStatus.RECEIVED,
            message = "端侧已接收任务",
        )
        reports += task.report(
            status = CloudTaskStatus.RUNNING,
            message = "端侧开始执行任务",
        )

        val result = try {
            executor(task)
        } catch (e: Exception) {
            CloudTaskExecutionResult.failure(
                message = e.message ?: "端侧执行异常",
                errorCode = CloudTaskErrorCode.UNKNOWN,
                retryable = true,
            )
        }

        reports += task.report(
            status = if (result.success) CloudTaskStatus.SUCCEEDED else CloudTaskStatus.FAILED,
            message = result.message,
            errorCode = result.errorCode,
            retryable = result.retryable,
            artifacts = result.artifacts,
        )
        return reports
    }

    private fun CloudExecutorTask.report(
        status: CloudTaskStatus,
        message: String? = null,
        errorCode: CloudTaskErrorCode = CloudTaskErrorCode.NONE,
        retryable: Boolean = false,
        artifacts: List<String> = emptyList(),
    ): CloudTaskStatusReport = CloudTaskStatusReport(
        taskId = taskId,
        deviceId = deviceId,
        traceId = traceId,
        status = status,
        occurredAtMillis = clock.nowMillis(),
        message = message,
        errorCode = errorCode,
        retryable = retryable,
        artifacts = artifacts,
    )
}
