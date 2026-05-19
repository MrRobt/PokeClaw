package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * 设备端API接口定义
 * 对应 device.openapi.yaml 中 /api/claw-device/* 路径
 */
interface DeviceApi {

    /**
     * 设备注册
     * POST /api/claw-device/register
     * 无需认证，首次启动时调用
     */
    @POST("api/claw-device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegister200Response>

    /**
     * 设备心跳
     * POST /api/claw-device/heartbeat
     * 需要 deviceToken 认证
     */
    @POST("api/claw-device/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: DeviceHeartbeatRequest
    ): Response<DeviceHeartbeat200Response>

    /**
     * 获取待处理任务
     * GET /api/claw-device/devices/{deviceId}/pending-tasks
     * 需要 deviceToken 认证
     */
    @GET("api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(
        @Path("deviceId") deviceId: String
    ): Response<GetPendingTasks200Response>

    /**
     * 提交任务执行结果
     * POST /api/claw-device/tasks/{taskUuid}/result
     * 需要 deviceToken 认证
     */
    @POST("api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(
        @Path("taskUuid") taskUuid: String,
        @Body request: TaskResultRequest
    ): Response<SubmitTaskResult200Response>

    /**
     * 刷新设备Token
     * POST /api/claw-device/token/refresh
     * 无需认证，使用 refreshToken
     */
    @POST("api/claw-device/token/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<RefreshDeviceToken200Response>
}
