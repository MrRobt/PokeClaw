// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.task

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.utils.XLog as XLogAlias

/**
 * 自动重试管理：根据用户设置（最大 N 次）自动重试失败任务。
 *
 * 不可恢复错误（recoverable=false）不重试。
 * 退避：1s/5s/30s 指数退避（与 CloudNetworkRetry 保持一致）。
 */
object AutoRetryManager {

    private const val TAG = "AutoRetry"
    private const val KV_MAX_RETRIES = "auto_retry_max_attempts"
    private const val DEFAULT_MAX = 3
    val RETRY_DELAYS_MS = longArrayOf(1_000L, 5_000L, 30_000L)

    fun getMaxAttempts(): Int {
        return KVUtils.getInt(KV_MAX_RETRIES, DEFAULT_MAX)
    }

    fun setMaxAttempts(value: Int) {
        KVUtils.putInt(KV_MAX_RETRIES, value.coerceIn(0, 10))
        XLog.d(TAG, "setMaxAttempts: $value")
    }

    /** Whether auto-retry is enabled. */
    fun isEnabled(): Boolean = getMaxAttempts() > 0

    /**
     * Decide whether the task should be retried.
     */
    fun shouldRetry(attempt: Int, recoverable: Boolean, maxAttempts: Int = getMaxAttempts()): Boolean {
        if (!recoverable) {
            XLog.d(TAG, "shouldRetry: false (non-recoverable)")
            return false
        }
        if (maxAttempts <= 0) {
            XLog.d(TAG, "shouldRetry: false (max=0, disabled)")
            return false
        }
        if (attempt >= maxAttempts) {
            XLog.d(TAG, "shouldRetry: false (attempt=$attempt >= max=$maxAttempts)")
            return false
        }
        return true
    }

    /** Get backoff delay for a given attempt (clamped to array). */
    fun backoffMs(attempt: Int): Long {
        val idx = (attempt - 1).coerceAtLeast(0).coerceAtMost(RETRY_DELAYS_MS.size - 1)
        return RETRY_DELAYS_MS[idx]
    }
}
