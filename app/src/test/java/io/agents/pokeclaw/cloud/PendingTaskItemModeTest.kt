// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-035：PendingTaskItem.mode 扩展（dry_run / prepare_only）测试。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskMode
import io.agents.pokeclaw.cloud.model.modeAsEnum
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.0 PendingTaskItem.mode 扩展测试套件。
 *
 * 覆盖：
 * 1. TaskMode.parse 4 个已知值（包含 snake_case 变体）
 * 2. TaskMode.parse null/空白/未知字符串的兜底
 * 3. TaskMode.isExecutable 仅 TASK/INTERACTIVE 可执行
 * 4. PendingTaskItem.modeAsEnum 扩展函数
 * 5. LocalAgentTaskExecutor 对 DRY_RUN / PREPARE_ONLY 的端到端短路
 */
class PendingTaskItemModeTest {

    @Test
    fun `TaskMode parse 4 个已知值`() {
        assertEquals(TaskMode.TASK, TaskMode.parse("TASK"))
        assertEquals(TaskMode.INTERACTIVE, TaskMode.parse("INTERACTIVE"))
        assertEquals(TaskMode.DRY_RUN, TaskMode.parse("dry_run"))
        assertEquals(TaskMode.PREPARE_ONLY, TaskMode.parse("prepare_only"))
    }

    @Test
    fun `TaskMode parse null 空白 未知字符串 - 全部兜底`() {
        assertEquals("null 应兜底为 TASK", TaskMode.TASK, TaskMode.parse(null))
        assertEquals("空串应兜底为 TASK", TaskMode.TASK, TaskMode.parse(""))
        assertEquals("空白应兜底为 TASK", TaskMode.TASK, TaskMode.parse("   "))
        assertEquals("未知 mode 字符串应兜底为 UNKNOWN", TaskMode.UNKNOWN, TaskMode.parse("nonsense_mode"))
        assertEquals("近似但非精确的字符串应兜底为 UNKNOWN", TaskMode.UNKNOWN, TaskMode.parse("TASK_EXTRA"))
    }

    @Test
    fun `TaskMode isExecutable 仅 TASK 和 INTERACTIVE 可执行`() {
        assertTrue(TaskMode.TASK.isExecutable)
        assertTrue(TaskMode.INTERACTIVE.isExecutable)
        assertFalse(TaskMode.DRY_RUN.isExecutable)
        assertFalse(TaskMode.PREPARE_ONLY.isExecutable)
        assertFalse("UNKNOWN 应视为不可执行，强制走人工审核", TaskMode.UNKNOWN.isExecutable)
    }

    @Test
    fun `PendingTaskItem modeAsEnum 扩展函数`() {
        val itemDryRun = PendingTaskItem(
            taskUuid = "u1",
            command = "x",
            mode = "dry_run",
            createdAt = 1L,
        )
        val itemNull = itemDryRun.copy(mode = null)
        val itemUnknown = itemDryRun.copy(mode = "weird")

        assertEquals(TaskMode.DRY_RUN, itemDryRun.modeAsEnum())
        assertEquals("null mode 应兜底为 TASK", TaskMode.TASK, itemNull.modeAsEnum())
        assertEquals(TaskMode.UNKNOWN, itemUnknown.modeAsEnum())
    }

