package io.agents.pokeclaw.cloud

import android.content.Context
import android.os.Build
import io.agents.pokeclaw.cloud.model.*
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * 设备服务
 * 封装设备注册、心跳、任务拉取和结果上报的完整流程
 * 支持离线降级：网络不可用时切换到本地模式
 */
class DeviceService private constructor(context: Context) {

    companion object {
        private const val TAG = "PokeClaw/DeviceService"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30秒心跳间隔
        private const val MAX_RETRY_ATTEMPTS = 3

        @Volatile
        private var instance: DeviceService? = null

        fun getInstance(context: Context): DeviceService {
            return instance ?: synchronized(this) {
                instance ?: DeviceService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val context = context
    private val cloudClient = CloudClient.getInstance(context)
    private val tokenManager = TokenManager.getInstance(context)
    private val deviceApi = cloudClient.deviceApi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 服务状态
    private val _serviceState = MutableStateFlow(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState

    // 待处理任务
    private val _pendingTasks = MutableStateFlow<List<PendingTaskItem>>(emptyList())
    val pendingTasks: StateFlow<List<PendingTaskItem>> = _pendingTasks

    // 心跳任务
    private var heartbeatJob: Job? = null

    // 设备信息缓存
    private var deviceInfo: DeviceInfo? = null

    /**
     * 设备信息数据类
     */
    data class DeviceInfo(
        val deviceId: String,
        val deviceName: String,
        val deviceModel: String,
        val androidVersion: String,
        val appVersion: String
    )

    /**
     * 服务状态枚举
     */
    enum class ServiceState {
        IDLE,           // 空闲
        REGISTERING,    // 注册中
        REGISTERED,     // 已注册
        HEARTBEATING,   // 心跳中
        OFFLINE,        // 离线模式
        ERROR           // 错误状态
    }

    /**
     * 初始化设备信息
     */
    fun initDeviceInfo(
        deviceId: String = generateDeviceId(),
        deviceName: String = "PokeClaw-${Build.MODEL}",
        deviceModel: String = Build.MODEL,
        androidVersion: String = Build.VERSION.RELEASE,
        appVersion: String = getAppVersion()
    ) {
        deviceInfo = DeviceInfo(deviceId, deviceName, deviceModel, androidVersion, appVersion)
        XLog.i(TAG, "设备信息初始化: $deviceInfo")
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 生成设备ID
     */
    private fun generateDeviceId(): String {
        // 优先使用已保存的ID
        tokenManager.getDeviceId()?.let { return it }

        // 生成新ID
        val newId = "pokeclaw-${UUID.randomUUID().toString().substring(0, 8)}"
        tokenManager.saveDeviceId(newId)
        return newId
    }

    /**
     * 注册设备
     */
    suspend fun registerDevice(): Result<DeviceRegisterResponse> {
        val info = deviceInfo ?: run {
            return Result.failure(IllegalStateException("设备信息未初始化"))
        }

        _serviceState.value = ServiceState.REGISTERING

        return try {
            val request = DeviceRegisterRequest(
                deviceId = info.deviceId,
                deviceName = info.deviceName,
                deviceModel = info.deviceModel,
                androidVersion = info.androidVersion,
                appVersion = info.appVersion,
                publicKey = null // 暂不启用公钥验证
            )

            XLog.i(TAG, "开始设备注册: ${info.deviceId}")
            val response = deviceApi.registerDevice(request)

            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.data
                if (data != null) {
                    // 保存Token
                    data.deviceToken?.let {
                        tokenManager.saveDeviceToken(it, data.expiresIn ?: 604800)
                    }
                    data.refreshToken?.let {
                        tokenManager.saveRefreshToken(it)
                    }

                    _serviceState.value = ServiceState.REGISTERED
                    XLog.i(TAG, "设备注册成功: ${info.deviceId}")
                    Result.success(data)
                } else {
                    _serviceState.value = ServiceState.ERROR
                    Result.failure(IllegalStateException("注册响应数据为空"))
                }
            } else {
                _serviceState.value = ServiceState.ERROR
                Result.failure(IllegalStateException("注册失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            _serviceState.value = ServiceState.OFFLINE
            XLog.e(TAG, "设备注册异常", e)
            Result.failure(e)
        }
    }

    /**
     * 发送心跳
     */
    suspend fun sendHeartbeat(
        batteryLevel: Int = 0,
        isCharging: Boolean = false,
        networkType: String = "unknown"
    ): Result<DeviceHeartbeatResponse> {
        if (!tokenManager.isRegistered()) {
            return Result.failure(IllegalStateException("设备未注册"))
        }

        return try {
            val request = DeviceHeartbeatRequest(
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                networkType = networkType
            )

            val response = deviceApi.sendHeartbeat(request)

            if (response.isSuccessful) {
                val body = response.body()
                val data = body?.data
                if (data != null) {
                    // 更新待处理任务
                    data.pendingTaskCount?.let { count ->
                        if (count > 0) {
                            XLog.i(TAG, "心跳返回: 有 $count 个待处理任务")
                            fetchPendingTasks()
                        }
                    }
                    Result.success(data)
                } else {
                    Result.failure(IllegalStateException("心跳响应数据为空"))
                }
            } else {
                // Token过期，尝试刷新
                if (response.code() == 401 && tokenManager.shouldRefreshToken()) {
                    val refreshed = cloudClient.tryRefreshToken()
                    if (refreshed) {
                        // 重试心跳
                        return sendHeartbeat(batteryLevel, isCharging, networkType)
                    }
                }
                Result.failure(IllegalStateException("心跳失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            _serviceState.value = ServiceState.OFFLINE
            XLog.e(TAG, "心跳异常", e)
            Result.failure(e)
        }
    }

    /**
     * 拉取待处理任务
     */
    suspend fun fetchPendingTasks(): Result<List<PendingTaskItem>> {
        val deviceId = tokenManager.getDeviceId()
            ?: return Result.failure(IllegalStateException("设备ID为空"))

        return try {
            val response = deviceApi.getPendingTasks(deviceId)

            if (response.isSuccessful) {
                val body = response.body()
                val tasks = body?.data ?: emptyList()
                _pendingTasks.value = tasks
                XLog.i(TAG, "拉取到 ${tasks.size} 个待处理任务")
                Result.success(tasks)
            } else {
                Result.failure(IllegalStateException("拉取任务失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            XLog.e(TAG, "拉取任务异常", e)
            Result.failure(e)
        }
    }

    /**
     * 提交任务结果
     */
    suspend fun submitTaskResult(
        taskUuid: String,
        status: TaskResultRequest.Status, // SUCCESS, FAILED, CANCELLED
        result: String? = null,
        errorMessage: String? = null,
        executionTimeMs: Long? = null,
        errorCategory: String? = null,
        errorCode: String? = null
    ): Result<Unit> {
        return try {
            val request = TaskResultRequest(
                status = status,
                result = result,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs,
                errorCategory = errorCategory,
                errorCode = errorCode
            )

            val response = deviceApi.submitTaskResult(taskUuid, request)

            if (response.isSuccessful) {
                XLog.i(TAG, "任务结果提交成功: $taskUuid")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("提交任务结果失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            XLog.e(TAG, "提交任务结果异常", e)
            Result.failure(e)
        }
    }

    /**
     * 启动心跳定时器
     */
    fun startHeartbeat(
        batteryProvider: () -> Int = { 0 },
        chargingProvider: () -> Boolean = { false },
        networkProvider: () -> String = { "unknown" }
    ) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                // 检查是否已注册
                if (!tokenManager.isRegistered()) {
                    XLog.w(TAG, "设备未注册，尝试重新注册")
                    registerDevice()
                }

                // 发送心跳
                val result = sendHeartbeat(
                    batteryLevel = batteryProvider(),
                    isCharging = chargingProvider(),
                    networkType = networkProvider()
                )

                if (result.isSuccess) {
                    _serviceState.value = ServiceState.HEARTBEATING
                }

                // 等待下一次心跳
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        _serviceState.value = ServiceState.HEARTBEATING
        XLog.i(TAG, "心跳定时器已启动")
    }

    /**
     * 停止心跳定时器
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _serviceState.value = ServiceState.IDLE
        XLog.i(TAG, "心跳定时器已停止")
    }

    /**
     * 检查是否需要重新注册
     */
    fun needsRegistration(): Boolean {
        return !tokenManager.isRegistered()
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        stopHeartbeat()
        scope.cancel()
    }
}
