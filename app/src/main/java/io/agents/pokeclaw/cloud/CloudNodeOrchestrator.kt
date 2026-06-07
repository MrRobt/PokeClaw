// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云执行节点编排器 — 串联注册、心跳、任务拉取、执行、结果上报的最小闭环。

package io.agents.pokeclaw.cloud

import android.content.Context
import android.os.Build
import io.agents.pokeclaw.BuildConfig
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.NetworkType
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloudnode.CloudExecutorClock
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.cloud.model.TaskStatus
import io.agents.pokeclaw.cloudnode.SystemCloudExecutorClock
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 端云执行节点编排器。
 *
 * 职责：
 * 1. 启动时向 dyq 云端注册设备（如未注册）
 * 2. 协程心跳循环：定时发心跳，响应 pendingTaskCount>0 拉取任务
 * 3. 收到任务后通过 [CloudTaskExecutor] 执行
 * 4. 执行完成后通过 [DeviceCloudClient] 上报结果
 * 5. 离线时结果进入 [CloudEventQueue] 缓存，网络恢复后补报
 *
 * 生命周期：
 * - start() 启动心跳协程，由 ForegroundService 或 Application 驱动
 * - stop() 取消心跳协程，不清理注册信息
 * - 当前任务正在执行时不会被心跳中断
 */
