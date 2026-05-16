// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// PokeClaw 端侧设备 API Retrofit 契约 — 对齐 dyq api-contracts/device.openapi.yaml。

package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.model.ApiResponse
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterResponse
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * PokeClaw 作为 dyq 云端执行节点时调用的设备端 API。
 *
 * 说明：
 * - 注册和刷新令牌接口不带设备令牌。
 * - 心跳、任务轮询、结果回传必须带 `Authorization: Bearer <deviceToken>`。
 * - 这里保留显式 Header 参数，便于联调时绕过拦截器直接验证令牌注入。
 */
interface CloudDeviceApi {

    /** 设备首次注册或重连时更新设备信息。 */
    @POST("/api/claw-device/register")
    suspend fun register(
        @Body request: DeviceRegisterRequest,
    ): ApiResponse<DeviceRegisterResponse>

    /** 设备定期发送心跳，更新在线状态和状态信息。 */
    @POST("/api/claw-device/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") authorization: String,
        @Body request: DeviceHeartbeatRequest,
    ): ApiResponse<DeviceHeartbeatResponse>

    /** 获取分配给该设备的待处理任务列表。 */
    @GET("/api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String,
    ): ApiResponse<List<PendingTaskItem>>

    /** 设备端执行完任务后提交执行结果。 */
    @POST("/api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(
        @Header("Authorization") authorization: String,
        @Path("taskUuid") taskUuid: String,
        @Body request: TaskResultRequest,
    ): ApiResponse<String>

    /** 使用 refreshToken 换取新的 deviceToken。 */
    @POST("/api/claw-device/token/refresh")
    suspend fun refreshDeviceToken(
        @Body request: TokenRefreshRequest,
    ): ApiResponse<TokenRefreshResponse>
}

fun String.asBearerToken(): String = if (startsWith("Bearer ")) this else "Bearer $this"
