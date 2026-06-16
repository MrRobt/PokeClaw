package io.agents.pokeclaw.mock

import io.agents.pokeclaw.cloud.model.*
import java.util.UUID

/**
 * Mock 数据提供者
 * 用于测试业务逻辑，提供各种场景下的模拟数据
 */
object MockDataProvider {

    // ============ Device 相关 Mock 数据 ============

    fun createMockDeviceRegisterRequest(
        deviceId: String = "test-device-001",
        deviceName: String = "Test Device",
        deviceModel: String = "Pixel 8",
        androidVersion: String = "14",
        appVersion: String = "0.6.12"
    ): DeviceRegisterRequest {
        return DeviceRegisterRequest(
            deviceId = deviceId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            appVersion = appVersion
        )
    }

    fun createMockDeviceRegister200Response(
        deviceId: String = "test-device-001",
        deviceToken: String = "mock-token-${UUID.randomUUID()}",
        refreshToken: String = "mock-refresh-${UUID.randomUUID()}",
        expiresIn: Int = 3600
    ): DeviceRegister200Response {
        return DeviceRegister200Response(
            code = 200,
            `data` = DeviceRegisterResponse(
                deviceId = deviceId,
                deviceToken = deviceToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn
            )
        )
    }

    fun createMockTokenRefreshRequest(
        refreshToken: String = "mock-refresh-token"
    ): TokenRefreshRequest {
        return TokenRefreshRequest(refreshToken = refreshToken)
    }

    fun createMockRefreshToken200Response(
        deviceToken: String = "mock-refreshed-token-${UUID.randomUUID()}",
        expiresIn: Int = 3600
    ): RefreshDeviceToken200Response {
        return RefreshDeviceToken200Response(
            code = 200,
            `data` = TokenRefreshResponse(
                deviceToken = deviceToken,
                expiresIn = expiresIn
            )
        )
    }

    // ============ Heartbeat 相关 Mock 数据 ============

    fun createMockDeviceHeartbeatRequest(
        batteryLevel: Int = 80,
        isCharging: Boolean = true,
        networkType: String = "wifi"
    ): DeviceHeartbeatRequest {
        return DeviceHeartbeatRequest(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = networkType
        )
    }

    fun createMockDeviceHeartbeatResponse(
        pendingTaskCount: Int = 0,
        skillVersion: Int = 1,
        serverTime: Long = System.currentTimeMillis()
    ): DeviceHeartbeatResponse {
        return DeviceHeartbeatResponse(
            pendingTaskCount = pendingTaskCount,
            skillVersion = skillVersion,
            serverTime = serverTime
        )
    }

    fun createMockDeviceHeartbeat200Response(
        deviceId: String = "test-device-001",
        pendingTaskCount: Int = 0,
        skillVersion: Int = 1,
        serverTime: Long = System.currentTimeMillis()
    ): DeviceHeartbeat200Response {
        return DeviceHeartbeat200Response(
            code = 200,
            `data` = DeviceHeartbeatResponse(
                pendingTaskCount = pendingTaskCount,
                skillVersion = skillVersion,
                serverTime = serverTime
            )
        )
    }

    // ============ Task 相关 Mock 数据 ============

    fun createMockPendingTaskItem(
        taskUuid: String = UUID.randomUUID().toString(),
        command: String = "点击屏幕坐标 (100, 200)",
        mode: String = "interactive",
        createdAt: Long = System.currentTimeMillis(),
        priority: String = "normal"
    ): PendingTaskItem {
        return PendingTaskItem(
            taskUuid = taskUuid,
            command = command,
            mode = mode,
            createdAt = createdAt,
            priority = priority
        )
    }

    fun createMockTaskResultRequest(
        status: TaskResultRequest.Status = TaskResultRequest.Status.SUCCESS,
        result: String? = "任务执行成功",
        errorMessage: String? = null,
        executionTimeMs: Long = 3500,
        errorCategory: String? = null,
        errorCode: String? = null,
        errorDetail: String? = null,
        recoverable: Boolean? = null
    ): TaskResultRequest {
        return TaskResultRequest(
            status = status,
            result = result,
            errorMessage = errorMessage,
            executionTimeMs = executionTimeMs,
            errorCategory = errorCategory,
            errorCode = errorCode,
            errorDetail = errorDetail,
            recoverable = recoverable
        )
    }

    fun createMockSubmitTaskResult200Response(
        success: Boolean = true,
        message: String = if (success) "Task result submitted successfully" else "Failed to submit task result"
    ): SubmitTaskResult200Response {
        return SubmitTaskResult200Response(
            code = if (success) 200 else 500,
            `data` = SubmitTaskResult200ResponseData(message = message)
        )
    }

    fun createMockGetPendingTasks200Response(
        tasks: List<PendingTaskItem> = listOf(
            createMockPendingTaskItem(command = "任务1"),
            createMockPendingTaskItem(command = "任务2")
        )
    ): GetPendingTasks200Response {
        return GetPendingTasks200Response(
            code = 200,
            `data` = tasks
        )
    }

