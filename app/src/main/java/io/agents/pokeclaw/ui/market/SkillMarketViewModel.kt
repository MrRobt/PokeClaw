// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Skill Market ViewModel (US-D-039)

package io.agents.pokeclaw.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Skill Market ViewModel。
 *
 * 暴露 [uiState]（StateFlow）给 UI 订阅。
 *
 * 三态机：
 *  - [UiState.Loading] — 加载中
 *  - [UiState.Loaded] — 成功拿到列表
 *  - [UiState.Error] — 失败 / 拒绝
 *
 * 复用现有 [SkillMarketplaceClient]，不重写 client 层。
 */
class SkillMarketViewModel(
    private val client: SkillMarketplaceClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _installEvents = MutableStateFlow<InstallEvent?>(null)
    val installEvents: StateFlow<InstallEvent?> = _installEvents.asStateFlow()

    private val tag = "SkillMarketViewModel"

    init {
        loadSkills()
    }

    fun loadSkills() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = try {
                client.listSkills()
            } catch (e: Exception) {
                XLog.e(tag, "loadSkills: network exception", e)
                _uiState.value = UiState.Error("网络异常：${e.javaClass.simpleName}")
                return@launch
            }
            _uiState.value = when (result) {
                is SkillMarketplaceClient.Result.OkList -> UiState.Loaded(result.skills)
                is SkillMarketplaceClient.Result.Rejected -> UiState.Error(result.message)
                else -> UiState.Error("未知结果")
            }
        }
    }

    fun installSkill(skillId: String) {
        viewModelScope.launch {
            val result = try {
                client.installSkill(skillId)
            } catch (e: Exception) {
                XLog.e(tag, "installSkill: network exception skillId=$skillId", e)
                _installEvents.value = InstallEvent.Failed(skillId, "网络异常：${e.javaClass.simpleName}")
                return@launch
            }
            _installEvents.value = when (result) {
                is SkillMarketplaceClient.Result.OkBoolean ->
                    if (result.value) InstallEvent.Success(skillId) else InstallEvent.Failed(skillId, "后端拒绝")
                is SkillMarketplaceClient.Result.Rejected -> InstallEvent.Failed(skillId, result.message)
                else -> InstallEvent.Failed(skillId, "未知结果")
            }
        }
    }

    fun consumeInstallEvent() {
        _installEvents.value = null
    }

    /**
     * Fix code-review H5：延迟 5 秒后静默重拉整列表，确保后端最终一致。
     * 避免每次 install 立即重拉导致：
     *  - 短时间内多用户并发安装时服务端压力放大
     *  - UI 闪烁（loading → list）
     */
    fun refreshAfterDelay(delayMillis: Long) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(delayMillis)
            loadSkills()
        }
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Loaded(val skills: List<ClawAppSkillMarketRespVO>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class InstallEvent {
        abstract val skillId: String
        data class Success(override val skillId: String) : InstallEvent()
        data class Failed(override val skillId: String, val reason: String) : InstallEvent()
    }

    /**
     * 显式 ViewModelProvider.Factory — 避免引入 hilt 等 DI 框架。
     */
    class Factory(private val client: SkillMarketplaceClient) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SkillMarketViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SkillMarketViewModel(client) as T
        }
    }
}
