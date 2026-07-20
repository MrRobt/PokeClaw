// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云设备云端客户端 — Retrofit 实现。
// 负责：token 注入、401 自动刷新、离线结果入队、心跳失败计数、错误日志、HMAC 签名（v1.1.0）。

package io.agents.pokeclaw.cloud

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.auth.HmacAuthException
import io.agents.pokeclaw.cloud.auth.HmacHeaders
import io.agents.pokeclaw.cloud.model.CancelTaskResponse
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse
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
 * - submitTaskResult / cancelTask 在网络异常时入 [OfflineResultQueue] 等网络恢复后补报
 * - sendHeartbeat 连续失败 3 次以上时触发 [onAuthFailed] 回调（编排器据此降级）
 * - 401001~401004 HMAC 错误码解析为 [HmacAuthException]，按 code 路由不同处理策略
 *
 * v1.1.0 升级（2026-05-21）：
 * - submitTaskResult / cancelTask 调用前自动生成 ts + nonce + HMAC-SHA256 签名
 * - 签名 basePath 与后端 Spring Web HttpServletRequest.getRequestURI() 保持一致（始终以 "/" 开头）
 */
class RetrofitDeviceCloudClient(
    private val api: DeviceApi,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: OfflineResultQueue? = null,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val onAuthFailed: (() -> Unit)? = null,
    private val gson: Gson = Gson(),
    /**
     * US-D-037 HMAC 时间偏移：来自 [CloudHeartbeatManager.getHmacTimeOffsetMillis]。
     * 当 device clock 与 server clock 偏差 > 4min 时，HMAC `X-Claw-Timestamp`
     * 应改用 `nowMillis - hmacTimeOffset` 计算（与 serverTime 基准对齐），避免 401002 TIMESTAMP_EXPIRED。
     * 默认 0L 表示不偏移（与 R7 之前行为一致）。
     */
    private val hmacTimeOffsetProvider: () -> Long = { 0L },
) : DeviceCloudClient {

    /**
     * 设置 HMAC 时间偏移 provider。WARN/OFFSET 检测到时由 [CloudHeartbeatManager] 注入。
     * 用于在 client 构造晚于 manager 初始化的场景下重新绑定。
     */
    fun setHmacTimeOffsetProvider(provider: () -> Long) {
        latestHmacTimeOffsetProvider = provider
    }

    @Volatile
    private var latestHmacTimeOffsetProvider: () -> Long = hmacTimeOffsetProvider

    /**
     * 计算 HMAC 签名时间戳：本地时间减去 [latestHmacTimeOffsetProvider] 报告的偏移。
     * 偏移 0 时退化为本地时间（与 R7 之前行为完全一致）。
     */
    private fun hmacNowMillis(): Long = nowProvider() - latestHmacTimeOffsetProvider()

    companion object {
        private const val TAG = "PokeClaw/RetrofitDeviceCloudClient"

        /** 与后端约定的 submitTaskResult 路径（含前导 "/"）。 */
        private const val SUBMIT_RESULT_PATH_FORMAT = "/device-api/claw-device/tasks/%s/result"

        /** 与后端约定的 cancelTask 路径（含前导 "/"）。 */
        private const val CANCEL_TASK_PATH_FORMAT = "/device-api/claw-device/tasks/%s/cancel"

        /**
         * 工厂方法：从 baseUrl + tokenStore + offlineQueue 构造完整客户端。
         * 对齐 ClawApplication 调用的 [RetrofitDeviceCloudClient.create] 签名。
         */
        fun create(
            baseUrl: String,
            tokenStore: CloudDeviceTokenStore,
            offlineQueue: OfflineResultQueue? = null,
            onAuthFailed: (() -> Unit)? = null,
            hmacTimeOffsetProvider: () -> Long = { 0L },
        ): RetrofitDeviceCloudClient {
            val api = CloudClientFactory.buildDeviceApi(baseUrl, tokenStore)
            return RetrofitDeviceCloudClient(
                api = api,
                tokenStore = tokenStore,
                offlineQueue = offlineQueue,
                onAuthFailed = onAuthFailed,
                hmacTimeOffsetProvider = hmacTimeOffsetProvider,
            )
        }
    }

    private val consecutiveHeartbeatFailures: Int
        get() = _consecutiveHeartbeatFailures
    private var _consecutiveHeartbeatFailures: Int = 0

    private val hmacErrorTextToCode = mapOf(
        "INVALID_SIGNATURE" to HmacAuthException.CODE_INVALID_SIGNATURE,
        "TIMESTAMP_EXPIRED" to HmacAuthException.CODE_TIMESTAMP_EXPIRED,
        "NONCE_DUPLICATE" to HmacAuthException.CODE_NONCE_DUPLICATE,
        "DEVICE_MISMATCH" to HmacAuthException.CODE_DEVICE_MISMATCH,
        "TASK_DEVICE_MISMATCH" to HmacAuthException.CODE_TASK_DEVICE_MISMATCH,
    )

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

    /**
     * 提交任务结果（v1.1.0 HMAC 必填）。
     *
     * 流程：序列化 body → 查 deviceToken → 计算 ts + nonce + 签名 → 注入 3 个 X-Claw-* 头 → POST。
     * 失败处理：
     * - 网络异常 → Result.failure + 入离线队列（enqueueOfflineResult）
     * - 401001~401004 → Result.failure(HmacAuthException)
     * - 401（非 HMAC）→ 走 runWithAuthRetry 重试
     */
    override suspend fun submitTaskResult(
        taskUuid: String,
        request: TaskResultRequest,
    ): Result<SubmitTaskResult200Response> {
        val bodyBytes = gson.toJson(request).toByteArray(Charsets.UTF_8)
        val path = SUBMIT_RESULT_PATH_FORMAT.format(taskUuid)
        val result = callWithHmac("submitTaskResult", path, bodyBytes) { ts, nonce, sig ->
            api.submitTaskResult(taskUuid, ts, nonce, sig, request)
        }
        if (result.isFailure && result.exceptionOrNull() !is HmacAuthException) {
            // 离线入队（HmacAuthException 不应入队，需要重算签名）
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

    /**
     * C3-01：单任务查询。不带 HMAC（Bearer 即可）。
     */
    override suspend fun getTaskByUuid(taskUuid: String): Result<GetTaskByUuidResponse> {
        return runWithAuthRetry("getTaskByUuid") {
            api.getTaskByUuid(taskUuid)
        }
    }

    /**
     * C3-01：取消任务（v1.1.0 HMAC 必填）。
     *
     * 取消请求也带 request body（通常是 RUNNING 状态 + 当前错误信息），以保持签名上下文。
     * data=false 时仍返回 success，但 business-level 失败由调用方处理。
     */
    override suspend fun cancelTask(
        taskUuid: String,
        request: TaskResultRequest,
    ): Result<CancelTaskResponse> {
        val bodyBytes = gson.toJson(request).toByteArray(Charsets.UTF_8)
        val path = CANCEL_TASK_PATH_FORMAT.format(taskUuid)
        val result = callWithHmac("cancelTask", path, bodyBytes) { ts, nonce, sig ->
            api.cancelTask(taskUuid, ts, nonce, sig, request)
        }
        // 仅网络异常（IOException）才入离线队列：
        // - 5xx 视为服务端错误，不入队（等下次心跳再试）
        // - 401/403 HMAC 鉴权失败由 onAuthFailed 处理
        val ex = result.exceptionOrNull()
        if (result.isFailure && ex is java.io.IOException) {
            enqueueOfflineResult(taskUuid, request)
        }
        return result
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
            val bodyBytes = gson.toJson(event.payload).toByteArray(Charsets.UTF_8)
            val path = SUBMIT_RESULT_PATH_FORMAT.format(event.taskUuid)
            val result = callWithHmac("flushOfflineQueue", path, bodyBytes) { ts, nonce, sig ->
                api.submitTaskResult(event.taskUuid, ts, nonce, sig, event.payload)
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
     * 带 HMAC 签名的调用：在 [block] 前生成 ts + nonce + 签名，再把 (ts, nonce, sig) 透传给后端。
     * 错误处理：401001~401004 → HmacAuthException；其他非 2xx / 网络异常 → 普通 failure。
     */
    private suspend fun <T> callWithHmac(
        op: String,
        path: String,
        bodyBytes: ByteArray,
        block: suspend (timestampMillis: Long, nonce: String, signature: String) -> Response<T>,
    ): Result<T> {
        val deviceToken = tokenStore.snapshot()?.deviceToken
        if (deviceToken.isNullOrBlank()) {
            XLog.e(TAG, "$op: 缺少 deviceToken，无法计算 HMAC 签名")
            return Result.failure(
                IllegalStateException("$op: 缺少 deviceToken，HMAC 签名无法计算"),
            )
        }
        val headers = HmacHeaders.build(
            deviceToken = deviceToken,
            path = path,
            bodyBytes = bodyBytes,
            nowMillis = hmacNowMillis(),
        )
        val result = runCatching {
            block(headers.timestampMillis, headers.nonce, headers.signature)
        }
        return result.fold(
            onSuccess = { response -> handleHmacResponse(op, response) },
            onFailure = { e ->
                XLog.e(TAG, "$op: 网络异常", e)
                Result.failure(e)
            },
        )
    }

    /**
     * 解析 HMAC 调用的 Response：401001~401004 → HmacAuthException；其他 2xx → success；其他 4xx/5xx → failure。
     */
    private fun <T> handleHmacResponse(op: String, response: Response<T>): Result<T> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                return Result.success(body)
            }
            XLog.w(TAG, "$op: 响应体为空 code=${response.code()}")
            return Result.failure(IllegalStateException("$op: 响应体为空"))
        }
        val code = response.code()
        val errorBody = response.errorBody()?.string().orEmpty()
        if (code == 401 || code == 403) {
            val hmac = parseHmacErrorCode(errorBody)
            if (hmac != null) {
                XLog.e(TAG, "$op: HMAC 鉴权失败 code=${hmac.errorCode} reason=${hmac.reason}")
                if (hmac.isDeviceMismatch || hmac.isTaskDeviceMismatch) {
                    // 401004 / 403001 — token 与 deviceId 错配或任务设备不匹配，需要全量重新注册
                    tokenStore.invalidate()
                    onAuthFailed?.invoke()
                }
                return Result.failure(hmac)
            }
        }
        XLog.w(TAG, "$op: 后端返回非成功 code=$code body=${errorBody.take(200)}")
        return Result.failure(IllegalStateException("$op: HTTP $code"))
    }

    /**
     * 从 401 响应体里抠出 HMAC 业务码（401001~401004 / 403001）。
     * 兼容两种形态：
     * - { "code": 401001, "msg": "INVALID_SIGNATURE" }
     * - 纯文本 "INVALID_SIGNATURE"
     */
    private fun parseHmacErrorCode(errorBody: String): HmacAuthException? {
        if (errorBody.isBlank()) return null
        return try {
            val json: JsonObject = JsonParser.parseString(errorBody).asJsonObject
            val code = json.get("code")?.asInt ?: 0
            HmacAuthException.forCode(code)
        } catch (_: Exception) {
            hmacErrorTextToCode[errorBody.trim().uppercase()]?.let { HmacAuthException.forCode(it) }
        }
    }

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
