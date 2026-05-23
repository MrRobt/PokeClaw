// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 设备云端客户端接口 — 定义设备与云端通信的抽象契约

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest

/**
 * 设备云端客户端接口。
 *
 * 职责：
 * 1. 设备注册
 * 2. 心跳发送
 * 3. 拉取待处理任务
 * 4. 提交任务结果
 * 5. Token 刷新
 * 6. 离线队列补报
 */
interface DeviceCloudClient {

    /**
     * 注册设备。
     *
     * @param request 注册请求
     * @return 是否注册成功
     */
    suspend fun register(request: DeviceRegisterRequest): Boolean

    /**
     * 发送心跳。
     *
     * @param request 心跳请求
     * @return 是否发送成功
     */
    suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Boolean

    /**
     * 拉取待处理任务。
     *
     * @param deviceId 设备编号
     * @return 待处理任务列表
     */
    suspend fun getPendingTasks(deviceId: String): List<PendingTaskItem>

    /**
     * 提交任务结果。
     *
     * @param taskUuid 任务编号
     * @param request 结果请求
     * @return 是否提交成功
     */
    suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Boolean

    /**
     * 刷新 Token（如需要）。
     *
     * @param nowMillis 当前时间戳
     * @return 是否刷新成功
     */
    suspend fun refreshTokenIfNeeded(nowMillis: Long): Boolean

    /**
     * 补报离线队列中的事件。
     *
     * @param nowMillis 当前时间戳
     * @return 成功补报数量
     */
    suspend fun flushOfflineQueue(nowMillis: Long): Int
}
