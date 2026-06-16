// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// TaskRetryQueue + TaskDeadLetterQueue 单测 — 队列核心契约：入队上限、过期 poll、按 id 操作、snapshot 排序。

package io.agents.pokeclaw.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 共用 setUp/tearDown：两个队列都是单例，clear() 保证测试隔离。
 */
class TaskRetryDeadLetterQueueTest {

    @Before
    fun setUp() {
        TaskRetryQueue.getInstance().clear()
        TaskDeadLetterQueue.getInstance().clear()
    }

    @After
    fun tearDown() {
        TaskRetryQueue.getInstance().clear()
        TaskDeadLetterQueue.getInstance().clear()
    }

    // ── TaskRetryQueue ──

    @Test
    fun `TaskRetryQueue 入队 - 返回 true 且 nextAttemptAtMillis 在未来`() {
        val now = System.currentTimeMillis()
        val r = TaskRetryQueue.getInstance().enqueue(
            taskUuid = "t1",
            command = "cmd",
            attempts = 1,
            lastError = "transient failure",
        )
        assertTrue(r)
        val entry = TaskRetryQueue.getInstance().snapshot().single()
        assertEquals("t1", entry.taskUuid)
        assertEquals("cmd", entry.command)
        assertEquals(1, entry.attempts)
        assertTrue("nextAttemptAtMillis 应在 now 之后", entry.nextAttemptAtMillis > now)
    }

    @Test
    fun `TaskRetryQueue 达 maxAttempts 拒绝入队 返回 false (业务契约：应转死信)`() {
        val r = TaskRetryQueue.getInstance().enqueue(
            taskUuid = "t1",
            command = "cmd",
            attempts = TaskRetryQueue.getInstance().maxAttempts,  // 3
            lastError = "exhausted",
        )
        assertFalse("attempts >= maxAttempts 应拒绝入队", r)
        assertEquals(0, TaskRetryQueue.getInstance().size())
    }

    @Test
    fun `TaskRetryQueue maxAttempts 上限固定为 3 (回归保护)`() {
        assertEquals(3, TaskRetryQueue.getInstance().maxAttempts)
    }

    @Test
    fun `TaskRetryQueue 入队 - lastError 截断到 200 字符`() {
        val longError = "X".repeat(500)
        TaskRetryQueue.getInstance().enqueue("t1", "cmd", attempts = 1, lastError = longError)
        val entry = TaskRetryQueue.getInstance().snapshot().single()
        assertEquals(200, entry.lastError.length)
    }

    @Test
    fun `TaskRetryQueue 同 taskUuid 二次入队 - 覆盖 (entries 是 Map)`() {
        val q = TaskRetryQueue.getInstance()
        q.enqueue("t1", "cmd-v1", attempts = 1, lastError = "e1")
        q.enqueue("t1", "cmd-v2", attempts = 2, lastError = "e2")
        assertEquals("同 taskUuid 应覆盖不重复", 1, q.size())
        assertEquals("cmd-v2", q.snapshot().single().command)
        assertEquals(2, q.snapshot().single().attempts)
    }

    @Test
    fun `TaskRetryQueue pollReady - 仅返回 nextAttemptAtMillis 已到期的条目`() {
        val q = TaskRetryQueue.getInstance()
        q.enqueue("t1", "c1", attempts = 1, lastError = "e1")
        q.enqueue("t2", "c2", attempts = 1, lastError = "e2")
        // 入队时 nextAttemptAtMillis = System.currentTimeMillis() + delayMs (大约 1s)
        // 用比当前 wall-clock 早的时间 → 没有条目到期
        val readyImmediate = q.pollReady(nowMillis = 1_000_000L)  // 1970 年
        assertEquals("未来时间的条目不应 poll 出来", 0, readyImmediate.size)

        // 用比当前 wall-clock 晚很久的时间 → 全部到期
        val readyFuture = q.pollReady(nowMillis = System.currentTimeMillis() + 10 * 60 * 1000L)
        assertEquals("10 分钟后应能 poll 出全部", 2, readyFuture.size)
    }

    @Test
    fun `TaskRetryQueue remove - 按 id 移除`() {
        val q = TaskRetryQueue.getInstance()
        q.enqueue("t1", "c", attempts = 1, lastError = "e")
        assertEquals(1, q.size())
        q.remove("t1")
        assertEquals(0, q.size())
    }

    @Test
    fun `TaskRetryQueue remove 不存在的 id - 不抛错`() {
        val q = TaskRetryQueue.getInstance()
        q.remove("nonexistent")
        assertEquals(0, q.size())
    }

