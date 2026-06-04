package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalAgentTaskExecutorTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
        SkillRegistry.loadBuiltInSkills()
    }

    @Test
    fun `打开应用任务会走本地桥接成功返回`() = runBlocking {
        val executor = LocalAgentTaskExecutor(
            deviceIdProvider = { "device-test-001" },
            nowProvider = { 123456L },
        )
        val result = executor.execute(
            PendingTaskItem(
                taskUuid = "task-open-settings",
                command = "打开设置",
                mode = "deterministic",
                createdAt = 123000L,
                priority = "high",
            )
        )

        assertTrue(result.success)
        assertTrue(result.message.contains("模拟打开应用"))
        assertEquals(CloudTaskErrorCode.NONE, result.errorCode)
        assertTrue(result.artifacts.any { it.contains("launch_app") })
        assertEquals("local-bridge", executor.getModelName())
    }

    @Test
    fun `点击任务会走桥接返回标准化证据`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val result = executor.execute(
            PendingTaskItem(
                taskUuid = "task-click-confirm",
                command = "点击确认",
                createdAt = 200000L,
            )
        )

        assertTrue(result.success)
        assertTrue(result.message.contains("点击"))
        assertTrue(result.artifacts.any { it.contains("find_and_tap") })
    }

    @Test
    fun `空指令会返回拒绝错误`() = runBlocking {
        val executor = LocalAgentTaskExecutor(modelProvider = { "local-bridge" })
        val result = executor.execute(
            PendingTaskItem(
                taskUuid = "task-empty",
                command = "   ",
                createdAt = 300000L,
            )
        )

        assertFalse(result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
        assertFalse(result.retryable)
        assertEquals("任务指令为空", result.message)
        assertEquals("local-bridge", executor.getModelName())
    }
}
