// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 本地闭环执行样例生成器 — 生成模拟任务和验证响应，用于 DYQ-28 端侧独立验收

package io.agents.pokeclaw.cloudnode

import io.agents.pokeclaw.utils.XLog

/**
 * 本地闭环执行样例生成器。
 *
 * 生成各类模拟任务及其预期响应，用于：
 * 1. 端侧本地闭环功能验证
 * 2. 模拟云端响应样例
 * 3. 离线执行链路测试
 * 4. 生成端侧日志证据
 *
 * 不依赖真实云端，所有样例在本地完成状态机推进。
 */
class LocalClosedLoopSampler(
    private val deviceId: String = "pokeclaw-test-device",
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
) {
    private val TAG = "PokeClaw/LocalLoopSampler"

    /**
     * 样例执行结果。
     */
    data class SampleResult(
        val name: String,
        val taskId: String,
        val instruction: String,
        val reports: List<CloudTaskStatusReport>,
        val receipt: CloudTaskReceipt,
        val mockCloudPayload: Map<String, String>,
        val experiencePayload: Map<String, String>,
        val verificationPassed: Boolean,
    )

    /**
     * 运行全部样例，生成本地闭环证据。
     *
     * @return 样例执行结果列表和汇总统计
     */
    fun runAllSamples(): SampleReport {
        XLog.i(TAG, "runAllSamples: 开始运行本地闭环样例集")

        val samples = listOf(
            sampleSuccess(),
            sampleFailureRetryable(),
            sampleFailureNonRetryable(),
            sampleTimeout(),
            samplePermissionMissing(),
            sampleOfflineCached(),
        )

        val passed = samples.count { it.verificationPassed }
        val failed = samples.size - passed

        XLog.i(TAG, "runAllSamples: 样例执行完成，总计=${samples.size}, 通过=$passed, 失败=$failed")

        return SampleReport(
            timestamp = clock.nowMillis(),
            deviceId = deviceId,
            samples = samples,
            totalCount = samples.size,
            passedCount = passed,
            failedCount = failed,
        )
    }

    /**
     * 样例1：成功执行。
     */
    fun sampleSuccess(): SampleResult {
        val taskId = "task-success-${clock.nowMillis()}"
        val instruction = "打开微信并发送消息'你好'给联系人'测试'"

        val manager = CloudTaskReceiptManager(deviceId, clock)
        val result = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            uploadOnline = false,
        ) { _ ->
            CloudTaskExecutionResult.success(
                message = "任务执行成功：已打开微信并发送消息",
                artifacts = listOf("screenshot_001.png", "log_001.txt"),
            )
        }

        return SampleResult(
            name = "成功执行",
            taskId = taskId,
            instruction = instruction,
            reports = result.reports,
            receipt = result.receipt,
            mockCloudPayload = result.mockCloudPayload,
            experiencePayload = result.experiencePayload,
            verificationPassed = verifySuccessSample(result),
        )
    }

    /**
     * 样例2：可重试失败。
     */
    fun sampleFailureRetryable(): SampleResult {
        val taskId = "task-retryable-${clock.nowMillis()}"
        val instruction = "点击屏幕坐标(100, 200)"

        val manager = CloudTaskReceiptManager(deviceId, clock)
        val result = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            uploadOnline = false,
        ) { _ ->
            CloudTaskExecutionResult.failure(
                message = "点击失败：目标元素未找到",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true,
            )
        }

        return SampleResult(
            name = "可重试失败",
            taskId = taskId,
            instruction = instruction,
            reports = result.reports,
            receipt = result.receipt,
            mockCloudPayload = result.mockCloudPayload,
            experiencePayload = result.experiencePayload,
            verificationPassed = verifyRetryableFailureSample(result),
        )
    }

    /**
     * 样例3：不可重试失败。
     */
    fun sampleFailureNonRetryable(): SampleResult {
        val taskId = "task-nonretry-${clock.nowMillis()}"
        val instruction = "执行无效指令 xyz123"

        val manager = CloudTaskReceiptManager(deviceId, clock)
        val result = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            uploadOnline = false,
        ) { _ ->
            CloudTaskExecutionResult.failure(
                message = "指令格式无效，无法解析",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = false,
            )
        }

        return SampleResult(
            name = "不可重试失败",
            taskId = taskId,
            instruction = instruction,
            reports = result.reports,
            receipt = result.receipt,
            mockCloudPayload = result.mockCloudPayload,
            experiencePayload = result.experiencePayload,
            verificationPassed = verifyNonRetryableFailureSample(result),
        )
    }

    /**
     * 样例4：执行超时。
     */
    fun sampleTimeout(): SampleResult {
        val taskId = "task-timeout-${clock.nowMillis()}"
        val instruction = "等待页面加载完成"

        val manager = CloudTaskReceiptManager(deviceId, clock)
        val result = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            timeoutMillis = 5000L,
            uploadOnline = false,
        ) { _ ->
            CloudTaskExecutionResult.failure(
                message = "页面加载超时（超过5秒）",
                errorCode = CloudTaskErrorCode.EXECUTION_TIMEOUT,
                retryable = true,
            )
        }

        return SampleResult(
            name = "执行超时",
            taskId = taskId,
            instruction = instruction,
            reports = result.reports,
            receipt = result.receipt,
            mockCloudPayload = result.mockCloudPayload,
            experiencePayload = result.experiencePayload,
            verificationPassed = verifyTimeoutSample(result),
        )
    }

    /**
     * 样例5：权限缺失。
     */
    fun samplePermissionMissing(): SampleResult {
        val taskId = "task-permission-${clock.nowMillis()}"
        val instruction = "执行无障碍点击操作"

        val manager = CloudTaskReceiptManager(deviceId, clock)
        val result = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            uploadOnline = false,
        ) { _ ->
            CloudTaskExecutionResult.failure(
                message = "缺少无障碍服务权限",
                errorCode = CloudTaskErrorCode.PERMISSION_MISSING,
                retryable = false,
            )
        }

        return SampleResult(
            name = "权限缺失",
            taskId = taskId,
            instruction = instruction,
            reports = result.reports,
            receipt = result.receipt,
            mockCloudPayload = result.mockCloudPayload,
            experiencePayload = result.experiencePayload,
            verificationPassed = verifyPermissionMissingSample(result),
        )
    }

    /**
     * 样例6：离线缓存场景。
     */
    fun sampleOfflineCached(): SampleResult {
        val taskId = "task-offline-${clock.nowMillis()}"
        val instruction = "网络断开时执行任务并缓存结果"

        val manager = CloudTaskReceiptManager(deviceId, clock)

        // 执行第一个任务（进入离线缓存）
        val result1 = manager.executeLocalLoop(
            taskId = taskId,
            instruction = instruction,
            uploadOnline = false, // 离线模式
        ) { _ ->
            CloudTaskExecutionResult.success(
                message = "任务执行成功（离线缓存）",
            )
        }

        // 验证离线缓存状态
        val evidence = manager.getLocalLoopEvidence()
        val cached = result1.cachedForOfflineUpload

        return SampleResult(
            name = "离线缓存",
            taskId = taskId,
            instruction = instruction,
            reports = result1.reports,
            receipt = result1.receipt,
            mockCloudPayload = result1.mockCloudPayload,
            experiencePayload = result1.experiencePayload,
            verificationPassed = cached && evidence.pendingCount >= 1,
        )
    }

    // ============ 验证函数 ============

    private fun verifySuccessSample(result: CloudExecutorLocalRun): Boolean {
        return result.receipt.finalStatus == CloudTaskStatus.SUCCEEDED &&
            result.receipt.accepted &&
            result.reports.size == 3 && // RECEIVED -> RUNNING -> SUCCEEDED
            result.mockCloudPayload["status"] == "SUCCESS"
    }

    private fun verifyRetryableFailureSample(result: CloudExecutorLocalRun): Boolean {
        return result.receipt.finalStatus == CloudTaskStatus.FAILED &&
            result.receipt.retryable &&
            result.retryPlan != null &&
            result.mockCloudPayload["recoverable"] == "true"
    }

    private fun verifyNonRetryableFailureSample(result: CloudExecutorLocalRun): Boolean {
        return result.receipt.finalStatus == CloudTaskStatus.FAILED &&
            !result.receipt.retryable &&
            result.retryPlan == null &&
            result.mockCloudPayload["recoverable"] == "false"
    }

    private fun verifyTimeoutSample(result: CloudExecutorLocalRun): Boolean {
        return result.receipt.finalStatus == CloudTaskStatus.FAILED &&
            result.receipt.errorCode == CloudTaskErrorCode.EXECUTION_TIMEOUT &&
            result.receipt.retryable
    }

    private fun verifyPermissionMissingSample(result: CloudExecutorLocalRun): Boolean {
        return result.receipt.finalStatus == CloudTaskStatus.FAILED &&
            result.receipt.errorCode == CloudTaskErrorCode.PERMISSION_MISSING &&
            !result.receipt.retryable
    }

    /**
     * 样例执行报告。
     */
    data class SampleReport(
        val timestamp: Long,
        val deviceId: String,
        val samples: List<SampleResult>,
        val totalCount: Int,
        val passedCount: Int,
        val failedCount: Int,
    ) {
        fun toEvidenceLog(): String {
            return buildString {
                appendLine("=== DYQ-28 本地闭环样例执行证据 ===")
                appendLine("时间戳: $timestamp")
                appendLine("设备编号: $deviceId")
                appendLine("样例总数: $totalCount")
                appendLine("通过: $passedCount")
                appendLine("失败: $failedCount")
                appendLine()
                samples.forEachIndexed { index, sample ->
                    appendLine("--- 样例 ${index + 1}: ${sample.name} ---")
                    appendLine("任务编号: ${sample.taskId}")
                    appendLine("指令: ${sample.instruction.take(50)}")
                    appendLine("验证结果: ${if (sample.verificationPassed) "✅ 通过" else "❌ 失败"}")
                    appendLine("状态流转: ${sample.reports.joinToString(" -> ") { it.status.name }}")
                    appendLine("最终状态: ${sample.receipt.finalStatus}")
                    appendLine()
                }
                appendLine("=== 证据结束 ===")
            }
        }
    }
}
