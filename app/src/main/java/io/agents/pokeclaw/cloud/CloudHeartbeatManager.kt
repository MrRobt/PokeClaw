package io.agents.pokeclaw.cloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云端心跳管理器
 * 负责与Claw后端保持心跳连接，上报设备状态
 * @author Hermes Agent
 */
class CloudHeartbeatManager(
    private val context: Context,
    private val config: CloudConfig
) {
    companion object {
        private const val TAG = "CloudHeartbeat"
        private const val DEFAULT_INTERVAL_MS = 30000L // 30秒
        private const val MIN_INTERVAL_MS = 10000L // 最小10秒
        private const val MAX_INTERVAL_MS = 300000L // 最大5分钟
    }

    data class CloudConfig(
        val baseUrl: String,
        val deviceId: String,
        val apiKey: String? = null,
        val heartbeatIntervalMs: Long = DEFAULT_INTERVAL_MS
    )

    data class HeartbeatResponse(
        val success: Boolean,
        val serverTime: Long? = null,
        val message: String? = null,
        val tasksAvailable: Boolean = false
    )

    interface HeartbeatListener {
        fun onHeartbeatSuccess(response: HeartbeatResponse)
        fun onHeartbeatFailure(error: String)
        fun onConnectionStateChanged(connected: Boolean)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var heartbeatJob: Job? = null
    private var isRunning = false
    private var isConnected = false
    private var currentInterval = config.heartbeatIntervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    private var consecutiveFailures = 0
    private var consecutiveSuccesses = 0
    
    private val listeners = mutableListOf<HeartbeatListener>()

    /**
     * 启动心跳服务
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        Log.i(TAG, "Starting heartbeat service, interval: ${currentInterval}ms")
        
        heartbeatJob = scope.launch {
            while (isActive && isRunning) {
                performHeartbeat()
                delay(currentInterval)
            }
        }
    }

    /**
     * 停止心跳服务
     */
    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "Heartbeat service stopped")
    }

    /**
     * 添加监听器
     */
    fun addListener(listener: HeartbeatListener) {
        listeners.add(listener)
    }

    /**
     * 移除监听器
     */
    fun removeListener(listener: HeartbeatListener) {
        listeners.remove(listener)
    }

    /**
     * 执行单次心跳
     */
    private suspend fun performHeartbeat() {
        try {
            val requestBody = buildHeartbeatPayload()
            val request = Request.Builder()
                .url("${config.baseUrl}/hermes/device/heartbeat")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .apply {
                    config.apiKey?.let { addHeader("X-API-Key", it) }
                }
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            handleHeartbeatResponse(response)
        } catch (e: Exception) {
            handleHeartbeatError(e.message ?: "Unknown error")
        }
    }

    /**
     * 构建心跳请求体
     */
    private fun buildHeartbeatPayload(): String {
        val metrics = collectDeviceMetrics()
        return JSONObject().apply {
            put("deviceId", config.deviceId)
            put("timestamp", System.currentTimeMillis())
            put("batteryLevel", metrics.batteryLevel)
            put("networkType", metrics.networkType)
            put("appVersion", metrics.appVersion)
            put("androidVersion", metrics.androidVersion)
            put("deviceModel", metrics.deviceModel)
            put("metrics", JSONObject().apply {
                put("memoryUsage", metrics.memoryUsage)
                put("storageUsage", metrics.storageUsage)
                put("cpuUsage", metrics.cpuUsage)
                put("tasksCompleted", metrics.tasksCompleted)
                put("tasksFailed", metrics.tasksFailed)
            })
        }.toString()
    }

    /**
     * 收集设备指标
     */
    private fun collectDeviceMetrics(): DeviceMetrics {
        return DeviceMetrics(
            batteryLevel = getBatteryLevel(),
            networkType = getNetworkType(),
            appVersion = getAppVersion(),
            androidVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            memoryUsage = getMemoryUsage(),
            storageUsage = getStorageUsage(),
            cpuUsage = getCpuUsage(),
            tasksCompleted = 0, // 由任务执行器更新
            tasksFailed = 0
        )
    }

    /**
     * 处理心跳响应
     */
    private fun handleHeartbeatResponse(response: Response) {
        val body = response.body?.string()
        val json = body?.let { JSONObject(it) }
        
        val success = response.isSuccessful && json?.optInt("code", -1) == 0
        
        if (success) {
            consecutiveSuccesses++
            consecutiveFailures = 0
            
            // 动态调整心跳间隔
            if (consecutiveSuccesses > 5 && currentInterval < MAX_INTERVAL_MS) {
                currentInterval = (currentInterval * 1.2).toLong().coerceAtMost(MAX_INTERVAL_MS)
                Log.d(TAG, "Increased heartbeat interval to ${currentInterval}ms")
            }
            
            val heartbeatResponse = HeartbeatResponse(
                success = true,
                serverTime = json?.optJSONObject("data")?.optLong("serverTime"),
                message = json?.optString("msg"),
                tasksAvailable = json?.optJSONObject("data")?.optBoolean("tasksAvailable", false) ?: false
            )
            
            mainHandler.post {
                listeners.forEach { it.onHeartbeatSuccess(heartbeatResponse) }
            }
            
            if (!isConnected) {
                isConnected = true
                mainHandler.post {
                    listeners.forEach { it.onConnectionStateChanged(true) }
                }
            }
        } else {
            handleHeartbeatError("HTTP ${response.code}: ${json?.optString("msg") ?: "Unknown error"}")
        }
    }

    /**
     * 处理心跳错误
     */
    private fun handleHeartbeatError(error: String) {
        consecutiveFailures++
        consecutiveSuccesses = 0
        
        // 动态调整心跳间隔 - 失败时缩短间隔
        if (consecutiveFailures > 2 && currentInterval > MIN_INTERVAL_MS) {
            currentInterval = (currentInterval * 0.8).toLong().coerceAtLeast(MIN_INTERVAL_MS)
            Log.d(TAG, "Decreased heartbeat interval to ${currentInterval}ms due to failures")
        }
        
        Log.w(TAG, "Heartbeat failed: $error")
        
        mainHandler.post {
            listeners.forEach { it.onHeartbeatFailure(error) }
        }
        
        if (isConnected && consecutiveFailures > 3) {
            isConnected = false
            mainHandler.post {
                listeners.forEach { it.onConnectionStateChanged(false) }
            }
        }
    }

    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    /**
     * 获取网络类型
     */
    private fun getNetworkType(): String {
        // 简化实现，实际应用需要更复杂的网络检测
        return "UNKNOWN"
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
     * 获取内存使用率
     */
    private fun getMemoryUsage(): Double {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        return if (total > 0) (used.toDouble() / total.toDouble() * 100) else 0.0
    }

    /**
     * 获取存储使用率
     */
    private fun getStorageUsage(): Double {
        // 简化实现
        return 0.0
    }

    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Double {
        // 简化实现
        return 0.0
    }

    data class DeviceMetrics(
        val batteryLevel: Int,
        val networkType: String,
        val appVersion: String,
        val androidVersion: String,
        val deviceModel: String,
        val memoryUsage: Double,
        val storageUsage: Double,
        val cpuUsage: Double,
        val tasksCompleted: Int,
        val tasksFailed: Int
    )
}
