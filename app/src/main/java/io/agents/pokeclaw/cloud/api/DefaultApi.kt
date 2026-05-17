package io.agents.pokeclaw.cloud.api

import org.openapitools.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.google.gson.annotations.SerializedName

import io.agents.pokeclaw.cloud.model.DeviceExecuteRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceUpdateStatusRequest
import io.agents.pokeclaw.cloud.model.ExecuteTaskOnDevice200Response
import io.agents.pokeclaw.cloud.model.GetDeviceDetail200Response
import io.agents.pokeclaw.cloud.model.GetDevicePage200Response
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.model.UpdateDeviceSkillRequest
import io.agents.pokeclaw.cloud.model.UpdateDeviceStatus200Response

interface DefaultApi {
    /**
     * 设备心跳
     * 设备定期发送心跳，更新在线状态和状态信息。
     * Responses:
     *  - 200: 心跳成功
     *  - 401: 未认证
     *
     * @param deviceHeartbeatRequest 
     * @return [DeviceHeartbeat200Response]
     */
    @POST("api/claw-device/heartbeat")
    suspend fun deviceHeartbeat(@Body deviceHeartbeatRequest: DeviceHeartbeatRequest): Response<DeviceHeartbeat200Response>

    /**
     * 设备注册
     * 设备首次注册或重连时更新设备信息。返回 deviceToken 和 refreshToken。
     * Responses:
     *  - 200: 注册成功
     *
     * @param deviceRegisterRequest 
     * @return [DeviceRegister200Response]
     */
    @POST("api/claw-device/register")
    suspend fun deviceRegister(@Body deviceRegisterRequest: DeviceRegisterRequest): Response<DeviceRegister200Response>

    /**
     * 向设备下发任务
     * 管理员向指定设备下发一个执行任务。
     * Responses:
     *  - 200: 任务已创建
     *
     * @param deviceId 设备唯一标识
     * @param deviceExecuteRequest 
     * @return [ExecuteTaskOnDevice200Response]
     */
    @POST("claw/device/{deviceId}/execute")
    suspend fun executeTaskOnDevice(@Path("deviceId") deviceId: kotlin.String, @Body deviceExecuteRequest: DeviceExecuteRequest): Response<ExecuteTaskOnDevice200Response>

    /**
     * 获取设备详情
     * 根据 deviceId 获取设备详细信息。
     * Responses:
     *  - 200: 设备详情
     *
     * @param deviceId 设备唯一标识
     * @return [GetDeviceDetail200Response]
     */
    @GET("claw/device/{deviceId}")
    suspend fun getDeviceDetail(@Path("deviceId") deviceId: kotlin.String): Response<GetDeviceDetail200Response>

    /**
     * 设备分页列表
     * 按条件分页查询已注册设备。
     * Responses:
     *  - 200: 设备分页数据
     *
     * @param deviceId 设备 ID（模糊搜索） (optional)
     * @param deviceName 设备名称（模糊搜索） (optional)
     * @param status 状态筛选 (optional)
     * @param pageNo  (optional, default to 1)
     * @param pageSize  (optional, default to 10)
     * @return [GetDevicePage200Response]
     */
    @GET("claw/device/list")
    suspend fun getDevicePage(@Query("deviceId") deviceId: kotlin.String? = null, @Query("deviceName") deviceName: kotlin.String? = null, @Query("status") status: kotlin.Int? = null, @Query("pageNo") pageNo: kotlin.Int? = 1, @Query("pageSize") pageSize: kotlin.Int? = 10): Response<GetDevicePage200Response>

    /**
     * 获取设备技能列表
     * （Phase 2 实现）获取设备已安装/可用的技能列表。
     * Responses:
     *  - 200: 技能列表（当前返回空数组）
     *
     * @param deviceId 设备唯一标识
     * @return [Unit]
     */
    @GET("claw/device/{deviceId}/skills")
    suspend fun getDeviceSkills(@Path("deviceId") deviceId: kotlin.String): Response<Unit>

    /**
     * 获取设备任务历史
     * 分页查询指定设备的任务执行历史。
     * Responses:
     *  - 200: 任务分页数据
     *
     * @param deviceId 设备唯一标识
     * @param status 状态筛选 (optional)
     * @param taskUuid 任务 UUID（模糊搜索） (optional)
     * @param pageNo  (optional, default to 1)
     * @param pageSize  (optional, default to 10)
     * @return [GetDevicePage200Response]
     */
    @GET("claw/device/{deviceId}/tasks")
    suspend fun getDeviceTaskHistory(@Path("deviceId") deviceId: kotlin.String, @Query("status") status: kotlin.String? = null, @Query("taskUuid") taskUuid: kotlin.String? = null, @Query("pageNo") pageNo: kotlin.Int? = 1, @Query("pageSize") pageSize: kotlin.Int? = 10): Response<GetDevicePage200Response>

    /**
     * 获取待处理任务
     * 获取分配给该设备的待处理（PENDING）任务列表。
     * Responses:
     *  - 200: 待处理任务列表
     *  - 403: 无权访问
     *
     * @param deviceId 设备唯一标识
     * @return [GetPendingTasks200Response]
     */
    @GET("api/claw-device/devices/{deviceId}/pending-tasks")
    suspend fun getPendingTasks(@Path("deviceId") deviceId: kotlin.String): Response<GetPendingTasks200Response>

    /**
     * 刷新设备 Token
     * 使用 refreshToken 换取新的 deviceToken（无感续期）。
     * Responses:
     *  - 200: 刷新成功
     *  - 401: 刷新令牌无效或已过期
     *
     * @param tokenRefreshRequest 
     * @return [RefreshDeviceToken200Response]
     */
    @POST("api/claw-device/token/refresh")
    suspend fun refreshDeviceToken(@Body tokenRefreshRequest: TokenRefreshRequest): Response<RefreshDeviceToken200Response>

    /**
     * 提交任务执行结果
     * 设备端执行完任务后提交执行结果。
     * Responses:
     *  - 200: 提交成功
     *
     * @param taskUuid 任务 UUID
     * @param taskResultRequest 
     * @return [SubmitTaskResult200Response]
     */
    @POST("api/claw-device/tasks/{taskUuid}/result")
    suspend fun submitTaskResult(@Path("taskUuid") taskUuid: kotlin.String, @Body taskResultRequest: TaskResultRequest): Response<SubmitTaskResult200Response>

    /**
     * 更新设备技能状态
     * （Phase 2 实现）启用/禁用设备的某个技能。
     * Responses:
     *  - 200: 操作成功
     *
     * @param deviceId 
     * @param skillId 
     * @param updateDeviceSkillRequest 
     * @return [Unit]
     */
    @PUT("claw/device/{deviceId}/skills/{skillId}")
    suspend fun updateDeviceSkill(@Path("deviceId") deviceId: kotlin.String, @Path("skillId") skillId: kotlin.Int, @Body updateDeviceSkillRequest: UpdateDeviceSkillRequest): Response<Unit>

    /**
     * 更新设备状态
     * 管理员手动设置设备状态（在线/离线/禁用）。
     * Responses:
     *  - 200: 更新成功
     *
     * @param deviceId 设备唯一标识
     * @param deviceUpdateStatusRequest 
     * @return [UpdateDeviceStatus200Response]
     */
    @PUT("claw/device/{deviceId}/status")
    suspend fun updateDeviceStatus(@Path("deviceId") deviceId: kotlin.String, @Body deviceUpdateStatusRequest: DeviceUpdateStatusRequest): Response<UpdateDeviceStatus200Response>

}
