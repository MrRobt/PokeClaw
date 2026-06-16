package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 离线降级管理器
 * 当网络不可用时，切换到本地 Gemma 4 模型执行简单任务
 */
class OfflineFallbackManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PokeClaw/OfflineFallback"

        @Volatile
        private var instance: OfflineFallbackManager? = null

        fun getInstance(context: Context): OfflineFallbackManager {
            return instance ?: synchronized(this) {
                instance ?: OfflineFallbackManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 离线模式状态
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode

    // 本地模型是否可用
    private val _isLocalModelAvailable = MutableStateFlow(false)
    val isLocalModelAvailable: StateFlow<Boolean> = _isLocalModelAvailable

    /**
     * 进入离线模式
     */
    fun enterOfflineMode() {
        _isOfflineMode.value = true
        XLog.i(TAG, "进入离线模式，切换到本地模型")
    }

    /**
     * 退出离线模式
     */
    fun exitOfflineMode() {
        _isOfflineMode.value = false
        XLog.i(TAG, "退出离线模式，恢复云端模式")
    }

    /**
     * 检查是否可以使用本地模型
     */
    fun canUseLocalModel(): Boolean {
        return _isOfflineMode.value && _isLocalModelAvailable.value
    }

    /**
     * 设置本地模型可用性
     */
    fun setLocalModelAvailable(available: Boolean) {
        _isLocalModelAvailable.value = available
        if (available) {
            XLog.i(TAG, "本地模型可用")
        } else {
            XLog.w(TAG, "本地模型不可用")
        }
    }

    /**
     * 执行离线任务
     * 这里可以调用本地 Gemma 4 模型
     */
    suspend fun executeOfflineTask(command: String): OfflineTaskResult {
        if (!canUseLocalModel()) {
            return OfflineTaskResult(
                success = false,
                result = null,
                error = "本地模型不可用，无法执行离线任务"
            )
        }

        return try {
            XLog.i(TAG, "执行离线任务: $command")
            // TODO: 接入本地 Gemma 4 模型执行
            // 目前返回占位结果
            OfflineTaskResult(
                success = true,
                result = "离线模式：任务已本地执行（Gemma 4 待接入）",
                error = null
            )
        } catch (e: Exception) {
            XLog.e(TAG, "离线任务执行失败", e)
            OfflineTaskResult(
                success = false,
                result = null,
                error = e.message
            )
        }
    }

    /**
     * 离线任务结果
     */
    data class OfflineTaskResult(
        val success: Boolean,
        val result: String?,
        val error: String?
    )
}
