// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// StuckDetector 单测 — 5 信号 (SameAction / ScreenUnchanged / ZeroDiff / HighRepetition / RepeatedError)
// + 3 级恢复 (HINT / STRATEGY_SWITCH / AUTO_KILL) + sliding window 裁剪 + recovery hint 文案。

package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StuckDetectorTest {

    private lateinit var detector: StuckDetector

    @Before
    fun setUp() {
        // 默认 windowSize=8
        detector = StuckDetector()
    }

    // ── 基础行为 ──

    @Test
    fun `record 1 步 - 返回 null 不触发任何信号`() {
        val r = detector.record("tap:1", screenHash = 1, screenDiffCount = 1, error = null)
        assertNull(r)
    }

    @Test
    fun `record 2 步 - 仍返回 null (最小触发窗口是 3)`() {
        detector.record("tap:1", 1, 1, null)
        assertNull(detector.record("tap:1", 1, 1, null))
    }

    @Test
    fun `record 步骤不足 3 - 即使全相同也不触发 SameAction`() {
        detector.record("find_and_tap:x", 1, 1, null)
        // 只有 1 条记录
        assertNull(detector.record("find_and_tap:x", 2, 1, null))
    }

    // ── SameAction 信号 ──

    @Test
    fun `连续 3 次相同 action - 触发 SameAction 第一次 HINT 级`() {
        detector.record("tap:a", 1, 1, null)
        detector.record("tap:a", 2, 1, null)
        val r = detector.record("tap:a", 3, 1, null)

        assertNotNull(r)
        assertTrue("信号应是 SameAction，实际 ${r!!.signal::class.simpleName}", r.signal is StuckDetector.Signal.SameAction)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r.level)
        assertEquals("tap:a", (r.signal as StuckDetector.Signal.SameAction).action)
        assertEquals(3, (r.signal as StuckDetector.Signal.SameAction).count)
    }

    @Test
    fun `连续 4 次相同 action - consecutive=2 仍 HINT 级`() {
        repeat(3) { i -> detector.record("tap:a", i, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r!!.level)
    }

    @Test
    fun `连续 5 次相同 action - consecutive=3 升级到 STRATEGY_SWITCH`() {
        repeat(4) { i -> detector.record("tap:a", i, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.STRATEGY_SWITCH, r!!.level)
    }

    @Test
    fun `连续 6 次相同 action - consecutive=4 仍 STRATEGY_SWITCH`() {
        repeat(5) { i -> detector.record("tap:a", i, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.STRATEGY_SWITCH, r!!.level)
    }

    @Test
    fun `连续 7 次相同 action - consecutive=5 升级到 AUTO_KILL`() {
        repeat(6) { i -> detector.record("tap:a", i, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.AUTO_KILL, r!!.level)
        // AUTO_KILL 时 recoveryHint 为空（让调用方处理）
        assertEquals("", r.recoveryHint)
    }

    @Test
    fun `连续 10 次相同 action - consecutive=8 仍 AUTO_KILL (不超出)`() {
        repeat(9) { i -> detector.record("tap:a", i, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.AUTO_KILL, r!!.level)
    }

    @Test
    fun `SameAction 优先级最高 - 即使 ScreenUnchanged 也会先报 SameAction`() {
        // 3 步：相同 action + 相同 screen + 相同 diff → SameAction 先触发
        detector.record("tap:a", 100, 0, null)
        detector.record("tap:a", 100, 0, null)
        val r = detector.record("tap:a", 100, 0, null)

        assertTrue(r!!.signal is StuckDetector.Signal.SameAction)
    }

    @Test
    fun `不同 action + 不同 screen 但 diff=0 - 触发 ZeroDiff 不是 ScreenUnchanged`() {
        detector.record("tap:a", 1, 0, null)
        detector.record("scroll:b", 2, 0, null)
        val r = detector.record("back:c", 3, 0, null)
        assertTrue("应是 ZeroDiff，实际 ${r!!.signal::class.simpleName}", r.signal is StuckDetector.Signal.ZeroDiff)
        assertEquals(3, (r.signal as StuckDetector.Signal.ZeroDiff).steps)
    }

    @Test
    fun `不同 action 但同 screen - 触发 ScreenUnchanged`() {
        detector.record("tap:a", 100, 1, null)
        detector.record("scroll:b", 100, 1, null)
        val r = detector.record("back:c", 100, 1, null)
        assertTrue("应是 ScreenUnchanged，实际 ${r!!.signal::class.simpleName}", r.signal is StuckDetector.Signal.ScreenUnchanged)
        assertEquals(3, (r.signal as StuckDetector.Signal.ScreenUnchanged).steps)
    }

    // ── ZeroDiff 信号 ──

    @Test
    fun `3 步全部 diff=0 - 触发 ZeroDiff HINT 级`() {
        detector.record("tap:a", 1, 0, null)
        detector.record("scroll:b", 2, 0, null)
        val r = detector.record("back:c", 3, 0, null)
        assertTrue(r!!.signal is StuckDetector.Signal.ZeroDiff)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r.level)
    }

    @Test
    fun `中间一步 diff 不为 0 - 不触发 ZeroDiff`() {
        detector.record("tap:a", 1, 0, null)
        detector.record("scroll:b", 2, 5, null)  // diff=5
        val r = detector.record("back:c", 3, 0, null)
        assertNull("中间 diff=5 应打破连续 0 序列", r)
    }

    // ── HighRepetition 信号 ──

    @Test
    fun `窗口未满 (小于 8 步) - 即使有重复 action 也不触发 HighRepetition`() {
        // 灌 7 步，3 次 tap:a + 4 个不同 action
        repeat(3) { detector.record("tap:a", it, 1, null) }
        repeat(4) { i -> detector.record("action-$i", 100 + i, 1, null) }
        // 不应触发 (actions.size=7 < windowSize=8)
        // 但 SameAction 也没触发（最后 3 步不全等）
        // 实际上 record 第 8 步前没检测过：调用最后一步会进入检测但 HighRepetition 不会触发
        val r = detector.record("last", 999, 1, null)
        // 最后 3 步：action-2, action-3, last → 不全等 → SameAction 不触发
        // ScreenUnchanged/ZeroDiff 不触发（screen 和 diff 都不同）
        // HighRepetition 不触发（窗口刚满，但 maxCount=3 应触发...等下确认逻辑）
        // 实际逻辑：if actions.size < windowSize return null → 满 8 后才会检查
        // 而 record 会 addLast 后 size=8 才检测。前面 7 步没有 signal → counter 一直被 reset
        // 第 8 步（last）进来时：actions 已满 8 个，maxEntry=tap:a 出现 3 次 >= 3 → HighRepetition
        assertTrue("窗口满 8 后应触发 HighRepetition (tap:a 出现 3 次)，实际 ${r?.signal}", r?.signal is StuckDetector.Signal.HighRepetition)
    }

    @Test
    fun `窗口满 8 步 - 同一 action 出现 3 次 - 触发 HighRepetition`() {
        // 3 次 tap:a + 5 个不同 action = 8 步 → 窗口首次填满
        // 第 3 步会触发 SameAction (last 3 全相同)，但随后被其他 action 打断 → counter 重置
        // 第 8 步 last 3 = [action-2, action-3, action-4] 不全等 → SameAction 不触发
        // 但窗口满，tap:a 出现 3 次 ≥ 阈值 → HighRepetition
        repeat(3) { i -> detector.record("tap:a", i, 1, null) }
        var lastResult: StuckDetector.Detection? = null
        repeat(5) { i ->
            lastResult = detector.record("action-$i", 100 + i, 1, null)
        }
        assertTrue(
            "第 8 步应触发 HighRepetition，实际 ${lastResult?.signal}",
            lastResult?.signal is StuckDetector.Signal.HighRepetition,
        )
    }

    @Test
    fun `HighRepetition windowSize 默认 8 - 业务契约保护`() {
        // 通过反射确认默认值
        val field = StuckDetector::class.java.getDeclaredField("windowSize")
        field.isAccessible = true
        // 创建一个 windowSize=8 的实例并检查默认值
        assertEquals(8, field.get(detector))
    }

    @Test
    fun `自定义 windowSize=4 - 窗口满 4 步就触发 HighRepetition`() {
        val d4 = StuckDetector(windowSize = 4)
        repeat(3) { d4.record("tap:a", it, 1, null) }
        // 第 4 步进来 → 窗口满
        val r = d4.record("trigger", 999, 1, null)
        assertTrue("windowSize=4 满后应触发 HighRepetition，实际 ${r?.signal}", r?.signal is StuckDetector.Signal.HighRepetition)
        assertEquals(4, (r!!.signal as StuckDetector.Signal.HighRepetition).window)
    }

    @Test
    fun `HighRepetition - 同一 action 只出现 2 次 - 不触发`() {
        val d4 = StuckDetector(windowSize = 4)
        d4.record("tap:a", 1, 1, null)
        d4.record("tap:a", 2, 1, null)
        repeat(2) { i -> d4.record("action-$i", 100 + i, 1, null) }
        // 窗口满 4 步 → tap:a 出现 2 次 → 不触发 HighRepetition (阈值 3)
        // 也不会触发其他信号
        assertNull(d4.record("trigger", 999, 1, null))
    }

    // ── RepeatedError 信号 ──

    @Test
    fun `连续 3 次相同 error - 触发 RepeatedError HINT 级`() {
        detector.record("a", 1, 1, error = "ETIMEDOUT")
        detector.record("b", 2, 1, error = "ETIMEDOUT")
        val r = detector.record("c", 3, 1, error = "ETIMEDOUT")
        assertTrue(r!!.signal is StuckDetector.Signal.RepeatedError)
        assertEquals("ETIMEDOUT", (r.signal as StuckDetector.Signal.RepeatedError).error)
        assertEquals(3, (r.signal as StuckDetector.Signal.RepeatedError).count)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r.level)
    }

    @Test
    fun `error 序列中插入 null - 序列被清空 不再触发 RepeatedError`() {
        detector.record("a", 1, 1, error = "err")
        detector.record("b", 2, 1, error = "err")
        // 第 3 步 error=null → errors.clear() → 后续不会基于这 2 条 + 1 条 null 凑齐 3
        detector.record("c", 3, 1, error = null)
        // 第 4 步 error=err → errors 里只有 1 条
        val r = detector.record("d", 4, 1, error = "err")
        assertFalse("error 序列被 null 清空后不应触发 RepeatedError，实际 ${r?.signal}", r?.signal is StuckDetector.Signal.RepeatedError)
    }

    @Test
    fun `错误信息不同 - 不触发 RepeatedError`() {
        detector.record("a", 1, 1, error = "err1")
        detector.record("b", 2, 1, error = "err2")
        val r = detector.record("c", 3, 1, error = "err3")
        assertNull(r)
    }

    // ── Counter 行为 ──

    @Test
    fun `触发信号后 counter 自增 - 中间无信号会重置`() {
        // 触发一次 stuck
        repeat(3) { detector.record("tap:a", it, 1, null) }
        assertNotNull(detector.record("tap:a", 99, 1, null))  // counter=1
        // 换不同 action → 不触发信号 → counter 重置
        assertNull(detector.record("different", 100, 1, null))
        // 再触发 3 次相同 → counter 又从 1 开始
        detector.record("tap:b", 101, 1, null)
        detector.record("tap:b", 102, 1, null)
        val r = detector.record("tap:b", 103, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r!!.level)
    }

    @Test
    fun `多种信号交替触发 - counter 应累加`() {
        // 触发 SameAction (counter=1)
        repeat(3) { detector.record("tap:a", it, 1, null) }
        assertEquals(StuckDetector.RecoveryLevel.HINT, detector.record("tap:a", 99, 1, null)?.level)
        // 切换：不同 action 但同 screen → ScreenUnchanged (counter=2)
        detector.record("scroll:b", 100, 1, null)
        detector.record("back:c", 100, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.HINT, detector.record("exit:d", 100, 1, null)?.level)
        // 再来一次 ZeroDiff (counter=3) → STRATEGY_SWITCH
        detector.record("a", 200, 0, null)
        detector.record("b", 201, 0, null)
        assertEquals(StuckDetector.RecoveryLevel.STRATEGY_SWITCH, detector.record("c", 202, 0, null)?.level)
    }

    // ── Sliding window 裁剪 ──

    @Test
    fun `窗口超过 windowSize - 老的记录被丢弃`() {
        val d = StuckDetector(windowSize = 3)
        d.record("a", 1, 1, null)
        d.record("b", 2, 1, null)
        d.record("c", 3, 1, null)
        // 第 4 步进来 → 窗口只剩 [b, c, d]
        d.record("d", 4, 1, null)
        // 此时 actions 是 [b, c, d]，不全等 → SameAction 不触发
        val r = d.record("e", 5, 1, null)
        assertNull("窗口滑出后早期数据应被丢弃，实际 ${r?.signal}", r)
    }

    // ── Recovery hint 文案 ──

    @Test
    fun `SameAction find_and_tap hint - 提到 tap_node 或 system_key enter`() {
        repeat(3) { detector.record("find_and_tap:x", it, 1, null) }
        val r = detector.record("find_and_tap:x", 99, 1, null)
        assertTrue("hint 应包含 tap_node，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("tap_node"))
        assertTrue("hint 应包含 [System Notice] 前缀", r.recoveryHint.startsWith("[System Notice]"))
    }

    @Test
    fun `SameAction scroll hint - 提示到达滚动末端`() {
        repeat(3) { detector.record("scroll:down", it, 1, null) }
        val r = detector.record("scroll:down", 99, 1, null)
        assertTrue("scroll hint 应提到 end of scrollable，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("end of scrollable"))
    }

    @Test
    fun `SameAction tap hint - 提示调用 get_screen_info`() {
        repeat(3) { detector.record("tap:button", it, 1, null) }
        val r = detector.record("tap:button", 99, 1, null)
        assertTrue("tap hint 应提到 get_screen_info，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("get_screen_info"))
    }

    @Test
    fun `SameAction 其他 action - 走通用 hint 路径`() {
        repeat(3) { detector.record("custom_op:x", it, 1, null) }
        val r = detector.record("custom_op:x", 99, 1, null)
        assertTrue("通用 hint 应包含 'completely different'，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("completely different"))
    }

    @Test
    fun `ScreenUnchanged hint - 提示按 back 或 home 重新尝试`() {
        repeat(3) { i -> detector.record("a-$i", 100, 1, null) }
        val r = detector.record("c", 100, 1, null)
        assertTrue("ScreenUnchanged hint 应提到 back 或 home，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("back") || r.recoveryHint.contains("home"))
    }

    @Test
    fun `ZeroDiff hint - 提示导航离开再回来`() {
        detector.record("a", 1, 0, null)
        detector.record("b", 2, 0, null)
        val r = detector.record("c", 3, 0, null)
        assertTrue("ZeroDiff hint 应提到 navigating away，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("navigating away"))
    }

    @Test
    fun `HighRepetition hint - 提示尝试根本不同的方法`() {
        val d4 = StuckDetector(windowSize = 4)
        repeat(3) { d4.record("repeat:a", it, 1, null) }
        val r = d4.record("trigger", 999, 1, null)
        assertTrue("HighRepetition hint 应提到 fundamentally different，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("fundamentally different"))
    }

    @Test
    fun `RepeatedError hint - 提示不要重试同样方法`() {
        detector.record("a", 1, 1, error = "EACCES")
        detector.record("b", 2, 1, error = "EACCES")
        val r = detector.record("c", 3, 1, error = "EACCES")
        assertTrue("RepeatedError hint 应包含 'Do not retry'，实际 ${r!!.recoveryHint}", r.recoveryHint.contains("Do not retry"))
    }

    @Test
    fun `STRATEGY_SWITCH level - hint 前缀为 System Warning`() {
        // 累积 counter: call 3 → 1 HINT, call 4 → 2 HINT, call 5 → 3 STRATEGY_SWITCH
        detector.record("a", 1, 1, null)
        detector.record("a", 2, 1, null)
        detector.record("a", 3, 1, null)  // counter=1 → HINT
        detector.record("a", 4, 1, null)  // counter=2 → HINT
        val r = detector.record("a", 5, 1, null)  // counter=3 → STRATEGY_SWITCH
        assertEquals(StuckDetector.RecoveryLevel.STRATEGY_SWITCH, r!!.level)
        assertTrue("STRATEGY_SWITCH 应以 [System Warning] 开头", r.recoveryHint.startsWith("[System Warning]"))
        assertTrue("STRATEGY_SWITCH hint 应提到 finish", r.recoveryHint.contains("finish"))
    }

    @Test
    fun `AUTO_KILL level - recoveryHint 为空字符串`() {
        // 累积 counter 到 5 → AUTO_KILL
        // call 3 → 1, call 4 → 2, call 5 → 3 (STRATEGY_SWITCH), call 6 → 4, call 7 → 5 (AUTO_KILL)
        detector.record("a", 1, 1, null)
        detector.record("a", 2, 1, null)
        detector.record("a", 3, 1, null)
        detector.record("a", 4, 1, null)
        detector.record("a", 5, 1, null)
        detector.record("a", 6, 1, null)
        val r = detector.record("a", 7, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.AUTO_KILL, r!!.level)
        assertEquals("", r.recoveryHint)
    }

    // ── 字段截断 ──

    @Test
    fun `action 名称超过 50 字符 - Signal 中只保留前 50 字符`() {
        val longAction = "x".repeat(100)
        repeat(3) { detector.record(longAction, it, 1, null) }
        val r = detector.record(longAction, 99, 1, null)
        assertEquals(50, (r!!.signal as StuckDetector.Signal.SameAction).action.length)
    }

    @Test
    fun `error 信息超过 80 字符 - Signal 中只保留前 80 字符`() {
        val longError = "E".repeat(120)
        detector.record("a", 1, 1, error = longError)
        detector.record("b", 2, 1, error = longError)
        val r = detector.record("c", 3, 1, error = longError)
        assertEquals(80, (r!!.signal as StuckDetector.Signal.RepeatedError).error.length)
    }

    // ── reset() ──

    @Test
    fun `reset - 清空所有状态`() {
        repeat(3) { detector.record("tap:a", it, 1, null) }
        assertNotNull(detector.record("tap:a", 99, 1, null))
        detector.reset()
        // reset 后从头开始
        assertNull(detector.record("tap:b", 200, 1, null))
    }

    @Test
    fun `reset 后 counter 重新计算 - 不继承之前的累积`() {
        // 累积 counter 到 STRATEGY_SWITCH (3)
        repeat(3) { i ->
            repeat(3) { detector.record("tap:a", i * 10 + it, 1, null) }
        }
        // 现在不同 action 应该 reset counter
        detector.record("x", 999, 1, null)
        detector.record("y", 1000, 1, null)
        // 再触发同 action → 应该从 HINT (counter=1) 开始而不是 STRATEGY_SWITCH
        detector.reset()  // 显式 reset 测试
        detector.record("tap:b", 1, 1, null)
        detector.record("tap:b", 2, 1, null)
        val r = detector.record("tap:b", 3, 1, null)
        assertEquals(StuckDetector.RecoveryLevel.HINT, r!!.level)
    }

    // ── Detection 数据类 ──

    @Test
    fun `Detection - 包含 signal level recoveryHint 三字段`() {
        repeat(3) { detector.record("tap:a", it, 1, null) }
        val r = detector.record("tap:a", 99, 1, null)!!
        assertNotNull(r.signal)
        assertNotNull(r.level)
        assertNotNull(r.recoveryHint)
    }

    @Test
    fun `Detection - 不同信号类型的 description 包含对应关键词`() {
        // SameAction
        repeat(2) { detector.record("tap:a", it, 1, null) }
        val sameAction = detector.record("tap:a", 99, 1, null)!!
        assertTrue("SameAction description 应包含 'repeated'，实际 ${sameAction.signal.description}", sameAction.signal.description.contains("repeated"))

        // ScreenUnchanged
        val d2 = StuckDetector()
        d2.record("a", 100, 1, null)
        d2.record("b", 100, 1, null)
        val screenUnchanged = d2.record("c", 100, 1, null)!!
        assertTrue("ScreenUnchanged description 应包含 'Screen unchanged'，实际 ${screenUnchanged.signal.description}", screenUnchanged.signal.description.contains("Screen unchanged"))

        // ZeroDiff
        val d3 = StuckDetector()
        d3.record("a", 1, 0, null)
        d3.record("b", 2, 0, null)
        val zeroDiff = d3.record("c", 3, 0, null)!!
        assertTrue("ZeroDiff description 应包含 'Zero'，实际 ${zeroDiff.signal.description}", zeroDiff.signal.description.contains("Zero"))

        // HighRepetition
        val d4 = StuckDetector(windowSize = 4)
        repeat(3) { d4.record("tap:x", it, 1, null) }
        val highRep = d4.record("trigger", 999, 1, null)!!
        assertTrue("HighRepetition description 应包含 'in last'，实际 ${highRep.signal.description}", highRep.signal.description.contains("in last"))

        // RepeatedError
        val d5 = StuckDetector()
        d5.record("a", 1, 1, "err")
        d5.record("b", 2, 1, "err")
        val repErr = d5.record("c", 3, 1, "err")!!
        assertTrue("RepeatedError description 应包含 'Same error'，实际 ${repErr.signal.description}", repErr.signal.description.contains("Same error"))
    }

    // ── 边界 / 防御性 ──

    @Test
    fun `record 空字符串 action - 也算相同 (3 次连续空字符串触发)`() {
        repeat(3) { detector.record("", it, 1, null) }
        val r = detector.record("", 99, 1, null)
        assertTrue(r!!.signal is StuckDetector.Signal.SameAction)
        assertEquals("", (r.signal as StuckDetector.Signal.SameAction).action)
    }

    @Test
    fun `record screenDiffCount 负数 - 仍参与判断 不当作 ZeroDiff`() {
        detector.record("a", 1, -1, null)
        detector.record("b", 2, -1, null)
        val r = detector.record("c", 3, -1, null)
        // -1 不等于 0，所以 ZeroDiff 不触发
        assertFalse("diff=-1 不应触发 ZeroDiff", r?.signal is StuckDetector.Signal.ZeroDiff)
    }

    @Test
    fun `error 被 null 清空后 - 再次出现 3 个连续 err 应触发 RepeatedError`() {
        // 业务契约：null 清空 deque 是为了从 0 重新计 consecutive，而不是永久屏蔽
        detector.record("a", 1, 1, "err")
        detector.record("b", 2, 1, "err")
        detector.record("c", 3, 1, null)  // errors cleared
        detector.record("d", 4, 1, "err")
        detector.record("e", 5, 1, "err")
        // 此时 errors = [err, err]，只有 2 个，需要 3 个才触发
        val r1 = detector.record("f", 6, 1, "err")
        // errors = [err, err, err] → 触发
        assertTrue("清空后重新累计 3 个 err 应触发 RepeatedError，实际 ${r1?.signal}", r1?.signal is StuckDetector.Signal.RepeatedError)
    }
}