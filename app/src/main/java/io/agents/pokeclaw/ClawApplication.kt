// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw

import android.content.IntentFilter
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.NetworkUtils
import io.agents.pokeclaw.agent.DefaultAgentService
import io.agents.pokeclaw.agent.llm.LocalBackendHealth
import io.agents.pokeclaw.base.BaseApp
import io.agents.pokeclaw.channel.ChannelManager
import io.agents.pokeclaw.cloud.AndroidDeviceInfoProvider
import io.agents.pokeclaw.cloud.CloudNodeOrchestrator
import io.agents.pokeclaw.cloud.LocalAgentTaskExecutor
import io.agents.pokeclaw.cloud.RetrofitDeviceCloudClient
import io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore
import io.agents.pokeclaw.receiver.MissedCallReceiver
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

        private val appViewModelLock = Any()
        private var appViewModelBacking: AppViewModel? = null

        val appViewModelInstance: AppViewModel
            get() {
                check(::instance.isInitialized) { "ClawApplication is not initialized" }
                return synchronized(appViewModelLock) {
                    appViewModelBacking
                        ?: instance.getAppViewModelProvider()[AppViewModel::class.java]
                            .also { appViewModelBacking = it }
                }
            }

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
    private val missedCallReceiver = MissedCallReceiver()

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
        XLog.setDEBUG(BuildConfig.DEBUG || BuildConfig.DEBUG_AUTOMATION_ENABLED)
        logStartupStep("logging")
        KVUtils.init(this)
        logStartupStep("kv")
        if (BuildConfig.MISSED_CALL_FOLLOWUP_ENABLED) {
            registerMissedCallReceiver()
            logStartupStep("missed-call-receiver")
        } else {
            logStartupStep("missed-call-disabled")
        }
        logStartupStep("viewmodel-deferred")

        logStartupStep("common-deferred")
        XLog.i(TAG, "ClawApplication core initialized")
        startRuntimeBootstrap()
        logStartupStep("runtime-started")
    }

    private fun registerMissedCallReceiver() {
        try {
            ContextCompat.registerReceiver(
                this,
                missedCallReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            XLog.i(TAG, "Missed-call dynamic receiver registered")
        } catch (e: Exception) {
            XLog.w(TAG, "Failed to register missed-call dynamic receiver", e)
        }
    }

    private fun startRuntimeBootstrap() {
        if (!runtimeBootstrapStarted.compareAndSet(false, true)) return
        Thread({
            try {
                android.util.Log.i("POKECLAW_INIT", "app-runtime-bootstrap thread STARTED")
                DefaultAgentService.FILE_LOGGING_ENABLED = BuildConfig.DEBUG || BuildConfig.DEBUG_AUTOMATION_ENABLED
                DefaultAgentService.FILE_LOGGING_CACHE_DIR = cacheDir
                LocalBackendHealth.recoverPendingGpuCrashIfNeeded()
                ToolRegistry.getInstance().registerAllTools(ToolRegistry.DeviceType.MOBILE)
                io.agents.pokeclaw.agent.skill.SkillRegistry.registerBuiltInSkillDefinitions()
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
        val deviceId = AndroidDeviceInfoProvider(this).loadOrGenerateDeviceId()
        heartbeatManager.initialize(cloudClient, deviceId)
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
            heartbeatManager.startHeartbeat()
            cloudOrchestrator.start()
            XLog.i(TAG, "initCloudNode: cloud node started, work heartbeat scheduled")
        } else {
            heartbeatManager.stopHeartbeat()
        }
    }
}
