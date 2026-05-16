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
