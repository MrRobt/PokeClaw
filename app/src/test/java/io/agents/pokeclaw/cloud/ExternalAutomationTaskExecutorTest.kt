// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// ExternalAutomationTaskExecutor 单元测试

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * ExternalAutomationTaskExecutor 单元测试
 * 验证外部自动化任务执行器的占位实现
 */
class ExternalAutomationTaskExecutorTest {

    private lateinit var executor: ExternalAutomationTaskExecutor

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        executor = ExternalAutomationTaskExecutor()
    }

    // ── 基础执行测试 ────────────────────────────────────────────────────────────

    @Test
    fun `execute blank command returns TASK_REJECTED`() = runBlocking {
        val task = createMockTask(command = "   ")

        val result = executor.execute(task)

        assertFalse(result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
        assertFalse(result.retryable)
        assertTrue(result.message.contains("指令为空"))
    }

    @Test
    fun `execute returns placeholder success for valid command`() = runBlocking {
        val task = createMockTask(command = "打开微信")

        val result = executor.execute(task)

        assertTrue(result.success)
        assertTrue(result.message.contains("外部自动化"))
    }

    @Test
    fun `execute includes taskUuid in artifacts`() = runBlocking {
        val taskUuid = UUID.randomUUID().toString()
        val task = createMockTask(
            command = "打开微信",
            taskUuid = taskUuid
        )

        val result = executor.execute(task)

        assertTrue(result.artifacts.any { it.contains(taskUuid) })
        assertTrue(result.artifacts.any { it.contains("taskUuid") })
    }

    @Test
    fun `execute includes command in artifacts`() = runBlocking {
        val task = createMockTask(command = "打开微信应用并发送消息")

        val result = executor.execute(task)

        assertTrue(result.artifacts.any { it.contains("command") })
    }

    @Test
    fun `execute includes entry point in artifacts`() = runBlocking {
        val task = createMockTask(command = "打开微信")

        val result = executor.execute(task)

        assertTrue(result.artifacts.any { it.contains("external-automation") })
    }

    @Test
    fun `execute includes mode in artifacts`() = runBlocking {
        val task = createMockTask(
            command = "打开微信",
            mode = "interactive"
        )

        val result = executor.execute(task)

        assertTrue(result.artifacts.any { it.contains("mode") })
    }

    // ── 模型名称测试 ────────────────────────────────────────────────────────────

    @Test
    fun `getModelName returns default value`() {
        val modelName = executor.getModelName()

        assertEquals("external-automation-bridge", modelName)
    }

    @Test
    fun `custom model provider is used`() {
        val customExecutor = ExternalAutomationTaskExecutor(
            modelProvider = { "custom-bridge-v1" }
        )

        assertEquals("custom-bridge-v1", customExecutor.getModelName())
    }

    // ── 长命令截断测试 ─────────────────────────────────────────────────────────

    @Test
    fun `long command is truncated in artifacts`() = runBlocking {
        val longCommand = "a".repeat(200)
        val task = createMockTask(command = longCommand)

        val result = executor.execute(task)

        val commandArtifact = result.artifacts.find { it.startsWith("command:") }
        assertNotNull(commandArtifact)
        // 命令应该被截断到 120 字符
        assertTrue(commandArtifact!!.length <= 130)
    }

    // ── Helper 方法 ─────────────────────────────────────────────────────────────

    private fun createMockTask(
        taskUuid: String = UUID.randomUUID().toString(),
        command: String,
        mode: String? = null
    ): PendingTaskItem {
        return PendingTaskItem(
            taskUuid = taskUuid,
            command = command,
            mode = mode,
            createdAt = System.currentTimeMillis(),
            priority = "normal"
        )
    }
}
