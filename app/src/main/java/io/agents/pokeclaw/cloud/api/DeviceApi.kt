package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.model.* // paths unified to /device-api/** per contract
import retrofit2.Response
import retrofit2.http.*

/**
 * 设备端API接口定义
 * 对应 device.openapi.yaml 中 `/api/claw-device/...` 路径
 *
 * v1.1.0 升级（2026-05-21）：submitTaskResult 与 cancelTask 强制要求
 * HMAC-SHA256 签名（X-Claw-Signature / X-Claw-Timestamp / X-Claw-Nonce 头）。
 */
interface DeviceApi {

    /**
     * 设备注册
     * POST /api/claw-device/register
     * 无需认证，首次启动时调用
     */
    @POST("/device-api/claw-device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegister200Response>

    /**
     * 设备心跳
     * POST /api/claw-device/heartbeat
     * 需要 deviceToken 认证
     */
    @POST("/device-api/claw-device/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: DeviceHeartbeatRequest
    ): Response<DeviceHeartbeat200Response>

    /**
     * 获取待处理任务
     * GET /api/claw-device/devices/{deviceId}/pending-tasks
     * 需要 deviceToken 认证
     */
    @GET("/device-api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(
        @Path("deviceId") deviceId: String
    ): Response<GetPendingTasks200Response>

    /**
     * 提交任务执行结果
     * POST /api/claw-device/tasks/{taskUuid}/result
     *
     * v1.1.0：除 JWT Bearer 外强制要求 HMAC 签名。
     * 签名算法见 cloud.auth.HmacSigner.signSubmitResult。
     */
    @POST("/device-api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(
        @Path("taskUuid") taskUuid: String,
        @Header("X-Claw-Timestamp") timestampMillis: Long,
        @Header("X-Claw-Nonce") nonce: String,
        @Header("X-Claw-Signature") signature: String,
        @Body request: TaskResultRequest,
    ): Response<SubmitTaskResult200Response>

    /**
     * 刷新设备Token
     * POST /api/claw-device/token/refresh
     * 无需认证，使用 refreshToken
     */
    @POST("/device-api/claw-device/token/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<RefreshDeviceToken200Response>

    /**
     * C3-01：根据 taskUuid 查询单条任务
     * GET /api/claw-device/tasks/{taskUuid}
     * 需要 deviceToken 认证
     */
    @GET("/device-api/claw-device/tasks/{taskUuid}")
    suspend fun getTaskByUuid(
        @Path("taskUuid") taskUuid: String
    ): Response<GetTaskByUuidResponse>

    /**
     * C3-01：取消一个尚未终态的任务
     * POST /api/claw-device/tasks/{taskUuid}/cancel
     * 需要 deviceToken 认证
     *
     * v1.1.0：与 submitTaskResult 同样要求 HMAC 签名。
     * 响应 data 为 true=取消成功；false=任务不存在或已是终态。
     */
    @POST("/device-api/claw-device/tasks/{taskUuid}/cancel")
    suspend fun cancelTask(
        @Path("taskUuid") taskUuid: String,
        @Header("X-Claw-Timestamp") timestampMillis: Long,
        @Header("X-Claw-Nonce") nonce: String,
        @Header("X-Claw-Signature") signature: String,
        @Body request: TaskResultRequest,
    ): Response<CancelTaskResponse>
}
