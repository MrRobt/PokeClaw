package io.agents.pokeclaw.cloudnode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudExecutorNodeContractTest {

    @Test
    fun `本地模拟任务会按接收运行成功顺序上报`() {
        val task = CloudExecutorTask(
            taskId = "task-1",
            deviceId = "device-pixel-8",
            instruction = "打开设置查看电量",
            traceId = "trace-1",
            issuedAtMillis = 1000L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(1100L, 1200L, 1800L)))

        val reports = simulator.simulate(task) { _ ->
            CloudTaskExecutionResult.success("电量百分之八十", listOf("logcat://task-1"))
        }

        assertEquals(listOf(CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING, CloudTaskStatus.SUCCEEDED), reports.map { it.status })
        assertEquals("task-1", reports.last().taskId)
        assertEquals("trace-1", reports.last().traceId)
        assertEquals("电量百分之八十", reports.last().message)
        assertEquals(listOf("logcat://task-1"), reports.last().artifacts)
        assertFalse(reports.last().retryable)
    }

    @Test
    fun `本地模拟失败任务会生成可重试错误回传`() {
        val task = CloudExecutorTask(
            taskId = "task-2",
            deviceId = "device-pixel-8",
            instruction = "发送消息给联系人",
            traceId = "trace-2",
            issuedAtMillis = 2000L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(2100L, 2200L, 2300L)))

        val reports = simulator.simulate(task) { _ ->
            CloudTaskExecutionResult.failure(
                message = "无障碍服务未启用",
                errorCode = CloudTaskErrorCode.PERMISSION_MISSING,
                retryable = true,
            )
        }

        val finalReport = reports.last()
        assertEquals(CloudTaskStatus.FAILED, finalReport.status)
        assertEquals(CloudTaskErrorCode.PERMISSION_MISSING, finalReport.errorCode)
        assertTrue(finalReport.retryable)
        assertEquals("无障碍服务未启用", finalReport.message)
    }

    @Test
    fun `状态报告可以折叠为请求回执`() {
        val task = CloudExecutorTask(
            taskId = "task-3",
            deviceId = "device-pixel-8",
            instruction = "截图",
            traceId = "trace-3",
            issuedAtMillis = 3000L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(3100L, 3200L, 3300L)))

        val reports = simulator.simulate(task) { _ ->
            CloudTaskExecutionResult.success(
                message = "已完成截图",
                artifacts = listOf("screenshot://mock_001.png"),
            )
        }
        val receipt = CloudTaskReceipt.fromReports("req-1", reports)

        assertEquals("req-1", receipt.requestId)
        assertEquals("task-3", receipt.taskId)
        assertEquals("device-pixel-8", receipt.deviceId)
        assertEquals("trace-3", receipt.traceId)
        assertTrue(receipt.accepted)
        assertEquals(CloudTaskStatus.SUCCEEDED, receipt.finalStatus)
        assertEquals(CloudTaskErrorCode.NONE, receipt.errorCode)
        assertEquals(listOf("screenshot://mock_001.png"), receipt.artifacts)
        assertEquals(3300L, receipt.occurredAtMillis)
    }

    @Test
    fun `本地闭环成功任务会生成回执和模拟云端载荷`() {
        val loop = CloudExecutorLocalClosedLoop(
            deviceId = "device-pixel-8",
            clock = FixedClock(listOf(4000L, 4100L, 4200L, 4300L)),
            requestIdProvider = { "req-success" },
        )

        val run = loop.execute(
            taskId = "task-4",
            instruction = "截图",
            traceId = "trace-4",
            uploadOnline = true,
        ) { task ->
            assertEquals("截图", task.instruction)
            CloudTaskExecutionResult.success("已完成截图", listOf("screenshot://mock_002.png"))
        }

        assertEquals(listOf(CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING, CloudTaskStatus.SUCCEEDED), run.reports.map { it.status })
        assertEquals("req-success", run.receipt.requestId)
        assertEquals(CloudTaskStatus.SUCCEEDED, run.receipt.finalStatus)
        assertFalse(run.cachedForOfflineUpload)
        assertTrue(loop.offlineCache.dueReceipts(4300L).isEmpty())
        assertEquals("SUCCESS", run.mockCloudPayload["status"])
        assertEquals("screenshot://mock_002.png", run.mockCloudPayload["evidenceRefs"])
        assertEquals("task-4", run.experiencePayload["taskUuid"])
        assertEquals("TASK_EXECUTION_SUCCESS", run.experiencePayload["lessonType"])
        assertEquals("SUCCESS", run.experiencePayload["outcome"])
        assertEquals("已完成截图", run.experiencePayload["summary"])
        assertEquals("screenshot://mock_002.png", run.experiencePayload["evidenceRefs"])
    }

    @Test
    fun `本地闭环离线上报会缓存回执并按退避重试`() {
        val cache = CloudTaskOfflineReceiptCache(
            retryPolicy = CloudTaskRetryPolicy(baseDelayMillis = 1000L, maxDelayMillis = 8000L),
        )
        val loop = CloudExecutorLocalClosedLoop(
            deviceId = "device-pixel-8",
            clock = FixedClock(listOf(5000L, 5100L, 5200L, 5300L)),
            offlineCache = cache,
            requestIdProvider = { "req-offline" },
        )

        val run = loop.execute(
            taskId = "task-5",
            instruction = "点击确认",
            traceId = "trace-5",
            uploadOnline = false,
        ) {
            CloudTaskExecutionResult.success("已点击确认", listOf("mock://find_and_tap/确认"))
        }

        assertTrue(run.cachedForOfflineUpload)
        assertEquals(1, cache.size())
        assertTrue(cache.dueReceipts(5300L).isNotEmpty())

        val cached = cache.markUploadFailed("req-offline", nowMillis = 5400L)
        assertEquals(1, cached?.retryCount)
        assertEquals(6400L, cached?.nextAttemptAtMillis)
        assertTrue(cache.dueReceipts(6399L).isEmpty())
        assertEquals("req-offline", cache.dueReceipts(6400L).single().requestId)

        cache.markUploaded("req-offline")
        assertEquals(0, cache.size())
    }

    @Test
    fun `本地闭环可重试失败会给出重试计划和错误载荷`() {
        val loop = CloudExecutorLocalClosedLoop(
            deviceId = "device-pixel-8",
            clock = FixedClock(listOf(7000L, 7100L, 7200L, 7300L)),
            retryPolicy = CloudTaskRetryPolicy(baseDelayMillis = 2000L),
            requestIdProvider = { "req-failed" },
        )

        val run = loop.execute(
            taskId = "task-6",
            instruction = "打开微信",
            traceId = "trace-6",
            uploadOnline = true,
        ) {
            CloudTaskExecutionResult.failure(
                message = "目标应用暂不可用",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true,
            )
        }

        assertEquals(CloudTaskStatus.FAILED, run.receipt.finalStatus)
        assertTrue(run.receipt.retryable)
        assertEquals(CloudTaskErrorCode.TOOL_FAILED, run.receipt.errorCode)
        assertEquals(1, run.retryPlan?.nextAttempt)
        assertEquals(9300L, run.retryPlan?.nextAttemptAtMillis)
        assertEquals("FAILED", run.mockCloudPayload["status"])
        assertEquals("TOOL_FAILED", run.mockCloudPayload["errorCode"])
        assertEquals("true", run.mockCloudPayload["recoverable"])
        assertEquals("TASK_EXECUTION_RETRYABLE_FAILURE", run.experiencePayload["lessonType"])
        assertEquals("FAILED", run.experiencePayload["outcome"])
        assertEquals("目标应用暂不可用", run.experiencePayload["summary"])
        assertEquals("TOOL_FAILED", run.experiencePayload["errorCode"])
        assertEquals("1", run.experiencePayload["retryNextAttempt"])
    }

    @Test
    fun `本地闭环样例集会生成可读证据并覆盖六类端侧结果`() {
        val sampleNames = listOf("成功执行", "可重试失败", "不可重试失败", "执行超时", "权限缺失", "离线缓存")
        val samples = sampleNames.mapIndexed { index, name ->
            LocalClosedLoopSampler.SampleResult(
                name = name,
                taskId = "task-${index + 1}",
                instruction = "端侧本地闭环样例：$name",
                reports = listOf(
                    CloudTaskStatusReport(
                        taskId = "task-${index + 1}",
                        deviceId = "device-pixel-8",
                        traceId = "trace-${index + 1}",
                        status = CloudTaskStatus.RECEIVED,
                        message = "已接收",
                        occurredAtMillis = 1000L + index,
                    ),
                    CloudTaskStatusReport(
                        taskId = "task-${index + 1}",
                        deviceId = "device-pixel-8",
                        traceId = "trace-${index + 1}",
                        status = CloudTaskStatus.RUNNING,
                        message = "执行中",
                        occurredAtMillis = 2000L + index,
                    ),
                    CloudTaskStatusReport(
                        taskId = "task-${index + 1}",
                        deviceId = "device-pixel-8",
                        traceId = "trace-${index + 1}",
                        status = CloudTaskStatus.SUCCEEDED,
                        message = "已完成",
                        occurredAtMillis = 3000L + index,
                    ),
                ),
                receipt = CloudTaskReceipt(
                    requestId = "req-${index + 1}",
                    taskId = "task-${index + 1}",
                    deviceId = "device-pixel-8",
                    traceId = "trace-${index + 1}",
                    accepted = true,
                    finalStatus = CloudTaskStatus.SUCCEEDED,
                    message = "已完成",
                    errorCode = CloudTaskErrorCode.NONE,
                    retryable = false,
                    artifacts = emptyList(),
                    occurredAtMillis = 3000L + index,
                ),
                mockCloudPayload = mapOf("status" to "SUCCESS"),
                experiencePayload = mapOf("outcome" to "SUCCESS"),
                verificationPassed = true,
            )
        }
        val report = LocalClosedLoopSampler.SampleReport(
            timestamp = 80000L,
            deviceId = "device-pixel-8",
            samples = samples,
            totalCount = samples.size,
            passedCount = samples.count { it.verificationPassed },
            failedCount = samples.count { !it.verificationPassed },
        )
        val evidence = report.toEvidenceLog()

        assertEquals(6, report.totalCount)
        assertEquals(6, report.passedCount)
        assertEquals(0, report.failedCount)
        assertTrue(evidence.contains("DYQ-28 本地闭环样例执行证据"))
        assertTrue(evidence.contains("成功执行"))
        assertTrue(evidence.contains("可重试失败"))
        assertTrue(evidence.contains("不可重试失败"))
        assertTrue(evidence.contains("执行超时"))
        assertTrue(evidence.contains("权限缺失"))
        assertTrue(evidence.contains("离线缓存"))
    }

    private class FixedClock(private val values: List<Long>) : CloudExecutorClock {
        private var index = 0
        override fun nowMillis(): Long = values[index++].coerceAtLeast(0L)
    }
}
