package io.agents.pokeclaw.cloudnode

import org.junit.Assert.*
import org.junit.Test

/**
 * 云端执行节点端到端测试。
 * 验证：任务接收 → 指令映射 → 模拟执行 → 状态上报 完整闭环。
 */
class CloudExecutorNodeTest {

    private val fixedClock = FixedClock(listOf(
        1000L,  // RECEIVED
        1100L,  // RUNNING
        1500L   // SUCCEEDED/FAILED
    ))
    private val deviceId = "test-device-001"

    @Test
    fun `打开应用任务完整闭环`() {
        val node = CloudExecutorNode(
            deviceId = deviceId,
            clock = fixedClock
        )
        val reports = mutableListOf<CloudTaskStatusReport>()
        node.addListener(object : CloudExecutorNode.TaskExecutionListener {
            override fun onStatusReport(report: CloudTaskStatusReport) {
                reports.add(report)
            }
        })

        val result = node.receiveAndExecute(
            taskId = "task-open-settings",
            instruction = "打开设置",
            traceId = "trace-001"
        )

        // 验证状态序列
        assertEquals(3, result.size)
        assertEquals(CloudTaskStatus.RECEIVED, result[0].status)
        assertEquals(CloudTaskStatus.RUNNING, result[1].status)
        assertEquals(CloudTaskStatus.SUCCEEDED, result[2].status)

        // 验证终态字段
        val finalReport = result.last()
        assertEquals("task-open-settings", finalReport.taskId)
        assertEquals(deviceId, finalReport.deviceId)
        assertEquals("trace-001", finalReport.traceId)
        assertTrue(finalReport.message.contains("模拟打开应用"))
        assertEquals(CloudTaskErrorCode.NONE, finalReport.errorCode)
        assertFalse(finalReport.retryable)

        // 验证监听器收到所有上报
        assertEquals(3, reports.size)
    }

    @Test
    fun `点击任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-tap-button",
            instruction = "点击确认按钮",
            traceId = "trace-002"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().message.contains("点击"))
        assertTrue(result.last().artifacts.any { it.contains("find_and_tap") })
    }

    @Test
    fun `输入文本任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-input-text",
            instruction = "输入hello world",
            traceId = "trace-003"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().message.contains("输入"))
    }

    @Test
    fun `截图任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-screenshot",
            instruction = "截图查看当前状态",
            traceId = "trace-004"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().artifacts.contains("screenshot://mock_001.png"))
    }

    @Test
    fun `返回任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-go-back",
            instruction = "返回上一级",
            traceId = "trace-005"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().message.contains("返回"))
    }

    @Test
    fun `搜索任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-search",
            instruction = "搜索天气预报",
            traceId = "trace-006"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().artifacts.any { it.contains("search") })
    }

    @Test
    fun `滑动任务完整闭环`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-swipe",
            instruction = "向下滑动",
            traceId = "trace-007"
        )

        assertEquals(CloudTaskStatus.SUCCEEDED, result.last().status)
        assertTrue(result.last().artifacts.any { it.contains("down") })
    }

    @Test
    fun `无法识别的指令返回失败`() {
        val node = CloudExecutorNode(deviceId = deviceId, clock = fixedClock)

        val result = node.receiveAndExecute(
            taskId = "task-unknown",
            instruction = "执行一些复杂的多步骤操作然后发送邮件",
            traceId = "trace-008"
        )

        assertEquals(CloudTaskStatus.FAILED, result.last().status)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.last().errorCode)
        assertFalse(result.last().retryable)
    }

    @Test
    fun `canHandle判断正确`() {
        val node = CloudExecutorNode(deviceId = deviceId)

        assertTrue(node.canHandle("打开微信"))
        assertTrue(node.canHandle("点击确定"))
        assertTrue(node.canHandle("截图"))
        assertTrue(node.canHandle("返回"))
        assertFalse(node.canHandle("这是一个完全无法识别的复杂指令需要AI理解"))
    }

    @Test
    fun `getSupportedActions返回动作列表`() {
        val node = CloudExecutorNode(deviceId = deviceId)
        val actions = node.getSupportedActions()

        assertEquals(9, actions.size)
        assertTrue(actions.contains("launch_app"))
        assertTrue(actions.contains("find_and_tap"))
        assertTrue(actions.contains("screenshot"))
    }

    private class FixedClock(private val values: List<Long>) : CloudExecutorClock {
        private var index = 0
        override fun nowMillis(): Long = values.getOrElse(index++) { System.currentTimeMillis() }
    }
}
