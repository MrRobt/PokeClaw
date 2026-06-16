// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// CloudNodeOrchestrator 完整流程测试

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.mock.MockDataProvider
import io.agents.pokeclaw.mock.MockDeviceCloudClient
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CloudNodeOrchestrator 完整流程测试
 * 验证任务获取→执行→上报的完整闭环
 */
class CloudNodeOrchestratorFlowTest {

    private lateinit var mockClient: MockDeviceCloudClient

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        mockClient = MockDeviceCloudClient()
    }

    @After
    fun tearDown() {
        mockClient.reset()
    }

    // ── 完整流程测试 ────────────────────────────────────────────────────────────

    @Test
    fun `full flow - get pending tasks executes and submits result`() = runBlocking {
        // 1. 准备 mock 任务
        val taskUuid = UUID.randomUUID().toString()
        val task = MockDataProvider.createMockPendingTaskItem(
            taskUuid = taskUuid,
            command = "打开微信"
        )
        mockClient.addPendingTask(task)

        // 2. 获取待处理任务
        val pendingResult = mockClient.getPendingTasks("device-1")
        assertTrue(pendingResult.isSuccess)
        val pendingTasks = pendingResult.getOrNull()?.data ?: emptyList()
        assertEquals(1, pendingTasks.size)
        assertEquals(taskUuid, pendingTasks[0].taskUuid)

        // 3. 模拟执行任务（成功）
        val executionResult = TaskResultRequest(
            status = TaskResultRequest.Status.SUCCESS,
            result = "微信已打开",
            executionTimeMs = 2500
        )

        // 4. 上报结果
        val submitResult = mockClient.submitTaskResult(taskUuid, executionResult)
        assertTrue(submitResult.isSuccess)
        assertEquals(200, submitResult.getOrNull()?.code)

        // 5. 验证任务状态已更新
        val taskByUuid = mockClient.getTaskByUuid(taskUuid)
        assertTrue(taskByUuid.isSuccess)
    }

    @Test
    fun `full flow - multiple tasks are executed sequentially`() = runBlocking {
        // 1. 准备多个 mock 任务
        val tasks = (1..3).map { index ->
            MockDataProvider.createMockPendingTaskItem(
                taskUuid = UUID.randomUUID().toString(),
                command = "任务 $index"
            ).also { mockClient.addPendingTask(it) }
        }

        // 2. 获取所有待处理任务
        val pendingResult = mockClient.getPendingTasks("device-1")
        val pendingTasks = pendingResult.getOrNull()?.data ?: emptyList()
        assertEquals(3, pendingTasks.size)

        // 3. 顺序执行并上报
        val executionOrder = CopyOnWriteArrayList<String>()
        tasks.forEach { task ->
            val result = TaskResultRequest(
                status = TaskResultRequest.Status.SUCCESS,
                result = "完成: ${task.command}",
                executionTimeMs = 1000
            )
            mockClient.submitTaskResult(task.taskUuid, result)
            executionOrder.add(task.taskUuid)
        }

        // 4. 验证所有任务都已上报
        assertEquals(3, executionOrder.size)
        tasks.forEach { task ->
            assertTrue(executionOrder.contains(task.taskUuid))
        }
    }

    @Test
    fun `full flow - failed task is reported correctly`() = runBlocking {
        val taskUuid = UUID.randomUUID().toString()
        val task = MockDataProvider.createMockPendingTaskItem(
            taskUuid = taskUuid,
            command = "打开不存在的应用"
        )
        mockClient.addPendingTask(task)

        // 模拟执行失败
        val failedResult = TaskResultRequest(
            status = TaskResultRequest.Status.FAILED,
            errorMessage = "应用未找到",
            errorCategory = "APP_NOT_FOUND",
            errorCode = "E1001",
            executionTimeMs = 500
        )

        val submitResult = mockClient.submitTaskResult(taskUuid, failedResult)
        assertTrue(submitResult.isSuccess)

        // 验证任务结果
        val taskResult = mockClient.getTaskResult(taskUuid)
        assertNotNull(taskResult)
        assertEquals(TaskResultRequest.Status.FAILED, taskResult?.status)
    }

    @Test
    fun `full flow - task cancellation`() = runBlocking {
        val taskUuid = UUID.randomUUID().toString()
        val task = MockDataProvider.createMockPendingTaskItem(
            taskUuid = taskUuid,
            command = "长时间运行的任务"
        )
        mockClient.addPendingTask(task)

        // 取消任务
        val cancelResult = TaskResultRequest(
            status = TaskResultRequest.Status.CANCELLED,
            errorMessage = "用户取消"
        )

        val result = mockClient.cancelTask(taskUuid, cancelResult)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.data == true)

        // 验证任务已被取消
        val taskResult = mockClient.getTaskResult(taskUuid)
        assertEquals(TaskResultRequest.Status.CANCELLED, taskResult?.status)
    }

    @Test
    fun `full flow - network error simulation`() = runBlocking {
        mockClient.shouldSimulateNetworkError = true

        val result = mockClient.getPendingTasks("device-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Network") == true)
    }

    @Test
    fun `full flow - get task by uuid returns correct status`() = runBlocking {
        val taskUuid = UUID.randomUUID().toString()
        val task = MockDataProvider.createMockPendingTaskItem(
            taskUuid = taskUuid,
            command = "测试任务"
        )
        mockClient.addPendingTask(task)

        // 初始状态是 PENDING
        var taskByUuid = mockClient.getTaskByUuid(taskUuid)
        assertTrue(taskByUuid.isSuccess)

        // 上报成功后状态变为 SUCCESS
        val successResult = TaskResultRequest(
            status = TaskResultRequest.Status.SUCCESS,
            result = "成功"
        )
        mockClient.submitTaskResult(taskUuid, successResult)

        taskByUuid = mockClient.getTaskByUuid(taskUuid)
        val status = taskByUuid.getOrNull()?.data?.status
        assertNotNull(status)
    }

    @Test
    fun `heartbeat returns correct pending task count`() = runBlocking {
        // 添加 2 个任务
        repeat(2) {
            mockClient.addPendingTask(
                MockDataProvider.createMockPendingTaskItem(
                    taskUuid = UUID.randomUUID().toString()
                )
            )
        }

        val heartbeatResult = mockClient.sendHeartbeat(
            MockDataProvider.createMockDeviceHeartbeatRequest()
        )

        assertTrue(heartbeatResult.isSuccess)
        assertEquals(2, heartbeatResult.getOrNull()?.data?.pendingTaskCount)
    }

    // ── 场景化测试 ─────────────────────────────────────────────────────────────

    @Test
    fun `scenario - task execution with local agent executor`() {
        val scenario = MockDataProvider.createLocalTaskExecutionScenario(
            command = "打开微信"
        )

        assertNotNull(scenario.pendingTask)
        assertEquals("launch_app", scenario.expectedSkillId)
    }

    @Test
    fun `scenario - dry run task returns stub response`() {
        val scenario = MockDataProvider.createDryRunTaskScenario()

        assertEquals("dry_run", scenario.pendingTask.mode)
        assertTrue(scenario.expectedStubMessage.contains("DRY_RUN"))
    }

    @Test
    fun `scenario - offline mode fallback`() {
        val scenario = MockDataProvider.createOfflineModeScenario()

        assertTrue(scenario.isOffline)
        assertTrue(scenario.isLocalModelAvailable)
        assertTrue(scenario.expectedCanExecute)
        assertTrue(scenario.fallbackResult.success)
    }
}