    @Test
    fun `LocalAgentTaskExecutor 对 DRY_RUN 短路返回 success 不实际执行`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-dry-1",
            command = "打开微信",  // 即使这条指令可执行，也不应真的打开
            mode = "dry_run",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertTrue("DRY_RUN 必须成功", result.success)
        assertEquals(CloudTaskErrorCode.NONE, result.errorCode)
        assertTrue("message 应含 DRY_RUN_OK", result.message.contains("DRY_RUN_OK"))
        // artifacts 必须含 mode / taskUuid / dry_run 三项
        val artifacts = result.artifacts
        assertNotNull(artifacts)
        assertTrue(artifacts.any { it.startsWith("mode:") })
        assertTrue(artifacts.any { it == "dry_run:true" })
        assertTrue(artifacts.any { it.contains("uuid-dry-1") })
    }

    @Test
    fun `LocalAgentTaskExecutor 对 PREPARE_ONLY 短路返回 success 不进入主流程`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-prep-1",
            command = "打开微信",
            mode = "prepare_only",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertTrue(result.success)
        assertTrue("message 应含 PREPARED", result.message.contains("PREPARED"))
    }

    // --- TaskMode.parse 大小写不敏感 ---

    @Test
    fun `TaskMode parse 大小写不敏感`() {
        // parse 使用 ignoreCase 比较 raw 字段
        assertEquals(TaskMode.TASK, TaskMode.parse("task"))
        assertEquals(TaskMode.TASK, TaskMode.parse("Task"))
        assertEquals(TaskMode.TASK, TaskMode.parse("tAsK"))
        assertEquals(TaskMode.INTERACTIVE, TaskMode.parse("interactive"))
        assertEquals(TaskMode.INTERACTIVE, TaskMode.parse("Interactive"))
        assertEquals(TaskMode.DRY_RUN, TaskMode.parse("DRY_RUN"))
        assertEquals(TaskMode.DRY_RUN, TaskMode.parse("Dry_Run"))
        assertEquals(TaskMode.DRY_RUN, TaskMode.parse("DRY_run"))
        assertEquals(TaskMode.PREPARE_ONLY, TaskMode.parse("PREPARE_ONLY"))
        assertEquals(TaskMode.PREPARE_ONLY, TaskMode.parse("Prepare_Only"))
    }

    // --- TaskMode.raw 字段值 ---

    @Test
    fun `TaskMode raw 字段值与 SerializedName 对齐`() {
        assertEquals("TASK", TaskMode.TASK.raw)
        assertEquals("INTERACTIVE", TaskMode.INTERACTIVE.raw)
        assertEquals("dry_run", TaskMode.DRY_RUN.raw)
        assertEquals("prepare_only", TaskMode.PREPARE_ONLY.raw)
        assertEquals("UNKNOWN", TaskMode.UNKNOWN.raw)
    }

    // --- TaskMode enum count ---

    @Test
    fun `TaskMode entries 数量为 5`() {
        assertEquals(5, TaskMode.entries.size)
    }

    // --- TaskMode.stubResult() ---

    @Test
    fun `TaskMode stubResult DRY_RUN 返回 DRY_RUN_OK`() {
        assertEquals("DRY_RUN_OK", TaskMode.DRY_RUN.stubResult())
    }

    @Test
    fun `TaskMode stubResult PREPARE_ONLY 返回 PREPARED`() {
        assertEquals("PREPARED", TaskMode.PREPARE_ONLY.stubResult())
    }

    @Test
    fun `TaskMode stubResult TASK INTERACTIVE UNKNOWN 都返回 null`() {
        // 仅 stub 模式有非 null stub 字符串，其他模式由业务逻辑自行填 result
        assertNull(TaskMode.TASK.stubResult())
        assertNull(TaskMode.INTERACTIVE.stubResult())
        assertNull(TaskMode.UNKNOWN.stubResult())
    }

    // --- LocalAgentTaskExecutor 边界 ---

    @Test
    fun `LocalAgentTaskExecutor 对 blank command 返回 TASK_REJECTED 失败`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-blank-1",
            command = "",
            mode = "TASK",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertFalse("空 command 必须失败", result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
        assertEquals(false, result.retryable)
        assertTrue("message 应含 '任务指令为空'", result.message.contains("任务指令为空"))
    }

    @Test
    fun `LocalAgentTaskExecutor 对 whitespace-only command 返回 TASK_REJECTED 失败`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-blank-2",
            command = "   \t\n  ",
            mode = "TASK",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertFalse(result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
    }

    @Test
    fun `LocalAgentTaskExecutor 对 null mode 不走 stub 短路 进入主流程`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        // 不可映射的命令 → 主流程会拒绝
        val task = PendingTaskItem(
            taskUuid = "uuid-null-mode",
            command = "xyz_unknown_command_xyz",
            mode = null,
            createdAt = 1L,
        )
        val result = executor.execute(task)
        // null 模式视同 TASK，进入主流程；该指令不可映射 → TASK_REJECTED
        assertFalse("null mode 必须进入主流程（不短路）", result.message.contains("DRY_RUN_OK"))
        assertFalse(result.message.contains("PREPARED"))
    }

    @Test
    fun `LocalAgentTaskExecutor DRY_RUN artifacts 包含 taskUuid command 截断`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val longCommand = "x".repeat(200)
        val task = PendingTaskItem(
            taskUuid = "uuid-dry-artifacts",
            command = longCommand,
            mode = "dry_run",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertTrue(result.success)
        val artifacts = result.artifacts
        // command 截断到 120 字符
        assertTrue("artifacts 必须包含截断的 command 字段", artifacts.any { it.startsWith("command:") && it.length <= "command:".length + 120 })
        assertTrue("artifacts 必须包含 taskUuid", artifacts.any { it.contains("uuid-dry-artifacts") })
        // artifacts 中 dry_run:true 必须存在（标志位）
        assertTrue("artifacts 必须包含 dry_run:true 标志", artifacts.contains("dry_run:true"))
    }

    @Test
    fun `LocalAgentTaskExecutor PREPARE_ONLY artifacts 包含 mode 字段`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-prep-art",
            command = "打开微信",
            mode = "prepare_only",
            createdAt = 1L,
        )
        val result = executor.execute(task)
        assertTrue(result.success)
        val artifacts = result.artifacts
        assertTrue("artifacts 必须包含 mode:prepare_only", artifacts.any { it == "mode:prepare_only" })
        assertTrue("artifacts 必须包含 taskUuid", artifacts.any { it.contains("uuid-prep-art") })
        assertTrue("artifacts 必须包含 command", artifacts.any { it.startsWith("command:") })
        // dry_run:true 在 PREPARE_ONLY 模式下也会出现（LocalAgentTaskExecutor 当前实现统一加）
        assertTrue("artifacts 必须包含 dry_run:true 标志", artifacts.contains("dry_run:true"))
    }

    // --- PendingTaskItem data class 行为 ---

    @Test
    fun `PendingTaskItem data class equality 与 copy 工作正常`() {
        val a = PendingTaskItem(
            taskUuid = "u1",
            command = "c1",
            mode = "TASK",
            createdAt = 100L,
            priority = "NORMAL",
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(priority = "HIGH")
        assertEquals("NORMAL", a.priority)
        assertEquals("HIGH", c.priority)
        assertNotEquals(a, c)
    }
}
