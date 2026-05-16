// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw

import io.agents.pokeclaw.agent.DefaultAgentService
import io.agents.pokeclaw.agent.llm.LocalBackendHealth
import io.agents.pokeclaw.base.BaseApp
import io.agents.pokeclaw.channel.ChannelManager
import io.agents.pokeclaw.cloud.CloudNodeOrchestrator
import io.agents.pokeclaw.cloud.RetrofitDeviceCloudClient
import io.agents.pokeclaw.cloud.LocalAgentTaskExecutor
import io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore
import io.agents.pokeclaw.tool.ToolRegistry
import io.agents.pokeclaw.utils.AppLogStore
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import com.blankj.utilcode.util.NetworkUtils

/**
 * Application entry point
 */

val appViewModel: AppViewModel by lazy { ClawApplication.appViewModelInstance }
class ClawApplication : BaseApp() {

    companion object {
        private const val TAG = "ClawApplication"
        lateinit var instance: ClawApplication
            private set
        lateinit var appViewModelInstance: AppViewModel

        /** 云端执行节点编排器（懒加载，首次使用时初始化）。 */
        lateinit var cloudOrchestrator: CloudNodeOrchestrator
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogStore.init(this)
        XLog.setDEBUG(BuildConfig.DEBUG)
        registerNetworkCallback()
        appViewModelInstance = getAppViewModelProvider()[AppViewModel::class.java]
        KVUtils.init(this)
        LocalBackendHealth.recoverPendingGpuCrashIfNeeded()
        ToolRegistry.getInstance().registerAllTools(ToolRegistry.DeviceType.MOBILE)
        io.agents.pokeclaw.agent.skill.SkillRegistry.loadBuiltInSkills()
        io.agents.pokeclaw.agent.PlaybookManager.loadAll(this)
        XLog.e(TAG, "ClawApplication initialized, tools registered: ${ToolRegistry.getInstance().getAllTools().size}")

        // 初始化云端执行节点（默认关闭，由设置开关控制）
        initCloudNode()

        // Write network logs to file (set to true when debugging)
        DefaultAgentService.FILE_LOGGING_ENABLED = BuildConfig.DEBUG
        DefaultAgentService.FILE_LOGGING_CACHE_DIR = cacheDir

        // Lightweight initialization (main thread)
        appViewModelInstance.initCommon()
        Thread({
            try {
                android.util.Log.e("POKECLAW_INIT", "app-async-init thread STARTED")
                val hasConfig = KVUtils.hasLlmConfig()
                android.util.Log.e("POKECLAW_INIT", "app-async-init: hasLlmConfig=$hasConfig, canDrawOverlays=${android.provider.Settings.canDrawOverlays(instance)}")
                if (hasConfig) {
                    appViewModelInstance.initAgent()
                    appViewModelInstance.afterInit()
                }
            } catch (e: Exception) {
                android.util.Log.e("POKECLAW_INIT", "app-async-init CRASHED: ${e.message}", e)
            }
        }, "app-async-init").start()
    }

    private var networkListener: NetworkUtils.OnNetworkStatusChangedListener? = null

    /**
     * Listen for network recovery and automatically re-initialize channels.
     * Fixes channel initialization failures when booting with no network, and reconnects channels after network outages.
     */
    private fun registerNetworkCallback() {
        networkListener = object : NetworkUtils.OnNetworkStatusChangedListener {
            override fun onConnected(networkType: NetworkUtils.NetworkType?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (KVUtils.hasLlmConfig()) {
                        XLog.i(TAG, "Network recovered (${networkType?.name}), checking and reconnecting dropped channels")
                        ChannelManager.reconnectIfNeeded()
                    }
                }, 2000)
            }

            override fun onDisconnected() {
                XLog.w(TAG, "Network disconnected")
            }
        }
        NetworkUtils.registerNetworkStatusChangedListener(networkListener)
    }

    /**
     * 初始化云端执行节点编排器。
     * 根据用户设置决定是否启动（默认关闭）。
     */
    private fun initCloudNode() {
        val baseUrl = KVUtils.getString("cloud_base_url") ?: "http://192.168.250.3:8080"
        val enabled = KVUtils.getBoolean("cloud_enabled", false)

        val tokenStore = AndroidKeystoreCloudDeviceTokenStore(this)
        val offlineQueue = io.agents.pokeclaw.cloud.CloudEventQueue(this)
        val cloudClient = RetrofitDeviceCloudClient.create(
            baseUrl = baseUrl,
            tokenStore = tokenStore,
            offlineQueue = offlineQueue
        )
        val taskExecutor = LocalAgentTaskExecutor()

        cloudOrchestrator = CloudNodeOrchestrator(
            context = this,
            cloudClient = cloudClient,
            tokenStore = tokenStore,
            offlineQueue = offlineQueue,
            taskExecutor = taskExecutor
        )

        XLog.i(TAG, "initCloudNode: 云端节点已初始化，enabled=$enabled, baseUrl=$baseUrl")

        if (enabled) {
            cloudOrchestrator.start()
            XLog.i(TAG, "initCloudNode: 云端节点已启动")
        }
    }

}