    @Test
    fun `TaskRetryQueue clear - 清空所有条目`() {
        val q = TaskRetryQueue.getInstance()
        q.enqueue("t1", "c", 1, "e")
        q.enqueue("t2", "c", 1, "e")
        assertEquals(2, q.size())
        q.clear()
        assertEquals(0, q.size())
    }

    // ── TaskDeadLetterQueue ──

    @Test
    fun `TaskDeadLetterQueue 入队 - 默认值`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", command = "cmd", attempts = 5, lastError = "exhausted")
        val entry = q.snapshot().single()
        assertEquals("t1", entry.taskUuid)
        assertEquals("cmd", entry.command)
        assertEquals(5, entry.attempts)
        assertEquals("exhausted", entry.lastError)
        assertTrue("history 默认空列表", entry.history.isEmpty())
        assertTrue("deadAtMillis 在 now 附近", entry.deadAtMillis <= System.currentTimeMillis())
    }

    @Test
    fun `TaskDeadLetterQueue 入队 - 带 history 完整保存`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue(
            taskUuid = "t1",
            command = "cmd",
            attempts = 5,
            lastError = "final",
            history = listOf("err1", "err2", "err3"),
        )
        val entry = q.snapshot().single()
        assertEquals(listOf("err1", "err2", "err3"), entry.history)
    }

    @Test
    fun `TaskDeadLetterQueue lastError 截断 200 字符`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", "cmd", 5, "X".repeat(500))
        assertEquals(200, q.snapshot().single().lastError.length)
    }

    @Test
    fun `TaskDeadLetterQueue snapshot 按 deadAtMillis 降序`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t-old", "c", 1, "e1")
        Thread.sleep(5)
        q.enqueue("t-mid", "c", 1, "e2")
        Thread.sleep(5)
        q.enqueue("t-new", "c", 1, "e3")

        val snap = q.snapshot()
        assertEquals(3, snap.size)
        // snapshot 排序按 deadAtMillis 降序 → 最新入队的在最前
        assertEquals("t-new", snap[0].taskUuid)
        assertEquals("t-mid", snap[1].taskUuid)
        assertEquals("t-old", snap[2].taskUuid)
    }

    @Test
    fun `TaskDeadLetterQueue retry - 移除并返回 entry`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", "cmd", 5, "err")
        val removed = q.retry("t1")
        assertNotNull(removed)
        assertEquals("t1", removed!!.taskUuid)
        assertEquals(0, q.size())
    }

    @Test
    fun `TaskDeadLetterQueue retry 不存在的 id - 返回 null 不抛错`() {
        val q = TaskDeadLetterQueue.getInstance()
        assertNull(q.retry("nonexistent"))
    }

    @Test
    fun `TaskDeadLetterQueue discard - 按 id 移除 返回 true`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", "c", 1, "e")
        assertTrue(q.discard("t1"))
        assertEquals(0, q.size())
    }

    @Test
    fun `TaskDeadLetterQueue discard 不存在的 id - 返回 false`() {
        val q = TaskDeadLetterQueue.getInstance()
        assertFalse(q.discard("nonexistent"))
    }

    @Test
    fun `TaskDeadLetterQueue get - 按 id 查询不删除`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", "c", 1, "e")
        val got = q.get("t1")
        assertNotNull(got)
        assertEquals("t1", got!!.taskUuid)
        assertEquals("查询不应删除", 1, q.size())
    }

    @Test
    fun `TaskDeadLetterQueue get 不存在的 id - 返回 null`() {
        assertNull(TaskDeadLetterQueue.getInstance().get("nonexistent"))
    }

    @Test
    fun `TaskDeadLetterQueue clear - 清空所有`() {
        val q = TaskDeadLetterQueue.getInstance()
        q.enqueue("t1", "c", 1, "e")
        q.enqueue("t2", "c", 1, "e")
        assertEquals(2, q.size())
        q.clear()
        assertEquals(0, q.size())
    }

    @Test
    fun `TaskDeadLetterQueue size 与 entries 数同步`() {
        val q = TaskDeadLetterQueue.getInstance()
        assertEquals(0, q.size())
        q.enqueue("t1", "c", 1, "e")
        assertEquals(1, q.size())
        q.enqueue("t2", "c", 1, "e")
        assertEquals(2, q.size())
        q.discard("t1")
        assertEquals(1, q.size())
    }
}
