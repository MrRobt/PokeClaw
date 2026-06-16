// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// CloudNetworkRetry 单测 — isRetryable 分类、nextDelayMs 退避序列、withExponentialBackoff 重试/不重试/耗尽语义。

package io.agents.pokeclaw.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CloudNetworkRetryTest {

    // ── isRetryable ──

    @Test
    fun `isRetryable - UnknownHostException 可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(UnknownHostException("host")))
    }

    @Test
    fun `isRetryable - SocketTimeoutException 可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(SocketTimeoutException("timeout")))
    }

    @Test
    fun `isRetryable - ConnectException 可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(ConnectException("refused")))
    }

    @Test
    fun `isRetryable - 通用 IOException 可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(IOException("io failed")))
    }

    @Test
    fun `isRetryable - 错误信息含 timeout 也可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(RuntimeException("Read timeout occurred")))
    }

    @Test
    fun `isRetryable - 错误信息含 connection 也可重试`() {
        assertTrue(CloudNetworkRetry.isRetryable(RuntimeException("connection reset by peer")))
    }

    @Test
    fun `isRetryable - IllegalArgumentException 不可重试`() {
        assertEquals(false, CloudNetworkRetry.isRetryable(IllegalArgumentException("bad input")))
    }

    @Test
    fun `isRetryable - IllegalStateException 不可重试`() {
        assertEquals(false, CloudNetworkRetry.isRetryable(IllegalStateException("conflict")))
    }

    @Test
    fun `isRetryable - 业务错误 不可重试`() {
        assertEquals(false, CloudNetworkRetry.isRetryable(RuntimeException("device already registered")))
    }

    // ── nextDelayMs ──

    @Test
    fun `nextDelayMs attempt=1 - 返回 DEFAULT_BACKOFF_MS 第一个元素 加 0-30 比例 抖动`() {
        val base = CloudNetworkRetry.DEFAULT_BACKOFF_MS[0]  // 1000
        repeat(20) {
            val actual = CloudNetworkRetry.nextDelayMs(attempt = 1)
            // 抖动 0-30%
            val maxJitter = (base * 0.3).toLong()
            assertTrue(
                "attempt=1 应在 base=${base}..${base + maxJitter}，实际 $actual",
                actual in base..(base + maxJitter),
            )
        }
    }

    @Test
    fun `nextDelayMs attempt=2 - 返回 DEFAULT_BACKOFF_MS 第二个元素 加 0-30 比例 抖动`() {
        val base = CloudNetworkRetry.DEFAULT_BACKOFF_MS[1]  // 5000
        val maxJitter = (base * 0.3).toLong()
        val actual = CloudNetworkRetry.nextDelayMs(attempt = 2)
        assertTrue("attempt=2 应在 base=${base}..${base + maxJitter}，实际 $actual", actual in base..(base + maxJitter))
    }

    @Test
    fun `nextDelayMs attempt=3 - 返回 DEFAULT_BACKOFF_MS 第三个元素 加 0-30 比例 抖动`() {
        val base = CloudNetworkRetry.DEFAULT_BACKOFF_MS[2]  // 30000
        val maxJitter = (base * 0.3).toLong()
        val actual = CloudNetworkRetry.nextDelayMs(attempt = 3)
        assertTrue("attempt=3 应在 base=${base}..${base + maxJitter}，实际 $actual", actual in base..(base + maxJitter))
    }

    @Test
    fun `nextDelayMs attempt=99 - fallback 到序列最后元素`() {
        val base = CloudNetworkRetry.DEFAULT_BACKOFF_MS.last()  // 30000
        val maxJitter = (base * 0.3).toLong()
        val actual = CloudNetworkRetry.nextDelayMs(attempt = 99)
        assertTrue("超出序列长度时 fallback 到最后元素，实际 $actual", actual in base..(base + maxJitter))
    }

    @Test
    fun `nextDelayMs - 自定义 baseSequence`() {
        val custom = longArrayOf(10L, 20L)
        val actual = CloudNetworkRetry.nextDelayMs(attempt = 1, baseSequence = custom)
        val maxJitter = (10L * 0.3).toLong()  // 3
        assertTrue("自定义序列 attempt=1 应在 10..13，实际 $actual", actual in 10L..(10L + maxJitter))
    }

    // ── withExponentialBackoff ──

    @Test
    fun `withExponentialBackoff 首次即成功 - 返回 Success 不调用 onRetry`() = runTest {
        var onRetryCalls = 0
        val r = CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 3,
            onRetry = { _, _, _ -> onRetryCalls++ },
        ) { attempt ->
            assertEquals(1, attempt)
            "ok"
        }
        assertTrue(r is CloudNetworkRetry.Result.Success)
        assertEquals("ok", (r as CloudNetworkRetry.Result.Success).value)
        assertEquals("首次成功不应触发 onRetry", 0, onRetryCalls)
    }

    @Test
    fun `withExponentialBackoff 第 2 次成功 - 触发一次 onRetry 返回 Success`() = runTest {
        val retryDelays = mutableListOf<Long>()
        val r = CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 3,
            onRetry = { _, delayMs, _ -> retryDelays.add(delayMs) },
        ) { attempt ->
            if (attempt < 2) throw SocketTimeoutException("transient")
            "ok-on-2nd"
        }
        assertTrue(r is CloudNetworkRetry.Result.Success)
        assertEquals("ok-on-2nd", (r as CloudNetworkRetry.Result.Success).value)
        assertEquals(1, retryDelays.size)
    }

    @Test
    fun `withExponentialBackoff 全部失败 - 返回 Exhausted 含 attempts=maxAttempts`() = runTest {
        var attempts = 0
        val r = CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 3,
        ) { attempt ->
            attempts = attempt
            throw SocketTimeoutException("never recover")
        }
        assertTrue("应返回 Exhausted，实际 ${r::class.simpleName}", r is CloudNetworkRetry.Result.Exhausted)
        val ex = r as CloudNetworkRetry.Result.Exhausted
        assertEquals(3, ex.attempts)
        assertEquals(3, attempts)
        assertNotNull(ex.lastError)
    }

    @Test
    fun `withExponentialBackoff 不可重试错误 - 立即返回 Failure 不重试`() = runTest {
        var attempts = 0
        val r = CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 5,
        ) { attempt ->
            attempts = attempt
            throw IllegalArgumentException("permanent bad input")
        }
        assertTrue("应返回 Failure，实际 ${r::class.simpleName}", r is CloudNetworkRetry.Result.Failure)
        val f = r as CloudNetworkRetry.Result.Failure
        assertEquals("不可重试错误第 1 次就应停止", 1, f.attempts)
        assertEquals(1, attempts)
    }

    @Test
    fun `withExponentialBackoff 错误信息含 unreachable 也可重试到耗尽`() = runTest {
        val r = CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 2,
        ) { _ ->
            throw RuntimeException("service unreachable, please retry")
        }
        assertTrue(r is CloudNetworkRetry.Result.Exhausted)
        assertEquals(2, (r as CloudNetworkRetry.Result.Exhausted).attempts)
    }

    @Test
    fun `withExponentialBackoff onRetry 接收 attempt=1, delayMs, error 信息`() = runTest {
        data class RetryCall(val attempt: Int, val delayMs: Long, val error: Throwable)
        val calls = mutableListOf<RetryCall>()
        CloudNetworkRetry.withExponentialBackoff(
            maxAttempts = 2,
            onRetry = { attempt, delayMs, error ->
                calls.add(RetryCall(attempt, delayMs, error))
            },
        ) { _ ->
            throw ConnectException("refused")
        }
        assertEquals(1, calls.size)
        assertEquals(1, calls[0].attempt)
        assertTrue("delayMs 应 > 0", calls[0].delayMs > 0)
        assertTrue("error 应被透传", calls[0].error is ConnectException)
    }

    @Test
    fun `withExponentialBackoff CancellationException - 透传不吞`() = runTest {
        try {
            CloudNetworkRetry.withExponentialBackoff(maxAttempts = 3) { _ ->
                throw CancellationException("cancelled by parent")
            }
            fail("CancellationException 应向上传播")
        } catch (e: CancellationException) {
            assertEquals("cancelled by parent", e.message)
        }
    }

    @Test
    fun `DEFAULT_BACKOFF_MS - 三段配置稳定 1s-5s-30s（业务契约）`() {
        // 回归保护：序列值不能轻易调整，否则 CloudNodeOrchestrator 心跳协同会出问题
        assertEquals(1_000L, CloudNetworkRetry.DEFAULT_BACKOFF_MS[0])
        assertEquals(5_000L, CloudNetworkRetry.DEFAULT_BACKOFF_MS[1])
        assertEquals(30_000L, CloudNetworkRetry.DEFAULT_BACKOFF_MS[2])
        assertEquals(3, CloudNetworkRetry.DEFAULT_MAX_ATTEMPTS)
    }
}
