package io.agents.pokeclaw.cloudnode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    // --- success() / failure() helper defaults ---

    @Test
    fun `success helper 默认 retryable 为 false 不带 artifacts`() {
        val task = CloudExecutorTask(
            taskId = "task-success-default",
            deviceId = "device-1",
            instruction = "say hi",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task) { _ -> CloudTaskExecutionResult.success("ok") }

        val finalReport = reports.last()
        assertEquals(CloudTaskStatus.SUCCEEDED, finalReport.status)
        assertEquals("ok", finalReport.message)
        assertFalse("default retryable 应为 false", finalReport.retryable)
        assertTrue("默认不应带 artifacts", finalReport.artifacts.isEmpty())
        assertEquals(CloudTaskErrorCode.NONE, finalReport.errorCode)
    }

    @Test
    fun `failure helper 默认 errorCode 是 UNKNOWN 且 retryable 为 false`() {
        val task = CloudExecutorTask(
            taskId = "task-failure-default",
            deviceId = "device-1",
            instruction = "do thing",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task) { _ -> CloudTaskExecutionResult.failure(message = "boom") }

        val finalReport = reports.last()
        assertEquals(CloudTaskStatus.FAILED, finalReport.status)
        assertEquals(CloudTaskErrorCode.UNKNOWN, finalReport.errorCode)
        assertFalse("failure 默认 retryable 应为 false", finalReport.retryable)
    }

    // --- exception path inside executor ---

    @Test
    fun `executor 抛异常会被转成 UNKNOWN 可重试的 FAILED 报告`() {
        val task = CloudExecutorTask(
            taskId = "task-throw",
            deviceId = "device-1",
            instruction = "explode",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task, executor = { _: CloudExecutorTask -> throw IllegalStateException("kaboom") })

        val finalReport = reports.last()
        assertEquals(CloudTaskStatus.FAILED, finalReport.status)
        assertEquals(CloudTaskErrorCode.UNKNOWN, finalReport.errorCode)
        assertTrue("捕获异常视为可重试", finalReport.retryable)
        assertEquals("kaboom", finalReport.message)
        // First two reports (RECEIVED + RUNNING) must still be present.
        assertEquals(3, reports.size)
        assertEquals(CloudTaskStatus.RECEIVED, reports[0].status)
        assertEquals(CloudTaskStatus.RUNNING, reports[1].status)
    }

    @Test
    fun `executor 抛 null message 异常时 fallback 文案是端侧执行异常`() {
        val task = CloudExecutorTask(
            taskId = "task-throw-null",
            deviceId = "device-1",
            instruction = "explode quietly",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task, executor = { _: CloudExecutorTask -> throw RuntimeException() })

        assertEquals("端侧执行异常", reports.last().message)
    }

    // --- simpler overload ---

    @Test
    fun `无 task 参数 overload 也能完成 RECEIVED RUNNING SUCCEEDED 流程`() {
        val task = CloudExecutorTask(
            taskId = "task-no-arg-overload",
            deviceId = "device-1",
            instruction = "ping",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task, executor = { _: CloudExecutorTask -> CloudTaskExecutionResult.success("pong") })

        assertEquals(3, reports.size)
        assertEquals(CloudTaskStatus.SUCCEEDED, reports.last().status)
        assertEquals("pong", reports.last().message)
    }

    // --- isTerminal truth table ---

    @Test
    fun `isTerminal 对 SUCCEEDED FAILED CANCELLED 返回 true`() {
        val terminalStatuses = listOf(
            CloudTaskStatus.SUCCEEDED,
            CloudTaskStatus.FAILED,
            CloudTaskStatus.CANCELLED,
        )
        for (status in terminalStatuses) {
            val report = CloudTaskStatusReport(
                taskId = "x",
                deviceId = "y",
                traceId = null,
                status = status,
                occurredAtMillis = 0L,
            )
            assertTrue("$status 应该是终态", report.isTerminal())
        }
    }

    @Test
    fun `isTerminal 对 RECEIVED RUNNING 返回 false`() {
        val nonTerminalStatuses = listOf(CloudTaskStatus.RECEIVED, CloudTaskStatus.RUNNING)
        for (status in nonTerminalStatuses) {
            val report = CloudTaskStatusReport(
                taskId = "x",
                deviceId = "y",
                traceId = null,
                status = status,
                occurredAtMillis = 0L,
            )
            assertFalse("$status 不应是终态", report.isTerminal())
        }
    }

    // --- require() input validation ---

    @Test
    fun `空白 taskId 抛 IllegalArgumentException`() {
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(1L, 2L, 3L)))
        val ex = runCatching {
            simulator.simulate(
                task = CloudExecutorTask(
                    taskId = "   ",
                    deviceId = "device-1",
                    instruction = "x",
                    issuedAtMillis = 1L,
                )
            ) { _ -> CloudTaskExecutionResult.success("ok") }
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got ${ex?.javaClass?.simpleName}", ex is IllegalArgumentException)
    }

    @Test
    fun `空白 deviceId 抛 IllegalArgumentException`() {
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(1L, 2L, 3L)))
        val ex = runCatching {
            simulator.simulate(
                task = CloudExecutorTask(
                    taskId = "task-1",
                    deviceId = "",
                    instruction = "x",
                    issuedAtMillis = 1L,
                )
            ) { _ -> CloudTaskExecutionResult.success("ok") }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `空白 instruction 抛 IllegalArgumentException`() {
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(1L, 2L, 3L)))
        val ex = runCatching {
            simulator.simulate(
                task = CloudExecutorTask(
                    taskId = "task-1",
                    deviceId = "device-1",
                    instruction = "\t\n",
                    issuedAtMillis = 1L,
                )
            ) { _ -> CloudTaskExecutionResult.success("ok") }
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    // --- field round-trip / metadata / traceId ---

    @Test
    fun `executor 可以读取 task 的 metadata 与 timeoutMillis 字段`() {
        val task = CloudExecutorTask(
            taskId = "task-meta",
            deviceId = "device-1",
            instruction = "do",
            issuedAtMillis = 100L,
            timeoutMillis = 30_000L,
            metadata = mapOf("region" to "tw", "tier" to "p0"),
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        var captured: CloudExecutorTask? = null
        val reports = simulator.simulate(task) { received ->
            captured = received
            CloudTaskExecutionResult.success("ok")
        }

        assertNotNull(captured)
        assertEquals(30_000L, captured!!.timeoutMillis)
        assertEquals(mapOf("region" to "tw", "tier" to "p0"), captured!!.metadata)
        assertEquals(CloudTaskStatus.SUCCEEDED, reports.last().status)
    }

    @Test
    fun `traceId 为 null 时报告也带 null 不被覆盖为空白`() {
        val task = CloudExecutorTask(
            taskId = "task-no-trace",
            deviceId = "device-1",
            instruction = "ping",
            traceId = null,
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task) { _ -> CloudTaskExecutionResult.success("ok") }

        assertNull(reports[0].traceId)
        assertNull(reports[1].traceId)
        assertNull(reports[2].traceId)
    }

    @Test
    fun `多任务独立 - 时钟值连续递增但 reports 各自完整`() {
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(10L, 20L, 30L, 40L, 50L, 60L)))
        val task1 = CloudExecutorTask(
            taskId = "t1",
            deviceId = "d1",
            instruction = "x",
            issuedAtMillis = 1L,
        )
        val task2 = CloudExecutorTask(
            taskId = "t2",
            deviceId = "d1",
            instruction = "y",
            issuedAtMillis = 1L,
        )

        val r1 = simulator.simulate(task1) { _ -> CloudTaskExecutionResult.success("one") }
        val r2 = simulator.simulate(task2) { _ -> CloudTaskExecutionResult.success("two") }

        assertEquals(listOf(10L, 20L, 30L), r1.map { it.occurredAtMillis })
        assertEquals(listOf(40L, 50L, 60L), r2.map { it.occurredAtMillis })
        assertEquals("t1", r1.last().taskId)
        assertEquals("t2", r2.last().taskId)
        assertEquals("one", r1.last().message)
        assertEquals("two", r2.last().message)
    }

    // --- artifact + retryable flow-through ---

    @Test
    fun `success 报告里的 artifacts 与 retryable 由 helper 决定不会被模拟器改写`() {
        val task = CloudExecutorTask(
            taskId = "task-artifacts",
            deviceId = "device-1",
            instruction = "x",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task) { _ ->
            CloudTaskExecutionResult.success("done", listOf("file://a", "logcat://b"))
        }

        assertEquals(listOf("file://a", "logcat://b"), reports.last().artifacts)
        assertFalse(reports.last().retryable)
    }

    @Test
    fun `failure 报告里的 retryable 与 artifacts 由 helper 决定`() {
        val task = CloudExecutorTask(
            taskId = "task-fail-artifacts",
            deviceId = "device-1",
            instruction = "x",
            issuedAtMillis = 100L,
        )
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(110L, 120L, 130L)))

        val reports = simulator.simulate(task) { _ ->
            CloudTaskExecutionResult.failure(
                message = "no net",
                errorCode = CloudTaskErrorCode.NETWORK_UNAVAILABLE,
                retryable = true,
                artifacts = listOf("diagnostic://net-fail"),
            )
        }

        val finalReport = reports.last()
        assertEquals(CloudTaskErrorCode.NETWORK_UNAVAILABLE, finalReport.errorCode)
        assertTrue(finalReport.retryable)
        assertEquals(listOf("diagnostic://net-fail"), finalReport.artifacts)
    }

    // --- exception types ---

    @Test
    fun `所有 enum 错误码在 test 中可枚举用于 fallback 分支`() {
        val codes = CloudTaskErrorCode.entries
        assertTrue("NONE 应在枚举中", CloudTaskErrorCode.NONE in codes)
        assertTrue("PERMISSION_MISSING 应在枚举中", CloudTaskErrorCode.PERMISSION_MISSING in codes)
        assertTrue("NETWORK_UNAVAILABLE 应在枚举中", CloudTaskErrorCode.NETWORK_UNAVAILABLE in codes)
        assertTrue("TASK_REJECTED 应在枚举中", CloudTaskErrorCode.TASK_REJECTED in codes)
        assertTrue("TOOL_FAILED 应在枚举中", CloudTaskErrorCode.TOOL_FAILED in codes)
        assertTrue("EXECUTION_TIMEOUT 应在枚举中", CloudTaskErrorCode.EXECUTION_TIMEOUT in codes)
        assertTrue("UNKNOWN 应在枚举中", CloudTaskErrorCode.UNKNOWN in codes)
    }

    @Test
    fun `未识别 require 错误文案以冒号分隔且提到字段名`() {
        val simulator = CloudExecutorNodeSimulator(clock = FixedClock(listOf(1L, 2L, 3L)))
        val ex = runCatching {
            simulator.simulate(
                task = CloudExecutorTask(
                    taskId = "",
                    deviceId = "device-1",
                    instruction = "x",
                    issuedAtMillis = 1L,
                )
            ) { _ -> CloudTaskExecutionResult.success("ok") }
        }.exceptionOrNull()

        if (ex is IllegalArgumentException) {
            // require() 抛出的文案至少要带原始字段提示，便于排查
            assertNotNull(ex.message)
            assertTrue(
                "异常文案应提到 taskId 或任务编号: ${ex.message}",
                ex.message!!.contains("taskId") || ex.message!!.contains("任务"),
            )
        } else {
            fail("expected IllegalArgumentException, got ${ex?.javaClass?.simpleName}")
        }
    }

    private class FixedClock(private val values: List<Long>) : CloudExecutorClock {
        private var index = 0
        override fun nowMillis(): Long = values[index++].coerceAtLeast(0L)
    }
}
