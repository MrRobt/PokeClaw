// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 统一云端状态视图：聚合 CloudNodeOrchestrator + CloudHeartbeatManager + 网络监听，
 * 暴露给 UI 显示「当前模式：云端 / 离线 / 错误」。
 *
 * 设计目标：
 *  - UI 只需订阅 [currentMode] / [statusLabel] / [statusColor]
 *  - 自动桥接到 [OfflineFallbackManager]：连续失败 → 离线 → 本地 Gemma
 *  - 网络恢复 → 自动切回云端
 */
object CloudStatusHelper {

    private const val TAG = "PokeClaw/CloudStatus"

    /** 云端状态模式（对 UI 友好）。 */
    enum class Mode(val displayName: String) {
        /** 未启用 / 未注册 / 未配置 baseUrl。 */
        DISABLED("Disabled"),
        /** 注册中或等待首次心跳。 */
        CONNECTING("Connecting"),
        /** 心跳正常。 */
        CLOUD_ONLINE("Cloud"),
        /** 心跳连续失败，但还在重试窗口内。 */
        CLOUD_DEGRADED("Degraded"),
        /** 心跳连续失败超过阈值 → 切换到本地 Gemma。 */
        CLOUD_OFFLINE("Offline (local)"),
        /** 致命错误（注册失败 / Token 失效）。 */
        ERROR("Error"),
    }

    // 单一权威状态源
    private val _mode = MutableStateFlow(Mode.DISABLED)
    val mode: StateFlow<Mode> = _mode

    // 上次模式变化时间戳（毫秒）
    private val _lastTransitionAt = MutableStateFlow(0L)
    val lastTransitionAt: StateFlow<Long> = _lastTransitionAt

    // 上次错误描述（如果有）
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /**
     * 推送新状态。由 CloudNodeOrchestrator / CloudHeartbeatManager 在状态变化时调用。
     *
     * @param newMode 新模式
     * @param error 错误描述（可选）
     */
    fun reportState(newMode: Mode, error: String? = null) {
        if (_mode.value == newMode && _lastError.value == error) return
        val previous = _mode.value
        _mode.value = newMode
        _lastError.value = error
        _lastTransitionAt.value = System.currentTimeMillis()
        XLog.i(TAG, "reportState: $previous → $newMode, error=$error")
        onModeChanged(previous, newMode)
    }

    /**
     * 当 CloudNodeOrchestrator 标记心跳失败时调用。
     */
    fun reportHeartbeatFailure(consecutiveFailures: Int, threshold: Int = 3) {
        when {
            consecutiveFailures <= 0 -> reportState(Mode.CLOUD_ONLINE)
            consecutiveFailures < threshold -> reportState(
                Mode.CLOUD_DEGRADED,
                "consecutive=$consecutiveFailures/$threshold"
            )
            else -> reportState(
                Mode.CLOUD_OFFLINE,
                "exceeded $threshold consecutive heartbeat failures"
            )
        }
    }

    /**
     * 心跳恢复。
     */
    fun reportHeartbeatSuccess() {
        reportState(Mode.CLOUD_ONLINE)
    }

    /**
     * 模式变化的副作用：同步到 OfflineFallbackManager。
     */
    private fun onModeChanged(previous: Mode, current: Mode) {
        val context = appContext ?: return
        val fallback = OfflineFallbackManager.getInstance(context)
        when (current) {
            Mode.CLOUD_OFFLINE -> {
                if (!fallback.isOfflineMode.value) {
                    fallback.enterOfflineMode()
                }
            }
            Mode.CLOUD_ONLINE, Mode.CLOUD_DEGRADED -> {
                if (fallback.isOfflineMode.value) {
                    fallback.exitOfflineMode()
                }
            }
            else -> { /* DISABLED / CONNECTING / ERROR 不切离线 */ }
        }
    }

    /**
     * UI 状态描述（短文本，用于设置页 / 状态栏）。
     */
    fun statusLabel(): String {
        return when (_mode.value) {
            Mode.DISABLED -> "Cloud: Off"
            Mode.CONNECTING -> "Cloud: Connecting…"
            Mode.CLOUD_ONLINE -> "Cloud: Online"
            Mode.CLOUD_DEGRADED -> "Cloud: Degraded (${_lastError.value ?: "retrying"})"
            Mode.CLOUD_OFFLINE -> "Local: Gemma"
            Mode.ERROR -> "Cloud: Error (${_lastError.value ?: "unknown"})"
        }
    }

    /**
     * 启动器：在 Application.onCreate 中调用一次。
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        // 启动时从 KV 读取初始模式
        val enabled = KVUtils.getBoolean("cloud_enabled", false)
        _mode.value = if (enabled) Mode.CONNECTING else Mode.DISABLED
        XLog.i(TAG, "initialize: enabled=$enabled, mode=${_mode.value}")
    }

    @Volatile
    private var appContext: Context? = null
}
