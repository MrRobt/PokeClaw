// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端心跳管理器：周期性设备状态上报与任务拉取

package io.agents.pokeclaw.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType as WorkNetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.agents.pokeclaw.cloud.model.DeviceCloudStatus
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.NetworkType as CloudNetworkType
import io.agents.pokeclaw.cloud.util.ClockSkewDetector
import io.agents.pokeclaw.cloud.util.SkillVersionCache
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 云端心跳工作器。
 *
 * 设计原则：
 * - 只做心跳上报和任务拉取，不做任务执行
 * - 失败时记录日志，由调用方决定是否重试或标记离线
 * - 网络状态通过 Constraints 前置过滤
 */
class CloudHeartbeatWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val TAG = "CloudHeartbeatWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        XLog.d(TAG, "心跳工作器启动")

        val manager = CloudHeartbeatManager.getInstance(applicationContext)
        val client = manager.deviceCloudClient

        if (client == null) {
            XLog.w(TAG, "云端客户端未初始化，跳过本次心跳")
            return@withContext Result.retry()
        }

        // 构建心跳请求（复用 AppCapabilityCoordinator 能力快照）
        val heartbeatRequest = manager.buildHeartbeatRequest()

        // 发送心跳
        val result = try {
            client.sendHeartbeat(heartbeatRequest)
        } catch (e: Exception) {
            XLog.e(TAG, "心跳发送异常", e)
            Result.failure<DeviceHeartbeat200Response>(e)
        }

        if (result.isFailure) {
            manager.recordHeartbeatFailure()
            return@withContext Result.retry()
        }

        // 心跳成功，喂入响应到遥测工具
        manager.onHeartbeatResponse(result.getOrThrow())

        // 心跳成功，拉取待处理任务
        manager.recordHeartbeatSuccess()
        manager.pullPendingTasks()

        Result.success()
    }
}

/**
 * 云端心跳管理器。
 *
 * 职责：
 * - 管理 WorkManager 周期性心跳调度
 * - 提供心跳请求构建（复用现有能力快照）
 * - 维护在线/离线状态
 * - 触发任务拉取
 */
