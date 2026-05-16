// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 设备云端客户端接口与实现。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.CloudDeviceApi
import io.agents.pokeclaw.cloud.api.CloudDeviceApiFactory
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.api.asBearerToken
import io.agents.pokeclaw.utils.XLog

/**
 * 端侧设备云端客户端接口。
 *
 * 抽出接口便于编排器解耦和单元测试替换。
 */
interface DeviceCloudClient {
    /** 注册设备；成功保存令牌并返回 true，失败返回 false。 */
    suspend fun register(request: DeviceRegisterRequest): Boolean

    /** 发送心跳；成功返回 true，缺令牌或网络失败返回 false。 */
    suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Boolean

    /** 拉取待处理任务列表；失败返回空列表。 */
    suspend fun getPendingTasks(deviceId: String): List<PendingTaskItem>

    /** 提交任务结果；成功返回 true，缺令牌时缓存到离线队列。 */
    suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Boolean

    /** 刷新令牌；过期才刷新，成功返回 true。 */
    suspend fun refreshTokenIfNeeded(nowMillis: Long = System.currentTimeMillis()): Boolean

    /** 补报离线队列中到期的事件。 */
    suspend fun flushOfflineQueue(nowMillis: Long = System.currentTimeMillis())
}

/**
 * 基于 Retrofit 的云端客户端实现。
 *
 * 设计原则：
 * - 本类只做网络通信，不做任务执行或 UI 操作
 * - 所有网络异常由调用方处理，本类只记录日志
 * - 离线时结果自动进入 CloudEventQueue 缓存
 */
class RetrofitDeviceCloudClient(
    private val api: CloudDeviceApi,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: CloudEventQueue,
) : DeviceCloudClient {

    override suspend fun register(request: DeviceRegisterRequest): Boolean {
        XLog.i(TAG, "register: 开始注册设备，deviceId=${request.deviceId}")
        val response = api.register(request)
        val data = response.data
        if (!response.isSuccess() || data == null) {
            XLog.e(TAG, "register: 注册失败，code=${response.code}, msg=${response.msg}")
            return false
        }
        tokenStore.saveTokens(data.deviceToken, data.refreshToken, data.expiresIn)
        XLog.i(TAG, "register: 注册成功，deviceId=${request.deviceId}")
        return true
    }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Boolean {
        val snapshot = tokenStore.snapshot()
        if (snapshot == null || !snapshot.hasDeviceToken()) {
            XLog.w(TAG, "sendHeartbeat: 缺少有效设备令牌")
            return false
        }
        XLog.d(TAG, "sendHeartbeat: battery=${request.batteryLevel}, network=${request.networkType}")
        val response = api.heartbeat(snapshot.deviceToken.asBearerToken(), request)
        val success = response.isSuccess()
        if (!success) {
            XLog.w(TAG, "sendHeartbeat: 心跳失败，code=${response.code}, msg=${response.msg}")
        }
        return success
    }

    override suspend fun getPendingTasks(deviceId: String): List<PendingTaskItem> {
        val snapshot = tokenStore.snapshot()
        if (snapshot == null || !snapshot.hasDeviceToken()) {
            XLog.w(TAG, "getPendingTasks: 缺少有效设备令牌")
            return emptyList()
        }
        val response = api.getPendingTasks(snapshot.deviceToken.asBearerToken(), deviceId)
        if (!response.isSuccess()) {
            XLog.w(TAG, "getPendingTasks: 拉取任务失败，code=${response.code}, msg=${response.msg}")
            return emptyList()
        }
        return response.data.orEmpty().also {
            XLog.i(TAG, "getPendingTasks: 拉取到 ${it.size} 个待处理任务")
        }
    }

    override suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Boolean {
        val snapshot = tokenStore.snapshot()
        if (snapshot == null || !snapshot.hasDeviceToken()) {
            offlineQueue.enqueue(taskUuid, request)
            XLog.w(TAG, "submitTaskResult: 缺少有效设备令牌，结果已缓存，taskUuid=$taskUuid")
            return false
        }
        return try {
            val response = api.submitTaskResult(snapshot.deviceToken.asBearerToken(), taskUuid, request)
            if (response.isSuccess()) {
                XLog.i(TAG, "submitTaskResult: 结果上报成功，taskUuid=$taskUuid, status=${request.status}")
                true
            } else {
                offlineQueue.enqueue(taskUuid, request)
                XLog.w(TAG, "submitTaskResult: 云端拒绝，结果已缓存，taskUuid=$taskUuid, code=${response.code}")
                false
            }
        } catch (e: Exception) {
            offlineQueue.enqueue(taskUuid, request)
            XLog.e(TAG, "submitTaskResult: 网络异常，结果已缓存，taskUuid=$taskUuid", e)
            false
        }
    }

    override suspend fun refreshTokenIfNeeded(nowMillis: Long): Boolean {
        val snapshot = tokenStore.snapshot() ?: return false
        if (!snapshot.shouldRefresh(nowMillis)) return true
        val response = api.refreshDeviceToken(TokenRefreshRequest(snapshot.refreshToken))
        val data = response.data
        if (!response.isSuccess() || data == null) {
            XLog.e(TAG, "refreshTokenIfNeeded: 刷新令牌失败，code=${response.code}, msg=${response.msg}")
            tokenStore.clear()
            return false
        }
        tokenStore.updateDeviceToken(data.deviceToken, data.expiresIn, nowMillis)
        XLog.i(TAG, "refreshTokenIfNeeded: 设备令牌已刷新")
        return true
    }

    override suspend fun flushOfflineQueue(nowMillis: Long) {
        val snapshot = tokenStore.snapshot()
        if (snapshot == null || !snapshot.hasDeviceToken()) return
        for (event in offlineQueue.peekDue(nowMillis)) {
            try {
                val response = api.submitTaskResult(
                    snapshot.deviceToken.asBearerToken(),
                    event.taskUuid,
                    event.payload,
                )
                if (response.isSuccess()) {
                    offlineQueue.markSucceeded(event.requestId)
                } else {
                    offlineQueue.markFailed(event.requestId, nowMillis)
                }
            } catch (e: Exception) {
                XLog.w(TAG, "flushOfflineQueue: 补报异常，requestId=${event.requestId}", e)
                offlineQueue.markFailed(event.requestId, nowMillis)
            }
        }
    }

    companion object {
        private const val TAG = "PokeClaw/DeviceCloudClient"

        /** 工厂方法：用 CloudDeviceApiFactory 构建完整客户端。 */
        fun create(
            baseUrl: String,
            tokenStore: CloudDeviceTokenStore,
            offlineQueue: CloudEventQueue,
        ): DeviceCloudClient {
            val api = CloudDeviceApiFactory.create(
                baseUrl = baseUrl,
                tokenProvider = { tokenStore.snapshot()?.deviceToken },
            )
            return RetrofitDeviceCloudClient(api, tokenStore, offlineQueue)
        }
    }
}
