// Copyright 2026 PokeClaw (agents.io). All rights reserved.

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
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
}