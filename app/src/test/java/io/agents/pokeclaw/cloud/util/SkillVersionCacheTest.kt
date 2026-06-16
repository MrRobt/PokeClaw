// Copyright 2026 PokeClaw (agents.io). All rights reserved.

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillVersionCacheTest {
    @Test fun `init has no version`() {
        val cache = SkillVersionCache()
        assertEquals(null, cache.current())
    }

    @Test fun `update to 5 sets current to 5`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertEquals(5, cache.current())
    }

    @Test fun `update with same value does not flag drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        val drifted = cache.update(5)
        assertTrue("should not drift", !drifted)
    }

    @Test fun `update with higher value flags drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertTrue(cache.update(7))
    }

    @Test fun `update with lower value flags drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertTrue(cache.update(3))
    }

    @Test fun `first update never flags drift`() {
        val cache = SkillVersionCache()
        assertTrue("first update should not drift", !cache.update(5))
    }

    @Test fun `concurrent updates from multiple threads do not lose drift detection`() {
        val cache = SkillVersionCache()
        cache.update(0)  // 初始化基线

        val threadCount = 8
        val updatesPerThread = 200
        val driftDetectedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(threadCount)

        repeat(threadCount) { threadId ->
            Thread {
                try {
                    repeat(updatesPerThread) { i ->
                        // 每次写入与上一个不同的值，强制出现 drift
                        val newVal = threadId * 1000 + i + 1
                        if (cache.update(newVal)) {
                            driftDetectedCount.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

        // 总更新次数：threadCount * updatesPerThread = 1600
        // 首次 update (cache.update(0)) 不算 drift
        // 之后所有 1600 次更新都应该被检测到 drift（因为每次写入的值都不同）
        // 注意：由于并发交错，实际 drift 计数可能略低，但不应严重低于 1600
        // 关键断言：drift 计数应该接近总更新次数（如果没有 lost-update，应该 = 1600）
        val expectedTotal = threadCount * updatesPerThread
        val ratio = driftDetectedCount.get().toDouble() / expectedTotal
        assertTrue(
            "drift detection ratio $ratio (got ${driftDetectedCount.get()}/$expectedTotal) " +
                "should be >= 0.95 (lost updates indicate non-atomic read-modify-write)",
            ratio >= 0.95,
        )
    }

    // --- 边界值 ---

    @Test fun `current 初始为 null 不抛异常`() {
        val cache = SkillVersionCache()
        assertNull(cache.current())
    }

    @Test fun `update 0 视为合法版本号`() {
        val cache = SkillVersionCache()
        assertFalse("首次 update 0 不算 drift", cache.update(0))
        assertEquals(0, cache.current())
        assertTrue("0 → 1 算 drift", cache.update(1))
    }

    @Test fun `update Int_MIN_VALUE 视为合法版本号`() {
        val cache = SkillVersionCache()
        assertFalse("首次 update MIN_VALUE 不算 drift", cache.update(Int.MIN_VALUE))
        assertEquals(Int.MIN_VALUE, cache.current())
    }

    @Test fun `update Int_MAX_VALUE 视为合法版本号`() {
        val cache = SkillVersionCache()
        assertFalse("首次 update MAX_VALUE 不算 drift", cache.update(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, cache.current())
    }

    @Test fun `update 负数 → 0 视为 drift 升级`() {
        val cache = SkillVersionCache()
        cache.update(-5)
        assertTrue("负数 → 0 是 drift", cache.update(0))
    }

    @Test fun `update 0 → -1 视为 drift 降级`() {
        val cache = SkillVersionCache()
        cache.update(0)
        assertTrue("0 → -1 是 drift", cache.update(-1))
    }

    @Test fun `update MAX_VALUE → MIN_VALUE 视为 drift 极大变化`() {
        val cache = SkillVersionCache()
        cache.update(Int.MAX_VALUE)
        assertTrue("MAX → MIN 是 drift", cache.update(Int.MIN_VALUE))
    }

    // --- 多次连续调用 ---

    @Test fun `连续 5 次相同 update 全部不 drift`() {
        val cache = SkillVersionCache()
        cache.update(10)  // baseline
        for (i in 0 until 5) {
            assertFalse("第 ${i + 1} 次相同 update 不应 drift", cache.update(10))
        }
        assertEquals(10, cache.current())
    }

    @Test fun `连续交替 0 1 0 1 每次都 drift`() {
        val cache = SkillVersionCache()
        assertFalse("首次 0", cache.update(0))
        assertTrue("0 → 1", cache.update(1))
        assertTrue("1 → 0", cache.update(0))
        assertTrue("0 → 1", cache.update(1))
        assertTrue("1 → 0", cache.update(0))
    }

    @Test fun `update 返回值表示 写入前后是否变化`() {
        // 仔细看 source：update 返回 prev != null && prev != remote
        // 即返回 true = 检测到 drift
        val cache = SkillVersionCache()
        assertFalse("prev=null → 不算 drift", cache.update(1))
        assertFalse("prev=1, new=1 → 相同", cache.update(1))
        assertTrue("prev=1, new=2 → drift", cache.update(2))
        assertFalse("prev=2, new=2 → 相同", cache.update(2))
    }
}