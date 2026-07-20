// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端云执行节点编排器 — 串联注册、心跳、任务拉取、执行、结果上报的最小闭环。

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.agent.safety.BatteryGuard
import io.agents.pokeclaw.agent.safety.BatteryStatus
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
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val cloudClient: DeviceCloudClient,
    private val tokenStore: CloudDeviceTokenStore,
    private val offlineQueue: OfflineResultQueue,
    private val taskExecutor: CloudTaskExecutor,
    private val config: OrchestratorConfig = OrchestratorConfig(),
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    /**
     * Android-dependent info source (battery / network / Build.* / KVUtils).
     * Default uses [AndroidDeviceInfoProvider]; tests inject a fake to keep
     * the orchestrator pure-JVM testable.
     */
    private val deviceInfo: DeviceInfoProvider,
) {
    /**
     * Android entry point — wraps [Context] into a default [AndroidDeviceInfoProvider].
     * Production code uses this; JVM tests use the primary constructor and inject
     * a [DeviceInfoProvider] fake directly.
     */
    constructor(
        context: Context,
        cloudClient: DeviceCloudClient,
        tokenStore: CloudDeviceTokenStore,
        offlineQueue: OfflineResultQueue,
        taskExecutor: CloudTaskExecutor,
        config: OrchestratorConfig = OrchestratorConfig(),
        clock: CloudExecutorClock = SystemCloudExecutorClock,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) : this(
        cloudClient = cloudClient,
        tokenStore = tokenStore,
        offlineQueue = offlineQueue,
        taskExecutor = taskExecutor,
        config = config,
        clock = clock,
        scope = scope,
        deviceInfo = AndroidDeviceInfoProvider(context),
    )

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
        deviceId = deviceInfo.loadOrGenerateDeviceId()

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

    /**
     * C3-01：主动取消当前正在执行的任务。
     *
     * 语义：
     * - 当前无任务执行 → Result.failure(IllegalStateException)，不调云端
     * - 取消请求成功但 data=false → Result.success(cancelled=false)，调用方降级处理
     * - 取消请求网络异常 → Result.failure，任务继续在端侧跑完（不丢）
     *
     * 不修改 _state —— cancelTask 不会打断当前 EXECUTING 状态，结果上报仍由原 executeCloudTask 流程负责。
     */
    suspend fun cancelTask(reason: String? = null): Result<Boolean> {
        val taskUuid = currentTaskUuid
        if (taskUuid == null) {
            XLog.w(TAG, "cancelTask: 当前无任务在执行，忽略取消请求")
            return Result.failure(IllegalStateException("当前无任务在执行"))
        }
        XLog.i(TAG, "cancelTask: 取消任务 taskUuid=$taskUuid, reason=$reason")
        val request = TaskResultRequest(
            status = TaskResultRequest.Status.CANCELLED,
            result = reason?.take(2048) ?: "用户主动取消",
            errorMessage = reason?.take(1024),
            executionTimeMs = 0L,
        )
        val response = cloudClient.cancelTask(taskUuid, request)
        return response.map { it.cancelled() }
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
            deviceName = deviceInfo.deviceModel(),
            deviceModel = deviceInfo.deviceModel(),
            androidVersion = deviceInfo.androidVersion(),
            appVersion = deviceInfo.appVersion(),
        )
        XLog.i(TAG, "registerDevice: 注册设备 $id, model=${deviceInfo.deviceModel()}")
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
        val batteryInfo = deviceInfo.readBatteryInfo()
        return DeviceHeartbeatRequest(
            batteryLevel = batteryInfo.first,
            isCharging = batteryInfo.second,
            networkType = deviceInfo.readNetworkType().value,
        )
    }

    /** 执行单个云端任务并上报结果。 */
    private suspend fun executeCloudTask(task: PendingTaskItem) {
        _state = State.EXECUTING
        currentTaskUuid = task.taskUuid
        val startTime = clock.nowMillis()

        XLog.i(TAG, "executeCloudTask: 开始执行 taskUuid=${task.taskUuid}, command=${task.command}")

        // T49 电量前置守卫：低电量且未充电时直接拒绝执行并上报 LOW_BATTERY，避免任务中途掉电导致更难恢复。
        // readBatteryInfo() 返回可空 Pair；读不到时按 fail-safe 视为电量充足，避免误伤正常任务。
        val battery = deviceInfo.readBatteryInfo()
        val batteryDecision = BatteryGuard.evaluate(
            BatteryStatus(percent = battery.first ?: 100, isCharging = battery.second ?: true),
        )
        if (!batteryDecision.allowed) {
            XLog.w(TAG, "executeCloudTask: 电量守卫拦截 taskUuid=${task.taskUuid}, ${batteryDecision.reason}")
        }

        val result: CloudTaskExecutionResult = if (!batteryDecision.allowed) {
            CloudTaskExecutionResult.failure(
                message = "设备电量不足，已拒绝执行：${batteryDecision.reason}",
                errorCode = CloudTaskErrorCode.LOW_BATTERY,
                retryable = true,
            )
        } else try {
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
            logSnippet = buildResultLogSnippet(task, result, executionTimeMs),
        )

        // 上报（Result 语义：成功 / 失败 / 离线入队 由 RetrofitDeviceCloudClient 内部处理）
        val reported = cloudClient.submitTaskResult(task.taskUuid, reportRequest)
        XLog.i(
            TAG,
            "executeCloudTask: 结果上报${if (reported.isSuccess) "成功" else "失败/已缓存"}，" +
                "taskUuid=${task.taskUuid}, status=$statusReport, " +
                "artifacts=${result.artifacts.size}",
        )

        // 跨设备自进化：任务终态经验上报云端（ExperienceUploader → /api/claw-device/experience，claw_experience 汇聚）
        reportExperience(task, result)

        currentTaskUuid = null
        if (_state == State.EXECUTING) {
            _state = State.RUNNING
        }
    }

    /**
     * 跨设备自进化：把云任务成功/失败经验上报云端（ExperienceUploader → /api/claw-device/experience）。
     * 让端侧本地自进化经验跨设备汇聚 / 供云端训练。best-effort，异常不影响任务结果上报。
     */
    private fun reportExperience(task: PendingTaskItem, result: CloudTaskExecutionResult) {
        try {
            val id = deviceId ?: return
            val token = tokenStore.snapshot()?.deviceToken ?: return
            val baseUrl = io.agents.pokeclaw.utils.KVUtils.getString("cloud_base_url")
            if (baseUrl.isBlank()) {
                return
            }
            val uploader = ExperienceUploader(baseUrl, id) { token }
            if (result.success) {
                uploader.uploadSuccess(
                    ExperienceUploader.SuccessExperience(
                        commercialTaskId = task.taskUuid,
                        summary = result.message.take(500),
                    )
                )
            } else {
                uploader.uploadFailure(
                    ExperienceUploader.FailureExperience(
                        commercialTaskId = task.taskUuid,
                        errorCategory = result.errorCode.name,
                        errorCode = result.errorCode.name,
                        recoveryHint = result.message.take(500),
                    )
                )
            }
            XLog.i(TAG, "reportExperience: 自进化经验已上报 taskUuid=${task.taskUuid}, success=${result.success}")
        } catch (e: Exception) {
            XLog.w(TAG, "reportExperience: 上报经验异常 taskUuid=${task.taskUuid}", e)
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

    private fun buildResultLogSnippet(
        task: PendingTaskItem,
        result: CloudTaskExecutionResult,
        executionTimeMs: Long,
    ): String {
        return buildString {
            append("taskUuid=").append(task.taskUuid)
            append(" status=").append(if (result.success) "SUCCESS" else "FAILED")
            append(" durationMs=").append(executionTimeMs)
            append(" errorCode=").append(result.errorCode.name)
            if (result.artifacts.isNotEmpty()) {
                append(" artifacts=").append(result.artifacts.joinToString(",").take(512))
            }
            append(" message=").append(result.message.take(512))
        }.take(1024)
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
            CloudTaskErrorCode.AUTH_REQUIRED -> ErrorDetail(
                category = "AUTH_REQUIRED",
                code = "AUTH_REQUIRED",
                detail = result.message.take(500).ifBlank { "账号未登录或登录态失效，需要人工完成登录/验证" },
                recoverable = false,
                suggestedAction = "请人工完成登录验证",
            )
            CloudTaskErrorCode.RISK_CONTROL -> ErrorDetail(
                category = "RISK_CONTROL",
                code = "RISK_CONTROL",
                detail = result.message.take(500).ifBlank { "触发平台风控（验证码/安全验证/滑块），已暂停执行" },
                recoverable = false,
                suggestedAction = "请人工完成登录验证",
            )
            CloudTaskErrorCode.LOW_BATTERY -> ErrorDetail(
                category = "LOW_BATTERY",
                code = "LOW_BATTERY",
                detail = result.message.take(500).ifBlank { "设备电量低于安全阈值，已拒绝执行任务" },
                recoverable = true,
                suggestedAction = "请为设备充电至安全电量后重试",
            )
            CloudTaskErrorCode.STORAGE_FULL -> ErrorDetail(
                category = "STORAGE_FULL",
                code = "STORAGE_FULL",
                detail = result.message.take(500).ifBlank { "端侧存储空间不足，无法完成任务" },
                recoverable = true,
                suggestedAction = "请清理设备存储后重试",
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

    // ── 设备信息读取已下沉到 DeviceInfoProvider ──

    companion object {
        private const val TAG = "PokeClaw/CloudNodeOrchestrator"
    }
}
