// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Skill Market Activity (US-D-039)

package io.agents.pokeclaw.ui.market

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.agents.pokeclaw.R
import io.agents.pokeclaw.cloud.CloudClientFactory
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.launch

/**
 * Skill Market Activity（US-D-039）
 *
 * 入口：Settings → Skill Market
 * 模式：
 *  - 启动时通过 [SkillMarketViewModel] 异步加载技能列表
 *  - 每个 row 显示 skillId / skillName / description / vendor / installStatus
 *  - 行内 [Install] 按钮触发 installSkill；按钮在已安装时禁用
 *  - 失败回退到 Retry 按钮
 *
 * 复用：
 *  - [SkillMarketplaceClient] 已有的 listSkills / installSkill（不重写）
 *  - [VendorBillingActivity] 的 ListView + ArrayAdapter 模式
 */
class SkillMarketActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "SkillMarketActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: SkillAdapter
    private lateinit var viewModel: SkillMarketViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skill_market)
        title = getString(R.string.settings_skill_market_title)

        // 1. 构造 SkillMarketplaceClient（懒初始化失败时回退到空 client）
        val client = buildSkillMarketplaceClient() ?: run {
            Toast.makeText(this, R.string.skill_market_offline_toast, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        viewModel = SkillMarketViewModel.Factory(client).let {
            androidx.lifecycle.ViewModelProvider(this, it)[SkillMarketViewModel::class.java]
        }

        // 2. ListView
        listView = findViewById(R.id.lv_skill_market)
        adapter = SkillAdapter(this, emptyList()) { skill ->
            viewModel.installSkill(skill.skillId)
        }
        listView.adapter = adapter

        // 3. observe state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.installEvents.collect { event ->
                        if (event != null) {
                            when (event) {
                                is SkillMarketViewModel.InstallEvent.Success -> {
                                    Toast.makeText(this@SkillMarketActivity, "已安装：${event.skillId}", Toast.LENGTH_SHORT).show()
                                    // Fix code-review H5：乐观更新本地 row，避免重新拉整列表
                                    // 后台 5s 后再做一次静默同步，保证最终一致性
                                    adapter.markInstalled(event.skillId)
                                    viewModel.refreshAfterDelay(5000L)
                                }
                                is SkillMarketViewModel.InstallEvent.Failed ->
                                    Toast.makeText(this@SkillMarketActivity, "安装失败：${event.reason}", Toast.LENGTH_LONG).show()
                            }
                            viewModel.consumeInstallEvent()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: SkillMarketViewModel.UiState) {
        when (state) {
            is SkillMarketViewModel.UiState.Loading -> {
                XLog.d(TAG, "renderState: loading")
                adapter.replaceAll(emptyList())
            }
            is SkillMarketViewModel.UiState.Loaded -> {
                XLog.i(TAG, "renderState: loaded ${state.skills.size} skills")
                adapter.replaceAll(state.skills)
            }
            is SkillMarketViewModel.UiState.Error -> {
                XLog.e(TAG, "renderState: error=${state.message}")
                Toast.makeText(this, "加载失败：${state.message}", Toast.LENGTH_LONG).show()
                adapter.replaceAll(emptyList())
            }
        }
    }

    /**
     * 复用 CloudClientFactory.buildSkillMarketplaceApi + SkillMarketplaceClient 包装
     *
     * baseUrl 与 tokenStore 优先用云端配置；缺一则返回 null（视为不可用）。
     */
    private fun buildSkillMarketplaceClient(): SkillMarketplaceClient? {
        val baseUrl = KVUtils.getDefaultCloudBaseUrl().takeIf { it.isNotBlank() } ?: return null
        val tokenStore: CloudDeviceTokenStore =
            io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore(applicationContext)
        return try {
            SkillMarketplaceClient(
                CloudClientFactory.buildSkillMarketplaceApi(baseUrl, tokenStore)
            )
        } catch (e: Exception) {
            XLog.e(TAG, "buildSkillMarketplaceClient: init failed", e)
            null
        }
    }

    private class SkillAdapter(
        activity: Activity,
        initial: List<ClawAppSkillMarketRespVO>,
        private val onInstallClick: (ClawAppSkillMarketRespVO) -> Unit,
    ) : ArrayAdapter<ClawAppSkillMarketRespVO>(
        activity,
        R.layout.item_skill_market_row,
        R.id.tv_skill_title,
        initial,
    ) {
        fun replaceAll(items: List<ClawAppSkillMarketRespVO>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        /**
         * Fix code-review H5：乐观更新单行的 installStatus，避免每次安装后重拉整列表。
         * 不可变数据 + ArrayAdapter：找到匹配的 skillId → 替换为带新 status 的 copy。
         */
        fun markInstalled(skillId: String) {
            for (i in 0 until count) {
                val item = getItem(i) ?: continue
                if (item.skillId == skillId && item.installStatus != "INSTALLED") {
                    val updated = item.copy(installStatus = "INSTALLED")
                    remove(item)
                    insert(updated, i)
                    notifyDataSetChanged()
                    break
                }
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = getItem(position) ?: return view
            val title = view.findViewById<TextView>(R.id.tv_skill_title)
            val subtitle = view.findViewById<TextView>(R.id.tv_skill_subtitle)
            val installBtn = view.findViewById<Button>(R.id.btn_skill_install)
            title.text = "${item.skillName}  ·  v${item.version ?: "?"}"
            val status = item.installStatus ?: "UNKNOWN"
            subtitle.text = "${item.vendor ?: "?"}  ·  ${item.description ?: ""}  ·  $status"
            val installed = status == "INSTALLED"
            installBtn.isEnabled = !installed
            installBtn.text = if (installed) "已安装" else "安装"
            installBtn.setOnClickListener { onInstallClick(item) }
            return view
        }
    }
}