    fun createMockGetTaskByUuidResponse(
        taskUuid: String = UUID.randomUUID().toString(),
        command: String = "测试任务",
        status: TaskStatus = TaskStatus.PENDING,
        result: String? = null
    ): GetTaskByUuidResponse {
        return GetTaskByUuidResponse(
            code = 200,
            msg = "success",
            data = DeviceTaskVO(
                taskUuid = taskUuid,
                command = command,
                status = DeviceTaskVO.Status.valueOf(status.name),
                result = result,
                createTime = java.time.OffsetDateTime.now()
            )
        )
    }

    fun createMockCancelTaskResponse(
        cancelled: Boolean = true
    ): CancelTaskResponse {
        return CancelTaskResponse(
            code = 200,
            msg = "success",
            data = cancelled
        )
    }

    // ============ ApiResponse 包装 ============

    fun <T> createMockApiResponse(
        data: T,
        code: Int = 200,
        msg: String = "success"
    ): ApiResponse<T> {
        return ApiResponse(code = code, msg = msg, data = data)
    }

    // ============ 场景化 Mock 数据组合 ============

    /**
     * 创建一个完整的任务执行流程 Mock 数据
     */
    fun createTaskExecutionScenario(): TaskExecutionScenario {
        val taskUuid = UUID.randomUUID().toString()
        return TaskExecutionScenario(
            pendingTask = createMockPendingTaskItem(
                taskUuid = taskUuid,
                command = "给张三发送微信消息：晚上一起吃饭"
            ),
            successResult = createMockTaskResultRequest(
                status = TaskResultRequest.Status.SUCCESS,
                result = "消息已发送"
            ),
            failedResult = createMockTaskResultRequest(
                status = TaskResultRequest.Status.FAILED,
                result = null,
                errorMessage = "未找到联系人张三",
                errorCategory = "CONTACT_NOT_FOUND",
                errorCode = "E1001"
            ),
            submitResponse = createMockSubmitTaskResult200Response(success = true)
        )
    }

    /**
     * 创建一个网络错误场景 Mock 数据
     */
    fun createNetworkErrorScenario(): NetworkErrorScenario {
        val taskUuid = UUID.randomUUID().toString()
        return NetworkErrorScenario(
            task = createMockPendingTaskItem(
                taskUuid = taskUuid,
                command = "测试网络错误"
            ),
            result = createMockTaskResultRequest(
                status = TaskResultRequest.Status.FAILED,
                errorMessage = "Network timeout after 30s",
                errorCategory = "NETWORK_ERROR",
                errorCode = "E5001",
                recoverable = true
            ),
            submitResponse = createMockSubmitTaskResult200Response(success = false)
        )
    }

    /**
     * 创建一个心跳响应场景 Mock 数据
     */
    fun createHeartbeatScenario(
        hasPendingTasks: Boolean = true,
        pendingTaskCount: Int = 2
    ): HeartbeatScenario {
        return HeartbeatScenario(
            heartbeatResponse = createMockDeviceHeartbeat200Response(
                pendingTaskCount = if (hasPendingTasks) pendingTaskCount else 0
            ),
            pendingTasks = if (hasPendingTasks) {
                List(pendingTaskCount) { index ->
                    createMockPendingTaskItem(
                        command = "任务 ${index + 1}"
                    )
                }
            } else emptyList()
        )
    }

    /**
     * 创建设备注册场景
     */
    fun createDeviceRegistrationScenario(): DeviceRegistrationScenario {
        val deviceId = "device-${UUID.randomUUID().toString().substring(0, 8)}"
        return DeviceRegistrationScenario(
            registerRequest = createMockDeviceRegisterRequest(deviceId = deviceId),
            registerResponse = createMockDeviceRegister200Response(deviceId = deviceId),
            heartbeatRequest = createMockDeviceHeartbeatRequest(),
            heartbeatResponse = createMockDeviceHeartbeat200Response(deviceId = deviceId)
        )
    }

    // ============ 数据类定义 ============

    data class TaskExecutionScenario(
        val pendingTask: PendingTaskItem,
        val successResult: TaskResultRequest,
        val failedResult: TaskResultRequest,
        val submitResponse: SubmitTaskResult200Response
    )

    data class NetworkErrorScenario(
        val task: PendingTaskItem,
        val result: TaskResultRequest,
        val submitResponse: SubmitTaskResult200Response
    )

    data class HeartbeatScenario(
        val heartbeatResponse: DeviceHeartbeat200Response,
        val pendingTasks: List<PendingTaskItem>
    )

    data class DeviceRegistrationScenario(
        val registerRequest: DeviceRegisterRequest,
        val registerResponse: DeviceRegister200Response,
        val heartbeatRequest: DeviceHeartbeatRequest,
        val heartbeatResponse: DeviceHeartbeat200Response
    )
}
