package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.model.*
import retrofit2.Response
import retrofit2.http.*

interface DeviceApi {

    @POST("api/claw-device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegister200Response>

    @POST("api/claw-device/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: DeviceHeartbeatRequest
    ): Response<DeviceHeartbeat200Response>

    @GET("api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(
        @Path("deviceId") deviceId: String
    ): Response<GetPendingTasks200Response>

    @POST("api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(
        @Path("taskUuid") taskUuid: String,
        @Body request: TaskResultRequest
    ): Response<SubmitTaskResult200Response>

    @POST("api/claw-device/token/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<RefreshDeviceToken200Response>
}
