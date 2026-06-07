// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云设备云端客户端 — Retrofit 实现。
// 负责：token 注入、401 自动刷新、离线结果入队、心跳失败计数、错误日志。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.utils.XLog
import retrofit2.Response

/**
 * 真实 [DeviceCloudClient] 实现。
 *
 * 行为契约：
 * - 401 响应时自动尝试一次 token 刷新，成功后重放原请求；仍 401 则视为认证失败
 * - 网络异常 / 5xx 返回 Result.failure
 * - submitTaskResult 在网络异常时入 [CloudEventQueue] 等网络恢复后补报
 * - sendHeartbeat 连续失败 3 次以上时触发 [onAuthFailed] 回调（编排器据此降级）
 */
class RetrofitDeviceCloudClient(
    private val api: DeviceApi,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: OfflineResultQueue? = null,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val onAuthFailed: (() -> Unit)? = null,
) : DeviceCloudClient {

    companion object {
        private const val TAG = "PokeClaw/RetrofitDeviceCloudClient"

        /**
         * 工厂方法：从 baseUrl + tokenStore + offlineQueue 构造完整客户端。
         * 对齐 ClawApplication 调用的 [RetrofitDeviceCloudClient.create] 签名。
         */
        fun create(
            baseUrl: String,
            tokenStore: CloudDeviceTokenStore,
            offlineQueue: OfflineResultQueue? = null,
            onAuthFailed: (() -> Unit)? = null,
        ): RetrofitDeviceCloudClient {
            val api = CloudClientFactory.buildDeviceApi(baseUrl, tokenStore)
            return RetrofitDeviceCloudClient(
                api = api,
                tokenStore = tokenStore,
                offlineQueue = offlineQueue,
                onAuthFailed = onAuthFailed,
            )
        }
    }

    private val consecutiveHeartbeatFailures: Int
        get() = _consecutiveHeartbeatFailures
    private var _consecutiveHeartbeatFailures: Int = 0

    override suspend fun register(request: DeviceRegisterRequest): Result<DeviceRegister200Response> {
        return runCatchingResponse("register") { api.registerDevice(request) }
            .onSuccess { resp ->
                val data = resp.data
                if (data != null) {
                    tokenStore.saveTokens(
                        deviceToken = data.deviceToken,
                        refreshToken = data.refreshToken,
                        expiresInSeconds = data.expiresIn,
                        nowMillis = nowProvider(),
                    )
                    XLog.i(TAG, "register: 设备注册成功，deviceId=${data.deviceId}")
                }
            }
    }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Result<DeviceHeartbeat200Response> {
        val result = runWithAuthRetry("sendHeartbeat") {
            api.sendHeartbeat(request)
        }
        if (result.isSuccess) {
            _consecutiveHeartbeatFailures = 0
        } else {
            _consecutiveHeartbeatFailures += 1
            if (_consecutiveHeartbeatFailures >= 3) {
                XLog.e(TAG, "sendHeartbeat: 连续失败 ${_consecutiveHeartbeatFailures} 次，标记离线")
                onAuthFailed?.invoke()
            }
        }
        return result
    }

    override suspend fun getPendingTasks(deviceId: String): Result<GetPendingTasks200Response> {
        return runWithAuthRetry("getPendingTasks") {
            api.getPendingTasks(deviceId)
        }
    }

    override suspend fun submitTaskResult(
        taskUuid: String,
        request: TaskResultRequest,
    ): Result<SubmitTaskResult200Response> {
        val result = runWithAuthRetry("submitTaskResult") {
            api.submitTaskResult(taskUuid, request)
        }
        if (result.isFailure) {
            // 离线入队
            enqueueOfflineResult(taskUuid, request)
        }
        return result
    }

    override suspend fun refreshToken(request: TokenRefreshRequest): Result<RefreshDeviceToken200Response> {
        return runCatchingResponse("refreshToken") { api.refreshToken(request) }
            .onSuccess { resp ->
                val data = resp.data
                if (data != null) {
                    tokenStore.updateDeviceToken(
                        deviceToken = data.deviceToken,
                        expiresInSeconds = data.expiresIn,
                        nowMillis = nowProvider(),
                    )
                    XLog.i(TAG, "refreshToken: 令牌刷新成功")
                }
            }
    }

    override suspend fun enqueueOfflineResult(taskUuid: String, request: TaskResultRequest): Boolean {
        val queue = offlineQueue ?: return false
        return try {
            queue.enqueue(taskUuid, request, nowMillis = nowProvider())
            true
        } catch (e: Exception) {
            XLog.e(TAG, "enqueueOfflineResult: 入队失败，taskUuid=$taskUuid", e)
            false
        }
    }

    override suspend fun flushOfflineQueue(nowMillis: Long): Int {
        val queue = offlineQueue ?: return 0
        val due = queue.peekDue(nowMillis, limit = 10)
        if (due.isEmpty()) return 0
        XLog.i(TAG, "flushOfflineQueue: 待补报 ${due.size} 条")
        var success = 0
        for (event in due) {
            val result = runWithAuthRetry("flushOfflineQueue") {
                api.submitTaskResult(event.taskUuid, event.payload)
            }
            if (result.isSuccess) {
                queue.markSucceeded(event.requestId)
                success += 1
            } else {
                queue.markFailed(event.requestId, nowMillis)
                XLog.w(TAG, "flushOfflineQueue: 单条补报失败，taskUuid=${event.taskUuid}")
                // 后续任务也跳过，等下次心跳再补
                break
            }
        }
        return success
    }

    // ── 内部辅助 ──

    /**
     * 统一错误处理：网络异常 → Result.failure；非 2xx → Result.failure；body 为空 → Result.failure。
     */
    private suspend fun <T> runCatchingResponse(
        op: String,
        block: suspend () -> Response<T>,
    ): Result<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    XLog.w(TAG, "$op: 响应体为空 code=${response.code()}")
                    Result.failure(IllegalStateException("$op: 响应体为空"))
                }
            } else {
                XLog.w(TAG, "$op: 后端返回非成功 code=${response.code()}")
                Result.failure(IllegalStateException("$op: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            XLog.e(TAG, "$op: 网络异常", e)
            Result.failure(e)
        }
    }

    /**
     * 带 401 重试的请求：失败时尝试刷新一次 token，成功则重放原请求。
     */
    private suspend fun <T> runWithAuthRetry(
        op: String,
        block: suspend () -> Response<T>,
    ): Result<T> {
        val first = runCatchingResponse(op, block)
        if (first.isSuccess) return first
        // 仅在 token 已存在时尝试刷新
        if (tokenStore.snapshot() == null) return first
        val refreshed = refreshToken(
            TokenRefreshRequest(refreshToken = tokenStore.snapshot()?.refreshToken ?: return first)
        )
        return if (refreshed.isSuccess) {
            XLog.i(TAG, "$op: 401 刷新后重放请求")
            runCatchingResponse(op, block)
        } else {
            XLog.w(TAG, "$op: 401 后刷新失败，不再重试")
            first
        }
    }
}
