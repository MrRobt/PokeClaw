// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw

import com.blankj.utilcode.util.NetworkUtils
import io.agents.pokeclaw.agent.DefaultAgentService
import io.agents.pokeclaw.agent.llm.LocalBackendHealth
import io.agents.pokeclaw.base.BaseApp
import io.agents.pokeclaw.channel.ChannelManager
import io.agents.pokeclaw.cloud.CloudNodeOrchestrator
import io.agents.pokeclaw.cloud.LocalAgentTaskExecutor
import io.agents.pokeclaw.cloud.RetrofitDeviceCloudClient
import io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore
import io.agents.pokeclaw.tool.ToolRegistry
import io.agents.pokeclaw.utils.AppLogStore
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application entry point.
 */
val appViewModel: AppViewModel by lazy { ClawApplication.appViewModelInstance }

class ClawApplication : BaseApp() {

    companion object {
        private const val TAG = "ClawApplication"

        lateinit var instance: ClawApplication
            private set

        lateinit var appViewModelInstance: AppViewModel
            private set

        private val runtimeBootstrapStarted = AtomicBoolean(false)
        private val runtimeBootstrapReady = CountDownLatch(1)

        lateinit var cloudOrchestrator: CloudNodeOrchestrator
            private set

        @JvmStatic
        fun awaitRuntimeBootstrapReady() {
            if (!::instance.isInitialized) return
            instance.startRuntimeBootstrap()
            try {
                runtimeBootstrapReady.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private var networkListener: NetworkUtils.OnNetworkStatusChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        val startupAt = android.os.SystemClock.elapsedRealtime()
        fun logStartupStep(step: String) {
            android.util.Log.i(
                "POKECLAW_INIT",
                "app-core $step +${android.os.SystemClock.elapsedRealtime() - startupAt}ms"
            )
        }

        instance = this
        logStartupStep("instance")
        AppLogStore.init(this)
        XLog.setDEBUG(BuildConfig.DEBUG)
        logStartupStep("logging")
        KVUtils.init(this)
        logStartupStep("kv")
        appViewModelInstance = getAppViewModelProvider()[AppViewModel::class.java]
        logStartupStep("viewmodel")

        DefaultAgentService.FILE_LOGGING_ENABLED = BuildConfig.DEBUG
        DefaultAgentService.FILE_LOGGING_CACHE_DIR = cacheDir

        appViewModelInstance.initCommon()
        logStartupStep("common")
        io.agents.pokeclaw.agent.skill.SkillRegistry.registerBuiltInSkillDefinitions()
        logStartupStep("skills")
        XLog.i(TAG, "ClawApplication core initialized")
        startRuntimeBootstrap()
        logStartupStep("runtime-started")
    }

    private fun startRuntimeBootstrap() {
        if (!runtimeBootstrapStarted.compareAndSet(false, true)) return
        Thread({
            try {
                android.util.Log.i("POKECLAW_INIT", "app-runtime-bootstrap thread STARTED")
                LocalBackendHealth.recoverPendingGpuCrashIfNeeded()
                ToolRegistry.getInstance().registerAllTools(ToolRegistry.DeviceType.MOBILE)
                io.agents.pokeclaw.agent.skill.SkillRegistry.loadRuntimeStats()
                io.agents.pokeclaw.agent.PlaybookManager.loadAll(this)
                XLog.i(
                    TAG,
                    "Runtime bootstrap initialized, tools registered: ${ToolRegistry.getInstance().getAllTools().size}"
                )

                initCloudNode()

                val hasConfig = KVUtils.hasLlmConfig()
                android.util.Log.i(
                    "POKECLAW_INIT",
                    "app-runtime-bootstrap: hasLlmConfig=$hasConfig, " +
                        "canDrawOverlays=${android.provider.Settings.canDrawOverlays(instance)}"
                )
                if (hasConfig) {
                    appViewModelInstance.initAgent()
                    appViewModelInstance.afterInit()
                }
                registerNetworkCallback()
            } catch (e: Exception) {
                android.util.Log.e("POKECLAW_INIT", "app-runtime-bootstrap CRASHED: ${e.message}", e)
            } finally {
                runtimeBootstrapReady.countDown()
            }
        }, "app-runtime-bootstrap").start()
    }

    /**
     * Listen for network recovery and automatically re-initialize channels.
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
     * Initialize the cloud execution node. It is disabled by default and controlled by settings.
     */
    private fun initCloudNode() {
        val baseUrl = KVUtils.getString("cloud_base_url")
            ?.takeIf { it.isNotBlank() }
            ?: "http://192.168.250.3:8080"
        val enabled = KVUtils.getBoolean("cloud_enabled", false)

        val tokenStore = AndroidKeystoreCloudDeviceTokenStore(this)
        val offlineQueue: io.agents.pokeclaw.cloud.OfflineResultQueue =
            io.agents.pokeclaw.cloud.CloudEventQueueAdapter(
                io.agents.pokeclaw.cloud.CloudEventQueue(this)
            )
        val heartbeatManager = io.agents.pokeclaw.cloud.CloudHeartbeatManager.getInstance(this)
        val cloudClient = RetrofitDeviceCloudClient.create(
            baseUrl = baseUrl,
            tokenStore = tokenStore,
            offlineQueue = offlineQueue,
            hmacTimeOffsetProvider = { heartbeatManager.getHmacTimeOffsetMillis() },
        )
        val taskExecutor = LocalAgentTaskExecutor()

        cloudOrchestrator = CloudNodeOrchestrator(
            context = this,
            cloudClient = cloudClient,
            tokenStore = tokenStore,
            offlineQueue = offlineQueue,
            taskExecutor = taskExecutor
        )

        XLog.i(TAG, "initCloudNode: cloud node initialized, enabled=$enabled, baseUrl=$baseUrl")

        if (enabled) {
            cloudOrchestrator.start()
            XLog.i(TAG, "initCloudNode: cloud node started")
        }
    }
}
