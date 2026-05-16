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

        val reports = simulator.simulate(task) {
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

        val reports = simulator.simulate(task) {
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

    private class FixedClock(private val values: List<Long>) : CloudExecutorClock {
        private var index = 0
        override fun nowMillis(): Long = values[index++].coerceAtLeast(0L)
    }
}
