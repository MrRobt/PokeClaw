package io.agents.pokeclaw.mock

import io.agents.pokeclaw.cloud.DeviceCloudClient
import io.agents.pokeclaw.cloud.model.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock 设备云客户端
 * 用于离线测试业务逻辑，模拟各种 API 响应场景
 */
class MockDeviceCloudClient : DeviceCloudClient {

    // 可配置的行为标志
    var shouldSimulateNetworkError = false
    var shouldSimulateAuthError = false
    var networkDelayMs = 0L
    var serverTimeOffsetMs = 0L

    // 模拟服务器状态
    private val taskStore = ConcurrentHashMap<String, PendingTaskItem>()
    private val taskResults = ConcurrentHashMap<String, TaskResultRequest>()
    private var deviceToken: String? = null
    private var refreshToken: String? = null

    // 回调监听
    var onRegisterCalled: ((DeviceRegisterRequest) -> Unit)? = null
    var onHeartbeatCalled: ((DeviceHeartbeatRequest) -> Unit)? = null
    var onGetPendingTasksCalled: (() -> Unit)? = null
    var onSubmitTaskResultCalled: ((String, TaskResultRequest) -> Unit)? = null
    var onGetTaskByUuidCalled: ((String) -> Unit)? = null
    var onCancelTaskCalled: ((String, TaskResultRequest) -> Unit)? = null

    // ============ Device API ============

    override suspend fun register(request: DeviceRegisterRequest): Result<DeviceRegister200Response> {
        onRegisterCalled?.invoke(request)

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error: Unable to connect to server"))
        }

        simulateDelay()

        val response = MockDataProvider.createMockDeviceRegister200Response(
            deviceId = request.deviceId
        )
        deviceToken = response.data?.deviceToken
        refreshToken = response.data?.refreshToken
        return Result.success(response)
    }

    override suspend fun refreshToken(request: TokenRefreshRequest): Result<RefreshDeviceToken200Response> {
        if (shouldSimulateAuthError) {
            return Result.failure(Exception("Auth error: Token expired"))
        }

        simulateDelay()

        return Result.success(MockDataProvider.createMockRefreshToken200Response())
    }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Result<DeviceHeartbeat200Response> {
        onHeartbeatCalled?.invoke(request)

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error: Heartbeat failed"))
        }

        simulateDelay()

        val pendingCount = taskStore.count { !taskResults.containsKey(it.key) }

        return Result.success(
            MockDataProvider.createMockDeviceHeartbeat200Response(
                pendingTaskCount = pendingCount,
                serverTime = System.currentTimeMillis() + serverTimeOffsetMs
            )
        )
    }

    // ============ Task API ============

    override suspend fun getPendingTasks(deviceId: String): Result<GetPendingTasks200Response> {
        onGetPendingTasksCalled?.invoke()

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error"))
        }

        simulateDelay()

        // 返回尚未有结果的待处理任务
        val pendingTasks = taskStore.values
            .filter { !taskResults.containsKey(it.taskUuid) }
            .toList()

        return Result.success(MockDataProvider.createMockGetPendingTasks200Response(tasks = pendingTasks))
    }

    override suspend fun submitTaskResult(
        taskUuid: String,
        request: TaskResultRequest
    ): Result<SubmitTaskResult200Response> {
        onSubmitTaskResultCalled?.invoke(taskUuid, request)

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error"))
        }

        simulateDelay()

        taskResults[taskUuid] = request

        return Result.success(
            MockDataProvider.createMockSubmitTaskResult200Response(
                success = true,
                message = "Task result submitted successfully"
            )
        )
    }

    override suspend fun getTaskByUuid(taskUuid: String): Result<GetTaskByUuidResponse> {
        onGetTaskByUuidCalled?.invoke(taskUuid)

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error"))
        }

        simulateDelay()

        val task = taskStore[taskUuid]
        val result = taskResults[taskUuid]

        return if (task != null) {
            val status = when (result?.status) {
                TaskResultRequest.Status.SUCCESS -> TaskStatus.SUCCESS
                TaskResultRequest.Status.FAILED -> TaskStatus.FAILED
                TaskResultRequest.Status.CANCELLED -> TaskStatus.CANCELLED
                TaskResultRequest.Status.RUNNING -> TaskStatus.RUNNING
                null -> TaskStatus.PENDING
            }

            Result.success(
                MockDataProvider.createMockGetTaskByUuidResponse(
                    taskUuid = taskUuid,
                    command = task.command,
                    status = status,
                    result = result?.result
                )
            )
        } else {
            Result.success(
                GetTaskByUuidResponse(
                    code = 404,
                    msg = "Task not found",
                    data = null
                )
            )
        }
    }

    override suspend fun cancelTask(taskUuid: String, request: TaskResultRequest): Result<CancelTaskResponse> {
        onCancelTaskCalled?.invoke(taskUuid, request)

        if (shouldSimulateNetworkError) {
            return Result.failure(Exception("Network error"))
        }

        simulateDelay()

        val task = taskStore[taskUuid]
        val existingResult = taskResults[taskUuid]

        // 如果任务不存在或已是终态，则不能取消
        val canCancel = task != null &&
            (existingResult == null ||
                (existingResult.status != TaskResultRequest.Status.SUCCESS &&
                 existingResult.status != TaskResultRequest.Status.FAILED &&
                 existingResult.status != TaskResultRequest.Status.CANCELLED))

        if (canCancel) {
            taskResults[taskUuid] = request.copy(status = TaskResultRequest.Status.CANCELLED)
        }

        return Result.success(
            MockDataProvider.createMockCancelTaskResponse(cancelled = canCancel)
        )
    }

    // ============ 辅助方法 ============

    /**
     * 添加一个待处理任务到模拟服务器
     */
    fun addPendingTask(task: PendingTaskItem) {
        taskStore[task.taskUuid] = task
    }

    /**
     * 清除所有任务
     */
    fun clearTasks() {
        taskStore.clear()
        taskResults.clear()
    }

    /**
     * 获取指定任务
     */
    fun getTask(taskUuid: String): PendingTaskItem? {
        return taskStore[taskUuid]
    }

    /**
     * 获取任务结果
     */
    fun getTaskResult(taskUuid: String): TaskResultRequest? {
        return taskResults[taskUuid]
    }

    /**
     * 获取所有任务
     */
    fun getAllTasks(): List<PendingTaskItem> {
        return taskStore.values.toList()
    }

    /**
     * 获取待处理任务数量
     */
    fun getPendingTaskCount(): Int {
        return taskStore.count { !taskResults.containsKey(it.key) }
    }

    /**
     * 重置 Mock 客户端状态
     */
    fun reset() {
        shouldSimulateNetworkError = false
        shouldSimulateAuthError = false
        networkDelayMs = 0L
        serverTimeOffsetMs = 0L
        taskStore.clear()
        taskResults.clear()
        deviceToken = null
        refreshToken = null
    }

    private fun simulateDelay() {
        if (networkDelayMs > 0) {
            Thread.sleep(networkDelayMs)
        }
    }
}
