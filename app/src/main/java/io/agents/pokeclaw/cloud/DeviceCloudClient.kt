// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云设备云端客户端契约 — 5 个端点（对齐 device.openapi.yaml）。
// 与 CloudNodeOrchestrator / CloudHeartbeatManager 解耦，方便替换为 Mock / 真实 Retrofit 实现。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest

/**
 * 端云设备云端客户端接口。
 *
 * 语义：所有 suspend 方法在网络异常 / 后端 5xx 时返回 Result.failure(...)，
 * 让编排器按 Result 决定重试或入离线队列。
 *
 * 实现约束：
 * - 实现需自带 token 注入、401 自动刷新、连续失败计数
 * - submitTaskResult 离线时需入 CloudEventQueue，网络恢复时由编排器触发 flush
 *
 * 业务语义（对齐 device.openapi.yaml）：
 * 1. register — 设备注册，无需认证
 * 2. sendHeartbeat — 心跳保活，Bearer JWT
 * 3. getPendingTasks — 拉取待处理任务，Bearer JWT，空列表 = 当前无任务
 * 4. submitTaskResult — 提交任务结果，Bearer JWT
 * 5. refreshToken — 刷新设备 token，无需认证（用 refreshToken）
 */
interface DeviceCloudClient {

    /** 设备注册。无需认证。 */
    suspend fun register(request: DeviceRegisterRequest): Result<DeviceRegister200Response>

    /** 设备心跳。需 deviceToken。 */
    suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Result<DeviceHeartbeat200Response>

    /** 拉取待处理任务。需 deviceToken。空列表表示当前无任务。 */
    suspend fun getPendingTasks(deviceId: String): Result<GetPendingTasks200Response>

    /** 提交任务结果。需 deviceToken。 */
    suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Result<SubmitTaskResult200Response>

    /** 刷新设备 token。需 refreshToken。 */
    suspend fun refreshToken(request: TokenRefreshRequest): Result<RefreshDeviceToken200Response>

    /**
     * 把无法上报的 TaskResultRequest 入离线队列，等待网络恢复后由编排器触发 flush。
     * 默认实现：直接返回 false（不支持离线入队）。
     * 实现方应重写以支持 CloudEventQueue。
     */
    suspend fun enqueueOfflineResult(taskUuid: String, request: TaskResultRequest): Boolean = false

    /**
     * 刷新离线队列（网络恢复后由编排器调用）。
     * 返回成功补报的任务数。
     */
    suspend fun flushOfflineQueue(nowMillis: Long): Int = 0
}
