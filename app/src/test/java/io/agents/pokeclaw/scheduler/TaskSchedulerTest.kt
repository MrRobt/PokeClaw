// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TaskScheduler.computeNextRunAt] which is the pure-logic
 * routing layer for CRON / ONCE / INTERVAL tasks. Persistence and
 * AlarmManager wiring are not exercised here.
 */
class TaskSchedulerTest {

    @Test
    fun computeNextRunAt_cron_returnsFutureMillis() {
        val now = 1_000_000L
        val task = ScheduledTask(
            id = "t1",
            name = "every-minute",
            type = ScheduledTask.Type.CRON,
            schedule = "* * * * *",
            prompt = "tick",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        // Should be strictly after `now` (caller's clock is irrelevant, just > 0).
        assertTrue("next must be > 0, got $next", next > 0L)
        assertNotEquals(now, next)
    }

    @Test
    fun computeNextRunAt_cron_malformedReturnsZero() {
        val task = ScheduledTask(
            id = "t1",
            name = "bad",
            type = ScheduledTask.Type.CRON,
            schedule = "not a cron",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun computeNextRunAt_once_returnsScheduleWhenFuture() {
        val now = System.currentTimeMillis()
        val future = now + 60_000L
        val task = ScheduledTask(
            id = "t1",
            name = "one-shot",
            type = ScheduledTask.Type.ONCE,
            schedule = future.toString(),
            prompt = "ping",
        )
        assertEquals(future, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun computeNextRunAt_once_returnsZeroWhenMalformed() {
        val task = ScheduledTask(
            id = "t1",
            name = "bad-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "not a number",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun computeNextRunAt_interval_advancesBySeconds() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "every-90s",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "90",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        assertEquals(true, next in (before + 90_000L)..(after + 90_000L))
    }

    @Test
    fun computeNextRunAt_interval_belowMinimumReturnsZero() {
        val task = ScheduledTask(
            id = "t1",
            name = "too-fast",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "30", // < MIN_INTERVAL_SEC (60)
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun computeNextRunAt_interval_malformedReturnsZero() {
        val task = ScheduledTask(
            id = "t1",
            name = "bad-int",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "abc",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    // --- ONCE 边界 ---

    @Test
    fun `computeNextRunAt ONCE past timestamp 强制提升为 now`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "past-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "1000",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        assertTrue("next 必须在 [before, after] 内, got $next", next in before..after)
    }

    @Test
    fun `computeNextRunAt ONCE 0 schedule 提升为 now`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "zero-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "0",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        assertTrue("next 必须在 [before, after] 内, got $next", next in before..after)
    }

    @Test
    fun `computeNextRunAt ONCE 负数 schedule 提升为 now`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "neg-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "-100",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        assertTrue(next in before..after)
    }

    @Test
    fun `computeNextRunAt ONCE empty schedule 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "empty-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt ONCE whitespace schedule 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "ws-once",
            type = ScheduledTask.Type.ONCE,
            schedule = "   ",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    // --- CRON 边界 ---

    @Test
    fun `computeNextRunAt CRON empty schedule 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "empty-cron",
            type = ScheduledTask.Type.CRON,
            schedule = "",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt CRON 6 字段表达式 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "6-field",
            type = ScheduledTask.Type.CRON,
            schedule = "0 * * * * *",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt CRON 1 字段表达式 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "1-field",
            type = ScheduledTask.Type.CRON,
            schedule = "*",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    // --- INTERVAL 边界 ---

    @Test
    fun `computeNextRunAt INTERVAL exactly 60 equals MIN_INTERVAL_SEC 可行`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "min-interval",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "60",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        assertTrue(next in (before + 60_000L)..(after + 60_000L))
    }

    @Test
    fun `computeNextRunAt INTERVAL 59 below MIN 1 秒 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "59",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "59",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt INTERVAL 86400 一整天 推进 24h`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "daily",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "86400",
            prompt = "x",
        )
        val next = TaskScheduler.computeNextRunAt(task)
        val after = System.currentTimeMillis()
        val expectedDelta = 86_400_000L
        assertTrue(next in (before + expectedDelta)..(after + expectedDelta))
    }

    @Test
    fun `computeNextRunAt INTERVAL empty string 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "empty-int",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt INTERVAL 0 schedule 低于 MIN 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "zero-int",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "0",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    @Test
    fun `computeNextRunAt INTERVAL 负数 schedule 低于 MIN 返回 0L`() {
        val task = ScheduledTask(
            id = "t1",
            name = "neg-int",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "-100",
            prompt = "x",
        )
        assertEquals(0L, TaskScheduler.computeNextRunAt(task))
    }

    // --- ScheduledTask 自身 ---

    @Test
    fun `ScheduledTask Type 枚举数量为 3`() {
        assertEquals(3, ScheduledTask.Type.values().size)
    }

    @Test
    fun `MIN_INTERVAL_SEC 常量等于 60`() {
        assertEquals(60L, ScheduledTask.MIN_INTERVAL_SEC)
    }

    @Test
    fun `ScheduledTask 默认值 enabled=true lastRunAt=0 nextRunAt=0 createdAt 在 currentTimeMillis 附近`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask(
            id = "t1",
            name = "n",
            type = ScheduledTask.Type.CRON,
            schedule = "* * * * *",
            prompt = "x",
        )
        val after = System.currentTimeMillis()
        assertTrue(task.enabled)
        assertEquals(0L, task.lastRunAt)
        assertEquals(0L, task.nextRunAt)
        assertTrue("createdAt 必须在 [before, after], got ${task.createdAt}", task.createdAt in before..after)
    }

    @Test
    fun `ScheduledTask data class copy 不改变 id`() {
        val original = ScheduledTask(
            id = "t-1",
            name = "n",
            type = ScheduledTask.Type.CRON,
            schedule = "* * * * *",
            prompt = "x",
        )
        val copied = original.copy(enabled = false, lastRunAt = 5000L)
        assertEquals("t-1", copied.id)
        assertEquals(false, copied.enabled)
        assertEquals(5000L, copied.lastRunAt)
    }

    @Test
    fun `INVALID 常量字符串定义`() {
        assertEquals("INVALID_INTERVAL", ScheduledTask.INVALID_INTERVAL)
        assertEquals("INVALID_CRON", ScheduledTask.INVALID_CRON)
        assertEquals("INVALID_TIMESTAMP", ScheduledTask.INVALID_TIMESTAMP)
    }

    @Test
    fun `computeNextRunAt 连续 10 次返回单调非降序结果`() {
        // 相同 INTERVAL task 连续调用 10 次：结果都基于当时的 now
        val task = ScheduledTask(
            id = "t1",
            name = "n",
            type = ScheduledTask.Type.INTERVAL,
            schedule = "60",
            prompt = "x",
        )
        val beforeAll = System.currentTimeMillis()
        val all = (0 until 10).map { TaskScheduler.computeNextRunAt(task) }
        val afterAll = System.currentTimeMillis()
        // 每次结果都应在 [beforeAll+60s, afterAll+60s] 窗口内
        for ((i, v) in all.withIndex()) {
            assertTrue("第 $i 次 next 必须在 [beforeAll+60s, afterAll+60s], got $v", v in (beforeAll + 60_000L)..(afterAll + 60_000L))
        }
        // 单调非降
        for (i in 1 until all.size) {
            assertTrue("结果应单调非降", all[i] >= all[i - 1])
        }
    }
}