class CloudHeartbeatManager private constructor(
    private val context: Context,
) {
    private val TAG = "CloudHeartbeatManager"

    // 云端客户端，由外部注入
    var deviceCloudClient: DeviceCloudClient? = null
        private set

    // 设备编号缓存
    private var deviceId: String? = null

    // 心跳配置
    private var heartbeatIntervalMinutes: Long = DEFAULT_HEARTBEAT_INTERVAL_MINUTES
    private var isEnabled: Boolean = false

    // 状态追踪
    private var consecutiveFailures: Int = 0
    private var lastHeartbeatTime: Long = 0
    private var currentStatus: DeviceCloudStatus = DeviceCloudStatus.UNREGISTERED

    // R7 心跳遥测
    private val clockSkewDetector = ClockSkewDetector()
    private val skillVersionCache = SkillVersionCache()

    /**
     * 初始化云端客户端。
     *
     * @param client 设备云端客户端
     * @param deviceId 设备唯一编号
     */
    fun initialize(client: DeviceCloudClient, deviceId: String) {
        this.deviceCloudClient = client
        this.deviceId = deviceId
        XLog.i(TAG, "云端客户端已初始化，deviceId=$deviceId")
    }

    /**
     * 启动周期性心跳。
     *
     * @param intervalMinutes 心跳间隔（分钟），默认 1 分钟
     */
    fun startHeartbeat(intervalMinutes: Long = DEFAULT_HEARTBEAT_INTERVAL_MINUTES) {
        if (deviceCloudClient == null) {
            XLog.w(TAG, "云端客户端未初始化，无法启动心跳")
            return
        }

        this.heartbeatIntervalMinutes = intervalMinutes
        this.isEnabled = true
        this.currentStatus = DeviceCloudStatus.ONLINE

        // 构建约束：需要网络连接
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(WorkNetworkType.CONNECTED)
            .build()

        // 创建周期性工作请求
        // 注意：WorkManager 周期最小为 15 分钟，如需更短间隔需使用自定义 Handler
        val workRequest = PeriodicWorkRequestBuilder<CloudHeartbeatWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        // 调度工作（保留现有，避免重复）
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        XLog.i(TAG, "心跳调度已启动，间隔=${intervalMinutes}分钟")
    }

    /**
     * 停止周期性心跳。
     */
    fun stopHeartbeat() {
        this.isEnabled = false
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        XLog.i(TAG, "心跳调度已停止")
    }

    /**
     * 构建心跳请求。
     * 复用 AppCapabilityCoordinator 的能力快照。
     * 对齐 device.openapi.yaml 字段规范。
     */
    fun buildHeartbeatRequest(): DeviceHeartbeatRequest {
        // 获取设备状态信息
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        val networkType = getNetworkType()

        return DeviceHeartbeatRequest(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = networkType?.value
        )
    }

    /**
     * 拉取待处理任务。
     */
    suspend fun pullPendingTasks() {
        val client = deviceCloudClient ?: return
        val deviceId = this.deviceId ?: return

        try {
            val tasks = client.getPendingTasks(deviceId)
            if (tasks.isNotEmpty()) {
                XLog.i(TAG, "拉取到 ${tasks.size} 个待处理任务")
                // 触发任务分发（通过回调或广播）
                onPendingTasksReceived(tasks)
            }
        } catch (e: Exception) {
            XLog.e(TAG, "拉取任务失败", e)
        }
    }

    /**
     * 记录心跳成功。
     */
    fun recordHeartbeatSuccess() {
        consecutiveFailures = 0
        lastHeartbeatTime = System.currentTimeMillis()
        currentStatus = DeviceCloudStatus.ONLINE
        XLog.d(TAG, "心跳成功，状态=在线")
    }

    /**
     * 记录心跳失败。
     */
    fun recordHeartbeatFailure() {
        consecutiveFailures++
        XLog.w(TAG, "心跳失败，连续失败次数=$consecutiveFailures")

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            currentStatus = DeviceCloudStatus.OFFLINE
            XLog.w(TAG, "连续 $MAX_CONSECUTIVE_FAILURES 次心跳失败，标记离线")
        }
    }

    /**
     * 处理心跳响应：喂入 serverTime 和 skillVersion 到遥测工具。
     *
     * R7 仅遥测记录，不触发主动重同步（reactive resync 是 R8+）。
     */
    internal fun onHeartbeatResponse(response: DeviceHeartbeat200Response) {
        val data = response.data ?: return
        val localNow = System.currentTimeMillis()

        // serverTime 时钟漂移检测
        val serverTime = data.serverTime
        if (serverTime != 0L) {
            val skewResult = clockSkewDetector.update(localNow, serverTime, SKEW_THRESHOLD_MILLIS)
            if (skewResult.state == ClockSkewDetector.SkewState.WARN) {
                XLog.w(TAG, "clock_skew_exceeded: skew=${skewResult.deltaMillis}ms (threshold=${SKEW_THRESHOLD_MILLIS}ms)")
            }
        }

        // skillVersion 漂移检测
        val skillVersion = data.skillVersion
        if (skillVersion != 0) {
            val previous = skillVersionCache.current()
            if (skillVersionCache.update(skillVersion)) {
                XLog.i(TAG, "skill_version_drift: old=$previous new=$skillVersion")
            }
        }
    }

    /**
     * 获取当前云端状态。
     */
    fun getStatus(): DeviceCloudStatus = currentStatus

    /**
     * 获取最后心跳时间。
     */
    fun getLastHeartbeatTime(): Long = lastHeartbeatTime

    /**
     * 是否在线。
     */
    fun isOnline(): Boolean = currentStatus == DeviceCloudStatus.ONLINE && isEnabled

    /**
     * 获取电池电量（百分比）。
     */
    private fun getBatteryLevel(): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else null
            }
        } catch (e: Exception) {
            XLog.w(TAG, "获取电池电量失败", e)
            null
        }
    }

    /**
     * 是否正在充电。
     */
    private fun isCharging(): Boolean? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            XLog.w(TAG, "获取充电状态失败", e)
            null
        }
    }

    /**
     * 获取网络类型。
     * 对齐 device.openapi.yaml v1.1.0：仅返回 wifi/cellular/offline。
     *
     * v1.1.0 升级（2026-05-21）：从已弃用的 [ConnectivityManager.activeNetworkInfo] 切到
     * [NetworkCapabilities]；无任何网络时显式返回 OFFLINE（不返回 null），让心跳请求始终带 networkType 字段。
     */
    private fun getNetworkType(): CloudNetworkType? {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager

            if (connectivityManager == null) {
                return CloudNetworkType.OFFLINE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
                when {
                    network == null || capabilities == null -> CloudNetworkType.OFFLINE
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> CloudNetworkType.WIFI
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> CloudNetworkType.CELLULAR
                    else -> CloudNetworkType.OFFLINE
                }
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                when {
                    activeNetworkInfo == null || !activeNetworkInfo.isConnected -> CloudNetworkType.OFFLINE
                    activeNetworkInfo.type == android.net.ConnectivityManager.TYPE_WIFI -> CloudNetworkType.WIFI
                    activeNetworkInfo.type == android.net.ConnectivityManager.TYPE_MOBILE -> CloudNetworkType.CELLULAR
                    else -> CloudNetworkType.OFFLINE
                }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "获取网络类型失败", e)
            CloudNetworkType.OFFLINE
        }
    }

    /**
     * 待处理任务回调。
     * 子类可重写此方法来处理拉取到的任务。
     */
    open fun onPendingTasksReceived(tasks: List<io.agents.pokeclaw.cloud.model.PendingTaskItem>) {
        // 默认实现：发送广播通知 CloudNodeOrchestrator
        val intent = android.content.Intent(ACTION_PENDING_TASKS)
        intent.setPackage(context.packageName)
        intent.putExtra(EXTRA_TASK_COUNT, tasks.size)
        context.sendBroadcast(intent)
        XLog.d(TAG, "已发送待处理任务广播，数量=${tasks.size}")
    }

    companion object {
        private const val TAG = "CloudHeartbeatManager"
        private const val WORK_NAME = "pokeclaw_cloud_heartbeat"
        private const val WORK_TAG = "cloud_heartbeat"
        private const val DEFAULT_HEARTBEAT_INTERVAL_MINUTES = 1L
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private val SKEW_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(4)

        const val ACTION_PENDING_TASKS = "io.agents.pokeclaw.action.PENDING_TASKS"
        const val EXTRA_TASK_COUNT = "task_count"

        @Volatile
        private var instance: CloudHeartbeatManager? = null

        /**
         * 获取单例实例。
         */
        fun getInstance(context: Context): CloudHeartbeatManager {
            return instance ?: synchronized(this) {
                instance ?: CloudHeartbeatManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
