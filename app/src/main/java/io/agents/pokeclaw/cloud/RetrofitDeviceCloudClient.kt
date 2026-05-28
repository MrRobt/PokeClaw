// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Retrofit 设备云端客户端实现

package io.agents.pokeclaw.cloud

import com.google.gson.Gson
import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.ClawSignatureGenerator
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.auth.LogSanitizer
import io.agents.pokeclaw.cloud.model.*
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 实现的设备云端客户端。
 *
 * 使用 Retrofit + OkHttp 进行网络通信，支持：
 * - Token 自动注入
 * - Token 自动刷新
 * - 离线队列补报
 * - 错误重试
 */
class RetrofitDeviceCloudClient(
    private val baseUrl: String,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: CloudEventQueue,
) : DeviceCloudClient {

    private val TAG = "PokeClaw/DeviceCloudClient"

    private val okHttpClient: OkHttpClient by lazy { createOkHttpClient() }
    private val retrofit: Retrofit by lazy { createRetrofit() }
    private val deviceApi: DeviceApi by lazy { retrofit.create(DeviceApi::class.java) }
    private val gson: Gson by lazy { Gson() }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val TAG = "PokeClaw/DeviceCloudClient"

        /**
         * 创建客户端实例（用于 ClawApplication 初始化）。
         */
        fun create(
            baseUrl: String,
            tokenStore: CloudDeviceTokenStore,
            offlineQueue: CloudEventQueue,
        ): RetrofitDeviceCloudClient {
            return RetrofitDeviceCloudClient(baseUrl, tokenStore, offlineQueue)
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        // 创建脱敏的日志拦截器，只输出 HEADERS 级别，并脱敏敏感头
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // 脱敏敏感头：Authorization, X-Claw-Signature, X-Claw-Nonce, X-Claw-Timestamp
            val sanitizedMessage = sanitizeLogMessage(message)
            XLog.d(TAG, sanitizedMessage)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()

                // 添加 Device Token（如果存在且需要认证）
                val token = tokenStore.getDeviceToken()
                if (token != null && shouldAddAuth(request.url.toString())) {
                    builder.addHeader("Authorization", "Bearer $token")
                    XLog.d(TAG, "添加认证头: Bearer ${token.take(8)}...${token.takeLast(4)}")
                }

                val newRequest = builder.build()
                val response = chain.proceed(newRequest)

                // 处理 401 未认证 (OkHttp Response 使用 .code 属性)
                if (response.code == 401) {
                    XLog.w(TAG, "收到 401 未认证响应，需要刷新Token")
                }

                response
            }
            .build()
    }

    /**
     * 脱敏日志消息中的敏感头信息
     */
    private fun sanitizeLogMessage(message: String): String {
        return LogSanitizer.sanitizeHttpHeaders(message)
    }

    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun shouldAddAuth(url: String): Boolean {
        return !url.contains("/register") && !url.contains("/token/refresh")
    }

    override suspend fun register(request: DeviceRegisterRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                XLog.i(TAG, "register: 开始注册设备，deviceId=${request.deviceId}")
                val response = deviceApi.registerDevice(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val data = body?.data
                    if (data?.deviceToken != null && data.refreshToken != null) {
                        // 原子保存两个 Token，禁止空 token 覆盖有效 token
                        tokenStore.saveTokens(
                            deviceToken = data.deviceToken,
                            refreshToken = data.refreshToken,
                            expiresInSeconds = data.expiresIn ?: 604800,
                            nowMillis = System.currentTimeMillis()
                        )
                        XLog.i(TAG, "register: 注册成功，deviceId=${request.deviceId}, token 已原子保存")
                        true
                    } else {
                        XLog.e(TAG, "register: 注册响应数据为空或缺少 token")
                        false
                    }
                } else {
                    XLog.e(TAG, "register: 注册失败，code=${response.code()}")
                    false
                }
            } catch (e: Exception) {
                XLog.e(TAG, "register: 注册异常", e)
                false
            }
        }
    }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                XLog.d(TAG, "sendHeartbeat: 发送心跳")
                val response = deviceApi.sendHeartbeat(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val data = body?.data
                    if (data != null) {
                        XLog.d(TAG, "sendHeartbeat: 心跳成功，pendingTaskCount=${data.pendingTaskCount}")
                        true
                    } else {
                        XLog.w(TAG, "sendHeartbeat: 心跳响应数据为空")
                        false
                    }
                } else {
                    XLog.w(TAG, "sendHeartbeat: 心跳失败，code=${response.code()}")
                    false
                }
            } catch (e: Exception) {
                XLog.e(TAG, "sendHeartbeat: 心跳异常", e)
                false
            }
        }
    }

    override suspend fun getPendingTasks(deviceId: String): List<PendingTaskItem> {
        return withContext(Dispatchers.IO) {
            try {
                XLog.i(TAG, "getPendingTasks: 拉取待处理任务，deviceId=$deviceId")
                val response = deviceApi.getPendingTasks(deviceId)

                if (response.isSuccessful) {
                    val body = response.body()
                    val data = body?.data
                    val tasks = data ?: emptyList()
                    XLog.i(TAG, "getPendingTasks: 拉取到 ${tasks.size} 个任务")
                    tasks
                } else {
                    XLog.w(TAG, "getPendingTasks: 拉取失败，code=${response.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                XLog.e(TAG, "getPendingTasks: 拉取异常", e)
                emptyList()
            }
        }
    }

    override suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                XLog.i(TAG, "submitTaskResult: 提交任务结果，taskUuid=$taskUuid, status=${request.status}")

                // 获取 deviceToken 用于签名
                val deviceToken = tokenStore.getDeviceToken()
                if (deviceToken == null) {
                    XLog.w(TAG, "submitTaskResult: 无 deviceToken，无法生成签名")
                    offlineQueue.enqueue(taskUuid, request)
                    return@withContext false
                }

                // 生成 HMAC 签名头
                val bodyJson = gson.toJson(request)
                val path = "/api/claw-device/tasks/$taskUuid/result"
                val signatureHeaders = ClawSignatureGenerator.generateHeaders(
                    deviceToken = deviceToken,
                    path = path,
                    bodyJson = bodyJson
                )

                XLog.d(TAG, "submitTaskResult: 签名头生成完成，timestamp=${signatureHeaders.timestamp}")

                val response = deviceApi.submitTaskResult(
                    taskUuid = taskUuid,
                    timestamp = signatureHeaders.timestamp,
                    nonce = signatureHeaders.nonce,
                    signature = signatureHeaders.signature,
                    request = request
                )

                if (response.isSuccessful) {
                    XLog.i(TAG, "submitTaskResult: 提交成功，taskUuid=$taskUuid")
                    true
                } else {
                    XLog.w(TAG, "submitTaskResult: 提交失败，code=${response.code()}")
                    // 401 签名错误不进入离线队列，需要检查签名逻辑
                    if (response.code() == 401) {
                        XLog.e(TAG, "submitTaskResult: 签名认证失败，请检查 HMAC 签名实现")
                    }
                    // 进入离线队列
                    offlineQueue.enqueue(taskUuid, request)
                    false
                }
            } catch (e: Exception) {
                XLog.e(TAG, "submitTaskResult: 提交异常，进入离线队列", e)
                // 进入离线队列
                offlineQueue.enqueue(taskUuid, request)
                false
            }
        }
    }

    override suspend fun refreshTokenIfNeeded(nowMillis: Long): Boolean {
        if (!tokenStore.shouldRefreshToken(nowMillis)) {
            return true
        }

        val refreshToken = tokenStore.getRefreshToken() ?: run {
            XLog.w(TAG, "refreshTokenIfNeeded: 没有 refreshToken")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                XLog.i(TAG, "refreshTokenIfNeeded: 刷新Token")
                val request = TokenRefreshRequest(refreshToken)
                val response = deviceApi.refreshToken(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val data = body?.data
                    if (data?.deviceToken != null) {
                        // Token 刷新只更新 deviceToken，保留原 refreshToken
                        tokenStore.updateDeviceToken(
                            data.deviceToken,
                            (data.expiresIn ?: 604800).toInt(),
                            System.currentTimeMillis()
                        )
                        XLog.i(TAG, "refreshTokenIfNeeded: Token刷新成功，deviceToken 已更新，refreshToken 保持不变")
                        true
                    } else {
                        XLog.e(TAG, "refreshTokenIfNeeded: Token刷新响应为空或缺少 deviceToken")
                        false
                    }
                } else {
                    XLog.e(TAG, "refreshTokenIfNeeded: Token刷新失败，code=${response.code()}")
                    if (response.code() == 401) {
                        // refreshToken 也过期了，清除令牌
                        tokenStore.clearTokens()
                    }
                    false
                }
            } catch (e: Exception) {
                XLog.e(TAG, "refreshTokenIfNeeded: Token刷新异常", e)
                false
            }
        }
    }

    override suspend fun flushOfflineQueue(nowMillis: Long): Int {
        val dueEvents = offlineQueue.peekDue(nowMillis)
        if (dueEvents.isEmpty()) {
            return 0
        }

        // 获取 deviceToken 用于签名
        val deviceToken = tokenStore.getDeviceToken()
        if (deviceToken == null) {
            XLog.w(TAG, "flushOfflineQueue: 无 deviceToken，无法补报")
            return 0
        }

        XLog.i(TAG, "flushOfflineQueue: 尝试补报 ${dueEvents.size} 个离线事件")

        var successCount = 0
        dueEvents.forEach { event ->
            try {
                // 为离线事件生成 HMAC 签名头
                val bodyJson = gson.toJson(event.payload)
                val path = "/api/claw-device/tasks/${event.taskUuid}/result"
                val signatureHeaders = ClawSignatureGenerator.generateHeaders(
                    deviceToken = deviceToken,
                    path = path,
                    bodyJson = bodyJson
                )

                val response = deviceApi.submitTaskResult(
                    taskUuid = event.taskUuid,
                    timestamp = signatureHeaders.timestamp,
                    nonce = signatureHeaders.nonce,
                    signature = signatureHeaders.signature,
                    request = event.payload
                )

                if (response.isSuccessful) {
                    offlineQueue.markSucceeded(event.requestId)
                    successCount++
                    XLog.i(TAG, "flushOfflineQueue: 补报成功，requestId=${event.requestId}")
                } else {
                    offlineQueue.markFailed(event.requestId, nowMillis)
                    XLog.w(TAG, "flushOfflineQueue: 补报失败，code=${response.code()}")
                }
            } catch (e: Exception) {
                offlineQueue.markFailed(event.requestId, nowMillis)
                XLog.e(TAG, "flushOfflineQueue: 补报异常", e)
            }
        }

        return successCount
    }
}