class CloudNodeOrchestrator(
    private val context: Context,
    private val cloudClient: DeviceCloudClient,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: CloudEventQueue,
    private val taskExecutor: CloudTaskExecutor,
    private val config: OrchestratorConfig = OrchestratorConfig(),
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    /** 编排器配置。 */
    data class OrchestratorConfig(
        val heartbeatIntervalMs: Long = 30_000L,
        val maxConsecutiveHeartbeatFailures: Int = 3,
        val flushQueueOnHeartbeat: Boolean = true,
        val autoRegisterOnStart: Boolean = true,
    )

    /** 编排器状态，只暴露观察用的快照。 */
    enum class State {
        IDLE,           // 未启动
        REGISTERING,    // 正在注册
        RUNNING,        // 心跳循环中
        EXECUTING,      // 正在执行云端任务
        STOPPED,        // 已停止
        ERROR,          // 错误状态（注册失败或心跳连续失败）
    }

    // ── 内部状态 ──
    private var _state: State = State.IDLE
    val state: State get() = _state
    private var heartbeatJob: Job? = null
    private var consecutiveHeartbeatFailures = 0
    private var currentTaskUuid: String? = null
    private var deviceId: String? = null

    /** 设备编号（首次注册后持久化）。 */
    fun getDeviceId(): String? = deviceId

    /** 当前正在执行的云端任务编号。 */
    fun getCurrentTaskUuid(): String? = currentTaskUuid

    // ── 公共方法 ──

    /**
     * 启动编排器。
     *
     * 流程：读取/生成 deviceId → 注册（如需）→ 启动心跳循环。
     * 此方法不会阻塞；注册和心跳在协程中异步执行。
     */
    fun start() {
        if (_state != State.IDLE && _state != State.STOPPED && _state != State.ERROR) {
            XLog.w(TAG, "start: 当前状态=$_state，忽略重复启动")
            return
        }
        XLog.i(TAG, "start: 启动端云编排器")
        deviceId = loadOrGenerateDeviceId()

        scope.launch {
            try {
                startInternal()
            } catch (e: CancellationException) {
                XLog.i(TAG, "startInternal: 协程被取消")
                _state = State.STOPPED
            } catch (e: Exception) {
                XLog.e(TAG, "startInternal: 启动异常", e)
                _state = State.ERROR
            }
        }
    }

    /** 停止编排器，取消心跳协程。 */
    fun stop() {
        XLog.i(TAG, "stop: 停止端云编排器，state=$_state")
        heartbeatJob?.cancel()
        heartbeatJob = null
        _state = State.STOPPED
    }

    /** 通知编排器有新任务需要执行（由心跳响应触发或外部调用）。 */
    suspend fun onPendingTasksAvailable(tasks: List<PendingTaskItem>) {
        if (tasks.isEmpty()) return
        if (_state == State.EXECUTING) {
            XLog.w(TAG, "onPendingTasksAvailable: 正在执行任务，跳过本次拉取（${tasks.size} 个待处理）")
            return
        }
        val task = tasks.first()
        XLog.i(TAG, "onPendingTasksAvailable: 取首个任务 taskUuid=${task.taskUuid}, command=${task.command}")
        executeCloudTask(task)
    }

    // ── 内部实现 ──

    private suspend fun startInternal() {
        // 1. 注册（如需）
        if (config.autoRegisterOnStart && tokenStore.snapshot() == null) {
            _state = State.REGISTERING
            val registered = registerDevice()
            if (!registered) {
                XLog.e(TAG, "startInternal: 设备注册失败，进入错误状态")
                _state = State.ERROR
                return
            }
        }

        // 2. 启动心跳循环
        _state = State.RUNNING
        heartbeatLoop()
    }

    private suspend fun registerDevice(): Boolean {
        val id = deviceId ?: return false.also {
            XLog.e(TAG, "registerDevice: deviceId 为空")
        }
        val request = DeviceRegisterRequest(
            deviceId = id,
            deviceName = Build.MODEL,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
        )
        XLog.i(TAG, "registerDevice: 注册设备 $id, model=${Build.MODEL}")
        val result = cloudClient.register(request)
        if (result.isFailure) {
            XLog.e(TAG, "registerDevice: 注册失败，${result.exceptionOrNull()?.message}")
        }
        return result.isSuccess
    }

    private suspend fun heartbeatLoop() {
        XLog.i(TAG, "heartbeatLoop: 启动心跳循环，间隔=${config.heartbeatIntervalMs}ms")
        while (scope.coroutineContext.isActive) {
            try {
                // 补报离线队列
                if (config.flushQueueOnHeartbeat) {
                    cloudClient.flushOfflineQueue(clock.nowMillis())
                }

                // 发心跳
                val heartbeatRequest = buildHeartbeatRequest()
                val heartbeatResult = cloudClient.sendHeartbeat(heartbeatRequest)
                val success = heartbeatResult.isSuccess

                if (success) {
                    consecutiveHeartbeatFailures = 0
                    if (_state != State.EXECUTING) {
                        _state = State.RUNNING
                    }
                } else {
                    consecutiveHeartbeatFailures++
                    XLog.w(TAG, "heartbeatLoop: 连续心跳失败 $consecutiveHeartbeatFailures 次：${heartbeatResult.exceptionOrNull()?.message}")
                    if (consecutiveHeartbeatFailures >= config.maxConsecutiveHeartbeatFailures) {
                        XLog.e(TAG, "heartbeatLoop: 连续心跳失败达 ${config.maxConsecutiveHeartbeatFailures} 次，标记离线")
                        _state = State.ERROR
                    }
                }

                // 检查是否有待处理任务
                val id = deviceId
                if (success && id != null && _state != State.EXECUTING) {
                    val tasksResult = cloudClient.getPendingTasks(id)
                    val tasks = tasksResult.getOrNull()?.data.orEmpty()
                    if (tasks.isNotEmpty()) {
                        onPendingTasksAvailable(tasks)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                XLog.e(TAG, "heartbeatLoop: 心跳循环异常", e)
                consecutiveHeartbeatFailures++
            }

            delay(config.heartbeatIntervalMs)
        }
    }

    private fun buildHeartbeatRequest(): DeviceHeartbeatRequest {
        val batteryInfo = readBatteryInfo()
        return DeviceHeartbeatRequest(
            batteryLevel = batteryInfo.first,
            isCharging = batteryInfo.second,
            networkType = readNetworkType().value,
        )
    }

    /** 执行单个云端任务并上报结果。 */
    private suspend fun executeCloudTask(task: PendingTaskItem) {
        _state = State.EXECUTING
        currentTaskUuid = task.taskUuid
        val startTime = clock.nowMillis()

        XLog.i(TAG, "executeCloudTask: 开始执行 taskUuid=${task.taskUuid}, command=${task.command}")

        val result: CloudTaskExecutionResult = try {
            withContext(Dispatchers.IO) {
                taskExecutor.execute(task)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            XLog.e(TAG, "executeCloudTask: 执行异常，taskUuid=${task.taskUuid}", e)
            CloudTaskExecutionResult.failure(
                message = e.message ?: "执行异常",
                errorCode = CloudTaskErrorCode.UNKNOWN,
                retryable = true,
            )
        }

        val executionTimeMs = clock.nowMillis() - startTime
        val statusReport = mapResultToStatusReport(result)

        // 构造错误详情（仅失败时填充）
        val errorDetail = if (!result.success) buildErrorDetail(result) else null

        // 把执行器产出的 artifacts 拆为 toolCalls（操作序列）+ evidenceUrls（截图/资源路径）
        val (toolCalls, evidenceUrls) = splitArtifacts(result.artifacts)

        // 上报结果
        val reportRequest = TaskResultRequest(
            status = statusReport,
            result = result.message.take(2048),
            errorMessage = if (!result.success) result.message.take(1024) else null,
            executionTimeMs = executionTimeMs,
            modelUsed = taskExecutor.getModelName(),
            toolCalls = toolCalls,
            evidenceUrls = evidenceUrls,
            // 错误回传字段（对齐云端错误上报协议）
            errorCategory = errorDetail?.category,
            errorCode = errorDetail?.code,
            errorDetail = errorDetail?.detail,
            recoverable = errorDetail?.recoverable,
            suggestedAction = errorDetail?.suggestedAction,
        )

        // 上报（Result 语义：成功 / 失败 / 离线入队 由 RetrofitDeviceCloudClient 内部处理）
        val reported = cloudClient.submitTaskResult(task.taskUuid, reportRequest)
        XLog.i(
            TAG,
            "executeCloudTask: 结果上报${if (reported.isSuccess) "成功" else "失败/已缓存"}，" +
                "taskUuid=${task.taskUuid}, status=$statusReport, " +
                "artifacts=${result.artifacts.size}",
        )

        currentTaskUuid = null
        if (_state == State.EXECUTING) {
            _state = State.RUNNING
        }
    }

    private fun mapResultToStatusReport(result: CloudTaskExecutionResult): TaskResultRequest.Status {
        return when {
            result.success -> TaskResultRequest.Status.SUCCESS
            result.errorCode == CloudTaskErrorCode.PERMISSION_MISSING -> TaskResultRequest.Status.FAILED
            result.retryable -> TaskResultRequest.Status.FAILED
            else -> TaskResultRequest.Status.FAILED
        }
    }

    /**
     * 构造错误详情（用于云端错误回传）。
     * 将 CloudTaskErrorCode 映射为结构化错误信息。
     */
    private data class ErrorDetail(
        val category: String,
        val code: String,
        val detail: String?,
        val recoverable: Boolean,
        val suggestedAction: String?,
    )

    /**
     * 把执行器产出的 artifacts 拆为 toolCalls + evidenceUrls 两组。
     * 拆分规则：含 "skill:" / "taskUuid:" / "steps:" / "params:" / "mode:" / "priority:" 视为操作元数据
     *          （进 toolCalls）；其它（截图路径、文件 URL 等）进 evidenceUrls。
     */
    private fun splitArtifacts(artifacts: List<String>): Pair<String?, String?> {
        if (artifacts.isEmpty()) return null to null
        val metadata = artifacts.filter { a ->
            a.startsWith("skill:") || a.startsWith("taskUuid:") ||
                a.startsWith("steps:") || a.startsWith("params:") ||
                a.startsWith("mode:") || a.startsWith("priority:")
        }
        val evidence = artifacts - metadata.toSet()
        return metadata.joinToString(";").takeIf { it.isNotBlank() } to
            evidence.joinToString(",").takeIf { it.isNotBlank() }
    }

    private fun buildErrorDetail(result: CloudTaskExecutionResult): ErrorDetail {
        return when (result.errorCode) {
            CloudTaskErrorCode.PERMISSION_MISSING -> ErrorDetail(
                category = "PERMISSION",
                code = "PERMISSION_MISSING",
                detail = "端侧缺少执行任务所需权限（无障碍服务/悬浮窗/存储等）",
                recoverable = false,
                suggestedAction = "请前往设置开启 PokeClaw 无障碍服务权限",
            )
            CloudTaskErrorCode.NETWORK_UNAVAILABLE -> ErrorDetail(
                category = "NETWORK",
                code = "NETWORK_UNAVAILABLE",
                detail = "端侧网络不可用，无法完成任务",
                recoverable = true,
                suggestedAction = "检查网络连接后重试",
            )
            CloudTaskErrorCode.TASK_REJECTED -> ErrorDetail(
                category = "TASK",
                code = "TASK_REJECTED",
                detail = "任务指令被拒绝或格式无效",
                recoverable = false,
                suggestedAction = "检查任务指令格式",
            )
            CloudTaskErrorCode.TOOL_FAILED -> ErrorDetail(
                category = "TOOL",
                code = "TOOL_FAILED",
                detail = "工具执行失败（截图/点击/输入等）",
                recoverable = true,
                suggestedAction = "检查目标应用状态后重试",
            )
            CloudTaskErrorCode.EXECUTION_TIMEOUT -> ErrorDetail(
                category = "TIMEOUT",
                code = "EXECUTION_TIMEOUT",
                detail = "任务执行超时",
                recoverable = true,
                suggestedAction = "简化任务或增加超时时间后重试",
            )
            else -> ErrorDetail(
                category = "UNKNOWN",
                code = "UNKNOWN_ERROR",
                detail = result.message.take(500),
                recoverable = result.retryable,
                suggestedAction = if (result.retryable) "可尝试重试" else "请联系技术支持",
            )
        }
    }

    // ── 设备信息读取 ──

    private fun loadOrGenerateDeviceId(): String {
        val existing = KVUtils.getString(KEY_DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val newId = "pokeclaw-${UUID.randomUUID()}"
        KVUtils.putString(KEY_DEVICE_ID, newId)
        XLog.i(TAG, "loadOrGenerateDeviceId: 生成新设备编号 $newId")
        return newId
    }

    private fun readBatteryInfo(): Pair<Int?, Boolean?> {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
                Pair(batteryPct, isCharging)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            XLog.w(TAG, "readBatteryInfo: 读取电量失败", e)
            Pair(null, null)
        }
    }

    private fun readNetworkType(): NetworkType {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 使用新的 NetworkCapabilities API
                val network = cm?.activeNetwork
                val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                when {
                    network == null || capabilities == null -> NetworkType.OFFLINE
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    else -> NetworkType.OFFLINE
                }
            } else {
                // 兼容旧版本（Android 5.x）
                @Suppress("DEPRECATION")
                val activeNetwork = cm?.activeNetworkInfo
                @Suppress("DEPRECATION")
                when {
                    activeNetwork == null || !activeNetwork.isConnected -> NetworkType.OFFLINE
                    activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                    activeNetwork.type == android.net.ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                    else -> NetworkType.OFFLINE
                }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "readNetworkType: 读取网络类型失败", e)
            NetworkType.OFFLINE
        }
    }

    companion object {
        private const val TAG = "PokeClaw/CloudNodeOrchestrator"
        private const val KEY_DEVICE_ID = "cloud_device_id"
    }
}
