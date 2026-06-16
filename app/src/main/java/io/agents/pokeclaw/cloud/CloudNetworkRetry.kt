// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * 云端网络重试策略（指数退避 + 抖动）。
 *
 * 设计要点：
 *  - 默认退避序列：1s / 5s / 30s（与 CloudNodeOrchestrator 心跳失败计数器协同）
 *  - 支持抖动（jitter）避免雪崩
 *  - 仅对 retryable 错误重试；非 retryable 直接返回
 *  - 上限 5 次，防止无限循环
 *
 * 何时使用：
 *  - registerDevice / sendHeartbeat / submitTaskResult / getPendingTasks 单次失败重试
 *  - CloudNodeOrchestrator 已经做心跳级失败计数；本类做单次 RPC 级重试
 */
object CloudNetworkRetry {

    private const val TAG = "PokeClaw/CloudRetry"

    /** 默认退避序列（毫秒）。 */
    val DEFAULT_BACKOFF_MS = longArrayOf(1_000L, 5_000L, 30_000L)

    /** 单次 RPC 最多重试次数。 */
    const val DEFAULT_MAX_ATTEMPTS = 3

    /**
     * 重试结果。
     */
    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Failure(val attempts: Int, val cause: Throwable) : Result<Nothing>()
        data class Exhausted(val attempts: Int, val lastError: Throwable) : Result<Nothing>()
    }

    /**
     * 判断错误是否可重试。
     */
    fun isRetryable(throwable: Throwable): Boolean {
        return when (throwable) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.io.IOException -> true
            else -> {
                val msg = throwable.message?.lowercase() ?: ""
                msg.contains("timeout") || msg.contains("connection") || msg.contains("unreachable")
            }
        }
    }

    /**
     * 计算第 N 次重试的延迟（毫秒）。带 0%-30% 抖动。
     */
    fun nextDelayMs(attempt: Int, baseSequence: LongArray = DEFAULT_BACKOFF_MS): Long {
        val base = baseSequence.getOrElse(attempt - 1) { baseSequence.last() }
        val jitter = (base * 0.3 * Math.random()).toLong()
        return base + jitter
    }

    /**
     * 带指数退避的执行。
     *
     * @param block 待执行的操作
     * @param maxAttempts 最大尝试次数（含首次）
     * @param onRetry 每次重试前的回调（attempt 从 1 开始，delayMs 是将要等待的毫秒数）
     */
    suspend fun <T> withExponentialBackoff(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        onRetry: (attempt: Int, delayMs: Long, error: Throwable) -> Unit = { _, _, _ -> },
        block: suspend (attempt: Int) -> T,
    ): Result<T> {
        var lastError: Throwable? = null
        repeat(maxAttempts) { index ->
            val attempt = index + 1
            try {
                val result = block(attempt)
                if (attempt > 1) {
                    XLog.i(TAG, "withExponentialBackoff: 第 $attempt 次重试成功")
                }
                return Result.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (!isRetryable(e)) {
                    XLog.w(TAG, "withExponentialBackoff: 错误不可重试 attempt=$attempt, error=${e.message}")
                    return Result.Failure(attempt, e)
                }
                if (attempt >= maxAttempts) {
                    XLog.w(TAG, "withExponentialBackoff: 已达上限 $maxAttempts 次，停止重试")
                    return Result.Exhausted(attempt, e)
                }
                val delayMs = nextDelayMs(attempt)
                XLog.w(TAG, "withExponentialBackoff: attempt=$attempt 失败，${delayMs}ms 后重试 (${e.message})")
                onRetry(attempt, delayMs, e)
                delay(delayMs)
            }
        }
        return Result.Exhausted(maxAttempts, lastError ?: RuntimeException("unknown"))
    }
}
