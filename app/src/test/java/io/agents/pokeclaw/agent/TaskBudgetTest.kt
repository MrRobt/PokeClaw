// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// TaskBudget 单测 — check() 状态机 (OK / SOFT / HARD)、companion KV 读写、版本迁移 v2→v3。

package io.agents.pokeclaw.agent

import io.agents.pokeclaw.utils.KVUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskBudgetTest {

    @Before
    fun setUp() {
        KVUtils.resetTestBacking()
        // 确保每次测试都从干净状态开始
        TaskBudget.clearSettings()
        // 但 clearSettings 会 markUserConfigured=true，下次迁移逻辑会跳过 — 需要手动重置 version
        KVUtils.remove("KEY_TASK_BUDGET_VERSION")
        KVUtils.remove("KEY_TASK_BUDGET_USER_SET")
    }

    @After
    fun tearDown() {
        KVUtils.resetTestBacking()
    }

    // ── check() 基础语义 ──

    @Test
    fun `check - 未达限制返回 OK`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.OK, b.check(currentTokens = 100, currentCostUsd = 0.10))
    }

    @Test
    fun `check - tokens 刚好等于 maxTokens 返回 HARD_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(1000, 0.0))
    }

    @Test
    fun `check - tokens 超过 maxTokens 返回 HARD_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(2000, 0.0))
    }

    @Test
    fun `check - cost 刚好等于 maxCostUsd 返回 HARD_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(0, 1.0))
    }

    @Test
    fun `check - cost 超过 maxCostUsd 返回 HARD_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(0, 5.0))
    }

    @Test
    fun `check - HARD 优先级最高 - tokens 超限即使 cost 远低于也返回 HARD`() {
        val b = TaskBudget(maxTokens = 100, maxCostUsd = 10.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(200, 0.001))
    }

    @Test
    fun `check - 默认软阈值 80% - tokens 达到 80% 返回 SOFT_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 10.0)
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(800, 0.0))
    }

    @Test
    fun `check - 软阈值之上但未到硬阈值 - SOFT_LIMIT`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 10.0)
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(950, 0.0))
    }

    @Test
    fun `check - cost 达到软阈值返回 SOFT_LIMIT`() {
        val b = TaskBudget(maxTokens = 10_000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(0, 0.85))
    }

    @Test
    fun `check - 低于软阈值 - OK`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.OK, b.check(799, 0.79))
    }

    @Test
    fun `check - 自定义软阈值 50%`() {
        val b = TaskBudget(maxTokens = 1000, maxCostUsd = 10.0, softLimitPercent = 0.5f)
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(500, 0.0))
        assertEquals(TaskBudget.Status.OK, b.check(499, 0.0))
    }

    // ── disabled limits ──

    @Test
    fun `check - maxTokens=Int MAX_VALUE (unlimited) - 仅检查 cost`() {
        val b = TaskBudget(maxTokens = Int.MAX_VALUE, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.OK, b.check(1_000_000, 0.0))
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(1_000_000, 1.0))
    }

    @Test
    fun `check - maxTokens=0 (disabled) - 不检查 token 限制`() {
        // tokenLimitEnabled = maxTokens in 1 until Int.MAX_VALUE → false 当 maxTokens=0
        val b = TaskBudget(maxTokens = 0, maxCostUsd = 1.0)
        assertEquals(TaskBudget.Status.OK, b.check(999_999, 0.0))
    }

    @Test
    fun `check - maxCostUsd=0 (unlimited) - 仅检查 token`() {
        val b = TaskBudget(maxTokens = 100, maxCostUsd = 0.0)
        assertEquals(TaskBudget.Status.OK, b.check(0, 999.0))
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(100, 999.0))
    }

    @Test
    fun `check - 两项都 unlimited 永不超限`() {
        val b = TaskBudget(maxTokens = Int.MAX_VALUE, maxCostUsd = 0.0)
        assertEquals(TaskBudget.Status.OK, b.check(Int.MAX_VALUE - 1, Double.MAX_VALUE / 2))
    }

    // ── companion fromSettings ──

    @Test
    fun `fromSettings - 未配置时返回 unlimited 默认`() {
        val b = TaskBudget.fromSettings()
        assertEquals(Int.MAX_VALUE, b.maxTokens)
        assertEquals(0.0, b.maxCostUsd, 0.0)
    }

    @Test
    fun `fromSettings - 已保存 maxTokens 后读取`() {
        TaskBudget.saveMaxTokens(500_000)
        val b = TaskBudget.fromSettings()
        assertEquals(500_000, b.maxTokens)
    }

    @Test
    fun `fromSettings - 已保存 maxCost 后读取`() {
        TaskBudget.saveMaxCost(2.5)
        val b = TaskBudget.fromSettings()
        assertEquals(2.5, b.maxCostUsd, 0.0)
    }

    @Test
    fun `fromSettings - 两项都保存`() {
        TaskBudget.saveMaxTokens(800_000)
        TaskBudget.saveMaxCost(3.0)
        val b = TaskBudget.fromSettings()
        assertEquals(800_000, b.maxTokens)
        assertEquals(3.0, b.maxCostUsd, 0.0)
    }

    // ── companion save/clear ──

    @Test
    fun `saveMaxTokens - 写入 KV 并 markUserConfigured`() {
        assertTrue(TaskBudget.saveMaxTokens(123_456))
        assertEquals(123_456, TaskBudget.getConfiguredMaxTokens())
        // 应当 mark user set
        assertTrue(KVUtils.getBoolean("KEY_TASK_BUDGET_USER_SET", false))
    }

    @Test
    fun `saveMaxCost - 写入 KV`() {
        assertTrue(TaskBudget.saveMaxCost(0.99))
        assertEquals(0.99, TaskBudget.getConfiguredMaxCost()!!, 0.0)
    }

    @Test
    fun `clearMaxTokens - 移除 KV 配置 - getConfiguredMaxTokens 返回 null`() {
        TaskBudget.saveMaxTokens(1000)
        assertNotNull(TaskBudget.getConfiguredMaxTokens())
        TaskBudget.clearMaxTokens()
        assertNull(TaskBudget.getConfiguredMaxTokens())
    }

    @Test
    fun `clearMaxCost - 移除 KV 配置 - getConfiguredMaxCost 返回 null`() {
        TaskBudget.saveMaxCost(1.0)
        assertNotNull(TaskBudget.getConfiguredMaxCost())
        TaskBudget.clearMaxCost()
        assertNull(TaskBudget.getConfiguredMaxCost())
    }

    @Test
    fun `clearSettings - 一次性清除 token + cost`() {
        TaskBudget.saveMaxTokens(100_000)
        TaskBudget.saveMaxCost(1.0)
        TaskBudget.clearSettings()
        assertNull(TaskBudget.getConfiguredMaxTokens())
        assertNull(TaskBudget.getConfiguredMaxCost())
    }

    // ── getMaxTokens / getMaxCost (always returns value) ──

    @Test
    fun `getMaxTokens - 未配置返回 Int MAX VALUE`() {
        assertEquals(Int.MAX_VALUE, TaskBudget.getMaxTokens())
    }

    @Test
    fun `getMaxCost - 未配置返回 0`() {
        assertEquals(0.0, TaskBudget.getMaxCost(), 0.0)
    }

    @Test
    fun `getMaxTokens - 已配置返回配置值`() {
        TaskBudget.saveMaxTokens(50_000)
        assertEquals(50_000, TaskBudget.getMaxTokens())
    }

    @Test
    fun `getMaxCost - 已配置返回配置值`() {
        TaskBudget.saveMaxCost(1.5)
        assertEquals(1.5, TaskBudget.getMaxCost(), 0.0)
    }

    // ── describeCurrentBudget ──

    @Test
    fun `describeCurrentBudget - 无配置 Unlimited`() {
        assertEquals("Unlimited", TaskBudget.describeCurrentBudget())
    }

    @Test
    fun `describeCurrentBudget - 仅 token 配置`() {
        TaskBudget.saveMaxTokens(50_000)
        val desc = TaskBudget.describeCurrentBudget()
        assertTrue("应包含 token 数 50.0K，实际：$desc", desc.contains("50.0K"))
        assertTrue("应包含 'no $ cap'，实际：$desc", desc.contains("no $ cap"))
    }

    @Test
    fun `describeCurrentBudget - 仅 cost 配置`() {
        TaskBudget.saveMaxCost(1.50)
        val desc = TaskBudget.describeCurrentBudget()
        assertTrue("应以 Unlimited 开头，实际：$desc", desc.startsWith("Unlimited"))
        assertTrue("应包含 cost $1.50，实际：$desc", desc.contains("$1.50"))
    }

    @Test
    fun `describeCurrentBudget - 两项都配置`() {
        TaskBudget.saveMaxTokens(250_000)
        TaskBudget.saveMaxCost(1.00)
        val desc = TaskBudget.describeCurrentBudget()
        assertTrue("应包含 250.0K，实际：$desc", desc.contains("250.0K"))
        assertTrue("应包含 $1.00，实际：$desc", desc.contains("$1.00"))
    }

    @Test
    fun `describeCurrentBudget - 大数 token 显示为 M`() {
        TaskBudget.saveMaxTokens(2_500_000)
        val desc = TaskBudget.describeCurrentBudget()
        assertTrue("应包含 2.5M，实际：$desc", desc.contains("2.5M"))
    }

    // ── version migration v2 → v3 ──

    @Test
    fun `maybeClearAutoDefaults - v2 默认配置 250k 加 1美元 未被用户设置 自动清除迁移到 unlimited`() {
        // 模拟 v2 的状态：写入默认值 + 标记非 userSet
        KVUtils.putInt("KEY_TASK_MAX_TOKENS", 250_000)
        KVUtils.putDouble("KEY_TASK_MAX_COST_USD", 1.00)
        KVUtils.putBoolean("KEY_TASK_BUDGET_USER_SET", false)
        KVUtils.putInt("KEY_TASK_BUDGET_VERSION", 2)  // 老版本号

        TaskBudget.fromSettings()  // 触发 maybeClearAutoDefaults

        // 老默认应被清除
        assertNull("v2 默认 tokens 应被自动清除", TaskBudget.getConfiguredMaxTokens())
        assertNull("v2 默认 cost 应被自动清除", TaskBudget.getConfiguredMaxCost())
        // version 应升级
        val version = KVUtils.getInt("KEY_TASK_BUDGET_VERSION", 0)
        assertTrue("version 应升级到 >=3，实际 $version", version >= 3)
    }

    @Test
    fun `maybeClearAutoDefaults - 用户已设置 不应被清除`() {
        // 模拟用户设置：250k + 1.0 + userSet=true
        KVUtils.putInt("KEY_TASK_MAX_TOKENS", 250_000)
        KVUtils.putDouble("KEY_TASK_MAX_COST_USD", 1.00)
        KVUtils.putBoolean("KEY_TASK_BUDGET_USER_SET", true)
        KVUtils.putInt("KEY_TASK_BUDGET_VERSION", 2)

        TaskBudget.fromSettings()

        // 用户设置应被保留
        assertEquals(250_000, TaskBudget.getConfiguredMaxTokens())
        assertEquals(1.00, TaskBudget.getConfiguredMaxCost()!!, 0.0)
    }

    @Test
    fun `maybeClearAutoDefaults - 用户设置的 cost 不等于默认值 不会被错误清除`() {
        // userSet=false 但 cost 不是 1.0 → 看起来不像 v2 自动默认
        KVUtils.putInt("KEY_TASK_MAX_TOKENS", 250_000)
        KVUtils.putDouble("KEY_TASK_MAX_COST_USD", 0.50)  // 不是 v2 默认
        KVUtils.putBoolean("KEY_TASK_BUDGET_USER_SET", false)
        KVUtils.putInt("KEY_TASK_BUDGET_VERSION", 2)

        TaskBudget.fromSettings()

        // tokens 应保留（因为不满足 v2 默认判定：cost 不等于 1.0）
        assertEquals(250_000, TaskBudget.getConfiguredMaxTokens())
        assertEquals(0.50, TaskBudget.getConfiguredMaxCost()!!, 0.0)
    }

    @Test
    fun `maybeClearAutoDefaults - 已经 v3 不重复迁移`() {
        // 已经升级到 v3 → 直接返回，不动现有配置
        KVUtils.putInt("KEY_TASK_MAX_TOKENS", 100_000)
        KVUtils.putBoolean("KEY_TASK_BUDGET_USER_SET", false)
        KVUtils.putInt("KEY_TASK_BUDGET_VERSION", 3)

        TaskBudget.fromSettings()

        // 不应被清除
        assertEquals(100_000, TaskBudget.getConfiguredMaxTokens())
    }

    @Test
    fun `maybeClearAutoDefaults - version=0 (全新安装) 不应清除 (因为没有老默认存在)`() {
        KVUtils.putInt("KEY_TASK_BUDGET_VERSION", 0)
        // KV 里没有 max_tokens / max_cost

        TaskBudget.fromSettings()

        // 不应清除任何东西（KV 为空）
        assertNull(TaskBudget.getConfiguredMaxTokens())
        assertNull(TaskBudget.getConfiguredMaxCost())
    }

    // ── 业务契约保护 ──

    @Test
    fun `CURRENT_BUDGET_VERSION - 默认 unlimited 不强制 token 上限 (业务契约)`() {
        // 升级后默认应是 unlimited，不再强制 250k/1.0 默认值
        TaskBudget.fromSettings()
        val maxT = TaskBudget.getMaxTokens()
        assertEquals("v3 默认应 unlimited tokens", Int.MAX_VALUE, maxT)
    }

    @Test
    fun `saveMaxTokens 后再 clearSettings - 应回到 unlimited 默认`() {
        TaskBudget.saveMaxTokens(100_000)
        TaskBudget.clearSettings()
        assertEquals(Int.MAX_VALUE, TaskBudget.getMaxTokens())
        assertEquals(0.0, TaskBudget.getMaxCost(), 0.0)
    }

    @Test
    fun `softLimitPercent 默认 0_8 边界精确`() {
        val b = TaskBudget(maxTokens = 100, maxCostUsd = 100.0)
        // 79% → OK
        assertEquals(TaskBudget.Status.OK, b.check(79, 0.0))
        // 80% → SOFT
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(80, 0.0))
        // 99% → SOFT (还没到 hard)
        assertEquals(TaskBudget.Status.SOFT_LIMIT, b.check(99, 0.0))
        // 100% → HARD
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(100, 0.0))
    }

    // ── 边界 / 防御 ──

    @Test
    fun `check - 负数 token 视作 0% 不触发 SOFT 或 HARD`() {
        val b = TaskBudget(maxTokens = 100, maxCostUsd = 1.0)
        // 负数 tokens 不会 >= maxTokens，tokenPercent 负值不 >= 0.8 → OK
        assertEquals(TaskBudget.Status.OK, b.check(-1, 0.0))
    }

    @Test
    fun `check - maxTokens=1 - 任何 token 大于等于 1 都 HARD`() {
        val b = TaskBudget(maxTokens = 1, maxCostUsd = 0.0)
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(1, 0.0))
        assertEquals(TaskBudget.Status.HARD_LIMIT, b.check(2, 0.0))
    }

    @Test
    fun `Status 枚举 三值稳定 UI 逻辑契约`() {
        val values = TaskBudget.Status.values().toSet()
        assertEquals(
            setOf(TaskBudget.Status.OK, TaskBudget.Status.SOFT_LIMIT, TaskBudget.Status.HARD_LIMIT),
            values,
        )
    }
}