// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CloudHeartbeatManager - 云端心跳管理器
 * 
 * 负责与Claw后端服务保持心跳连接，上报设备状态和接收云端指令。
 * 用于PokeClaw Android端接入dyq-server-hermes设备管理服务。
 *
 * @author Hermes Agent
 */
class CloudHeartbeatManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CloudHeartbeat"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30000L // 30秒
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 5

        @Volatile
        private var instance: CloudHeartbeatManager? = null

        fun getInstance(context: Context): CloudHeartbeatManager {
            return instance ?: synchronized(this) {
                instance ?: CloudHeartbeatManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // Configuration
    private var serverUrl: String = ""
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var deviceType: String = "ANDROID_PHONE"
    private var authToken: String = ""
    private var heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS

    // State
    private val isRunning = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private val currentStatus = AtomicReference(DeviceStatus.OFFLINE)
    private var retryAttempts = 0

    // Coroutine
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // HTTP Client
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Callbacks
    interface HeartbeatCallback {
        fun onConnected(deviceId: String)
        fun onDisconnected(reason: String)
        fun onCommandReceived(command: CloudCommand)
        fun onError(error: Throwable)
    }

    private var callback: HeartbeatCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 设备状态枚举
     */
    enum class DeviceStatus {
        ONLINE, OFFLINE, BUSY, ERROR
    }

    /**
     * 云端命令数据类
     */
    data class CloudCommand(
        val commandId: String,
        val type: CommandType,
        val payload: JSONObject,
        val timestamp: Long
    )

    /**
     * 命令类型
     */
    enum class CommandType {
        EXECUTE_TASK, QUERY_STATUS, UPDATE_CONFIG, REBOOT, UNKNOWN
    }

    /**
     * 配置管理器
     */
    data class Config(
        val serverUrl: String,
        val deviceId: String,
        val deviceName: String,
        val deviceType: String = "ANDROID_PHONE",
        val authToken: String = "",
        val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS
    )

    /**
     * 初始化配置
     */
    fun initialize(config: Config, callback: HeartbeatCallback? = null): CloudHeartbeatManager {
        this.serverUrl = config.serverUrl
        this.deviceId = config.deviceId
        this.deviceName = config.deviceName
        this.deviceType = config.deviceType
        this.authToken = config.authToken
        this.heartbeatIntervalMs = config.heartbeatIntervalMs
        this.callback = callback

        XLog.i(TAG, "初始化完成: serverUrl=$serverUrl, deviceId=$deviceId")
        return this
    }

    /**
     * 开始心跳
     */
    fun start(): Boolean {
        if (isRunning.get()) {
            XLog.w(TAG, "心跳已经在运行中")
            return false
        }

        if (serverUrl.isBlank() || deviceId.isBlank()) {
            XLog.e(TAG, "配置不完整，无法启动心跳")
            return false
        }

        isRunning.set(true)
        retryAttempts = 0

        heartbeatJob = managerScope.launch {
            // 首次注册设备
            registerDevice()

            // 启动心跳循环
            while (isActive && isRunning.get()) {
                try {
                    sendHeartbeat()
                    delay(heartbeatIntervalMs)
                } catch (e: CancellationException) {
                    XLog.d(TAG, "心跳任务被取消")
                    break
                } catch (e: Exception) {
                    XLog.e(TAG, "心跳异常: ${e.message}")
                    handleError(e)
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        XLog.i(TAG, "心跳已启动，间隔: ${heartbeatIntervalMs}ms")
        return true
    }

    /**
     * 停止心跳
     */
    fun stop() {
        XLog.i(TAG, "停止心跳")
        isRunning.set(false)
        heartbeatJob?.cancel()
        heartbeatJob = null
        isConnected.set(false)
        currentStatus.set(DeviceStatus.OFFLINE)
        
        // 通知离线
        mainHandler.post {
            callback?.onDisconnected("手动停止")
        }
    }

    /**
     * 注册设备到服务器
     */
    private suspend fun registerDevice() {
        val url = "$serverUrl/hermes/device"
        val json = JSONObject().apply {
            put("name", deviceName)
            put("type", deviceType)
            put("status", "ONLINE")
            put("ipAddress", getLocalIpAddress())
            put("osInfo", "Android ${android.os.Build.VERSION.RELEASE}")
            put("model", android.os.Build.MODEL)
            put("manufacturer", android.os.Build.MANUFACTURER)
        }

        val request = buildRequest(url, json.toString())

        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        XLog.i(TAG, "设备注册成功: $body")
                        
                        // 解析响应获取设备ID（如果是新注册）
                        body?.let {
                            try {
                                val respJson = JSONObject(it)
                                if (respJson.has("data") && respJson.getJSONObject("data").has("id")) {
                                    val newId = respJson.getJSONObject("data").getString("id")
                                    if (deviceId != newId) {
                                        deviceId = newId
                                        XLog.i(TAG, "更新设备ID: $deviceId")
                                    }
                                }
                            } catch (e: Exception) {
                                XLog.w(TAG, "解析注册响应失败: ${e.message}")
                            }
                        }

                        isConnected.set(true)
                        currentStatus.set(DeviceStatus.ONLINE)
                        retryAttempts = 0

                        mainHandler.post {
                            callback?.onConnected(deviceId)
                        }
                    } else {
                        throw IOException("注册失败: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                XLog.e(TAG, "设备注册失败: ${e.message}")
                throw e
            }
        }
    }

    /**
     * 发送心跳
     */
    private suspend fun sendHeartbeat() {
        val url = "$serverUrl/hermes/device/$deviceId/heartbeat"
        
        val heartbeatData = JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("status", currentStatus.get().name)
            put("batteryLevel", getBatteryLevel())
            put("memoryUsage", getMemoryUsage())
            put("storageUsage", getStorageUsage())
            put("networkType", getNetworkType())
            put("agentVersion", getAppVersion())
        }

        val request = buildRequest(url, heartbeatData.toString())

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    XLog.d(TAG, "心跳发送成功")
                    
                    // 处理服务器返回的命令
                    body?.let { parseServerResponse(it) }
                    
                    isConnected.set(true)
                    retryAttempts = 0
                } else if (response.code == 404) {
                    // 设备不存在，需要重新注册
                    XLog.w(TAG, "设备未找到，重新注册")
                    registerDevice()
                } else {
                    throw IOException("心跳失败: ${response.code}")
                }
            }
        }
    }

    /**
     * 解析服务器响应
     */
    private fun parseServerResponse(responseBody: String) {
        try {
            val json = JSONObject(responseBody)
            
            // 检查是否有待执行命令
            if (json.has("commands")) {
                val commands = json.getJSONArray("commands")
                for (i in 0 until commands.length()) {
                    val cmdJson = commands.getJSONObject(i)
                    val command = CloudCommand(
                        commandId = cmdJson.getString("commandId"),
                        type = CommandType.valueOf(
                            cmdJson.optString("type", "UNKNOWN")
                        ),
                        payload = cmdJson.optJSONObject("payload") ?: JSONObject(),
                        timestamp = cmdJson.optLong("timestamp", System.currentTimeMillis())
                    )

                    mainHandler.post {
                        callback?.onCommandReceived(command)
                    }
                }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "解析服务器响应失败: ${e.message}")
        }
    }

    /**
     * 上报任务执行结果
     */
    fun reportTaskResult(taskId: String, success: Boolean, result: String) {
        managerScope.launch {
            try {
                val url = "$serverUrl/hermes/device/$deviceId/task-result"
                val json = JSONObject().apply {
                    put("taskId", taskId)
                    put("success", success)
                    put("result", result)
                    put("timestamp", System.currentTimeMillis())
                }

                val request = buildRequest(url, json.toString())
                
                withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().close()
                }
                
                XLog.i(TAG, "任务结果上报成功: taskId=$taskId, success=$success")
            } catch (e: Exception) {
                XLog.e(TAG, "任务结果上报失败: ${e.message}")
            }
        }
    }

    /**
     * 更新设备状态
     */
    fun updateStatus(status: DeviceStatus) {
        currentStatus.set(status)
        XLog.d(TAG, "设备状态更新: $status")
    }

    /**
     * 获取当前状态
     */
    fun getCurrentStatus(): DeviceStatus = currentStatus.get()

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = isConnected.get() && isRunning.get()

    /**
     * 获取设备ID
     */
    fun getDeviceId(): String = deviceId

    /**
     * 处理错误
     */
    private fun handleError(error: Throwable) {
        retryAttempts++
        isConnected.set(false)
        
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            currentStatus.set(DeviceStatus.ERROR)
            XLog.e(TAG, "达到最大重试次数，停止心跳")
            stop()
        }

        mainHandler.post {
            callback?.onError(error)
        }
    }

    /**
     * 构建HTTP请求
     */
    private fun buildRequest(url: String, jsonBody: String): Request {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        
        return Request.Builder()
            .url(url)
            .post(body)
            .apply {
                if (authToken.isNotBlank()) {
                    header("Authorization", "Bearer $authToken")
                }
            }
            .header("Content-Type", "application/json")
            .header("X-Device-ID", deviceId)
            .build()
    }

    // ============ Helper Methods ============

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "unknown"
                    }
                }
            }
            "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    private fun getMemoryUsage(): Double {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            (usedMemory * 100.0 / runtime.maxMemory())
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getStorageUsage(): Double {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val usedBlocks = totalBlocks - availableBlocks
            (usedBlocks * 100.0 / totalBlocks)
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.typeName ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        stop()
        managerScope.cancel()
        instance = null
    }
}
