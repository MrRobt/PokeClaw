// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.UUID

/**
 * 端云节点注册管理器（端侧 + UI 友好）。
 *
 * 职责：
 *  - 提供 Settings UI 的「云端执行节点」注册入口
 *  - 调用 [CloudNodeOrchestrator] 复用 [TokenManager] 完成设备注册
 *  - 注册成功后展示 deviceId / 注册时间 / 「下一个任务」按钮
 *  - 注册失败：返回可读错误，UI Toast 提示 + Settings 红色高亮
 *
 * 状态：
 *  - IDLE → ENROLLING → ENROLLED / FAILED
 *
 * R2 US-B-3T-002-ENROLL-MULTI:
 *  - 多后端枚举 (LOCAL / STAGING / PRODUCTION)
 *  - 每个后端独立的 deviceId + deviceToken + refreshToken (MMKV key: claw_enroll_{env}_*)
 *  - 切换后端不丢失其他后端的 token (仅切换 active 指针)
 */
class ClawNodeEnrollmentManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PokeClaw/Enrollment"
        private const val KEY_ENROLLED_AT = "cloud_enrolled_at"
        private const val KEY_LAST_ENROLL_ERROR = "cloud_last_enroll_error"
        private const val KEY_LAST_ENROLL_DEVICE_ID = "cloud_enrolled_device_id"
        private const val KEY_ACTIVE_ENV = "claw_enroll_active_env"

        // Per-env MMKV keys (R2 US-B-3T-002-ENROLL-MULTI)
        private const val PREFIX_CLAW_ENROLL = "claw_enroll_"
        private const val SUFFIX_DEVICE_ID = "_device_id"
        private const val SUFFIX_ENROLLED_AT = "_enrolled_at"
        private const val SUFFIX_ERROR = "_error"

        val DEFAULT_ENV_BACKEND_URLS: Map<BackendEnv, String> = mapOf(
            BackendEnv.LOCAL to "http://10.0.2.2:8080",
            BackendEnv.STAGING to "https://staging.claw.agents.io",
            BackendEnv.PRODUCTION to "https://claw.agents.io",
        )

        @Volatile
        private var instance: ClawNodeEnrollmentManager? = null

        fun getInstance(context: Context): ClawNodeEnrollmentManager {
            return instance ?: synchronized(this) {
                instance ?: ClawNodeEnrollmentManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** 注册状态。 */
    enum class State {
        IDLE,
        ENROLLING,
        ENROLLED,
        FAILED,
    }

    /** Multi-backend environments (R2). */
    enum class BackendEnv {
        LOCAL,
        STAGING,
        PRODUCTION;

        companion object {
            fun fromStorageOrDefault(): BackendEnv {
                val name = KVUtils.getString(KEY_ACTIVE_ENV, BackendEnv.PRODUCTION.name)
                return runCatching { valueOf(name) }.getOrDefault(PRODUCTION)
            }
        }
    }

    private val appContext = context.applicationContext

    /** 当前状态（仅反映 active env 的注册结果）。 */
    fun getState(): State {
        val env = activeBackend()
        val enrolledAt = KVUtils.getLong(envKey(env, SUFFIX_ENROLLED_AT), 0L)
        val lastError = KVUtils.getString(envKey(env, SUFFIX_ERROR))
        return when {
            enrolledAt > 0L && lastError.isNullOrBlank() -> State.ENROLLED
            lastError?.isNotEmpty() == true -> State.FAILED
            else -> State.IDLE
        }
    }

    /** 已注册 deviceId（active env）。 */
    fun getDeviceId(): String? = KVUtils.getString(envKey(activeBackend(), SUFFIX_DEVICE_ID))
        ?: KVUtils.getString(KEY_LAST_ENROLL_DEVICE_ID) // legacy fallback

    /** 上次注册时间（毫秒，active env）。 */
    fun getEnrolledAtMillis(): Long = KVUtils.getLong(envKey(activeBackend(), SUFFIX_ENROLLED_AT), 0L)

    /** 上次错误（可空，active env）。 */
    fun getLastError(): String? = KVUtils.getString(envKey(activeBackend(), SUFFIX_ERROR))

    /** Current active backend environment. */
    fun activeBackend(): BackendEnv = BackendEnv.fromStorageOrDefault()

    /** Switch the active backend without losing other environments' tokens. */
    fun setActiveBackend(env: BackendEnv) {
        KVUtils.putString(KEY_ACTIVE_ENV, env.name)
        XLog.i(TAG, "enroll: switched active backend to $env (other env tokens preserved)")
    }

    /** Token snapshot for a given env (used by the UI to render the right column). */
    fun snapshotForEnv(env: BackendEnv): EnvSnapshot {
        return EnvSnapshot(
            env = env,
            deviceId = KVUtils.getString(envKey(env, SUFFIX_DEVICE_ID)),
            enrolledAt = KVUtils.getLong(envKey(env, SUFFIX_ENROLLED_AT), 0L),
            lastError = KVUtils.getString(envKey(env, SUFFIX_ERROR)),
        )
    }

    private fun envKey(env: BackendEnv, suffix: String): String = PREFIX_CLAW_ENROLL + env.name.lowercase() + suffix

    /**
     * 执行注册流程：
     *  - 若 active env 已注册：直接返回成功
     *  - 否则调用 TokenManager 生成 deviceId（如无）+ 触发 CloudNodeOrchestrator.registerDevice()
     *  - 注册成功后写 env-scoped MMKV (claw_enroll_{env}_*)，同时保留 legacy keys
     *    以便旧版 UI 仍能展示 deviceId
     */
    suspend fun enroll(): EnrollmentResult {
        val env = activeBackend()
        val current = getState()
        if (current == State.ENROLLING) {
            return EnrollmentResult.AlreadyEnrolling
        }
        XLog.i(TAG, "enroll: env=$env state=$current → ENROLLING")

        // 确保 deviceId 已生成 (TokenManager 仍是单一来源)
        var deviceId = TokenManager.getInstance(appContext).getDeviceId()
        if (deviceId.isNullOrBlank()) {
            deviceId = "pokeclaw-${UUID.randomUUID()}"
            TokenManager.getInstance(appContext).saveDeviceId(deviceId)
            XLog.i(TAG, "enroll: 生成新 deviceId=$deviceId")
        }

        // 调用云端注册：通过 RetrofitDeviceCloudClient（CloudNodeOrchestrator 复用同一 client）
        val result = try {
            val baseUrl = KVUtils.getString("cloud_base_url")
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_ENV_BACKEND_URLS[env]!!
            val tokenStore = io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore(appContext)
            val offlineQueue: io.agents.pokeclaw.cloud.OfflineResultQueue =
                io.agents.pokeclaw.cloud.CloudEventQueueAdapter(io.agents.pokeclaw.cloud.CloudEventQueue(appContext))
            val client = io.agents.pokeclaw.cloud.RetrofitDeviceCloudClient.create(
                baseUrl = baseUrl,
                tokenStore = tokenStore,
                offlineQueue = offlineQueue,
            )
            client.register(
                io.agents.pokeclaw.cloud.model.DeviceRegisterRequest(
                    deviceId = deviceId,
                    deviceName = android.os.Build.MODEL,
                    deviceModel = android.os.Build.MODEL,
                    androidVersion = android.os.Build.VERSION.RELEASE,
                    appVersion = io.agents.pokeclaw.BuildConfig.VERSION_NAME,
                )
            )
        } catch (e: Exception) {
            XLog.e(TAG, "enroll: 调用云端注册异常 env=$env", e)
            val err = (e.message ?: "unknown").take(200)
            KVUtils.putString(envKey(env, SUFFIX_ERROR), err)
            return EnrollmentResult.Failure(e.message ?: "unknown")
        }

        return if (result.isSuccess) {
            val now = System.currentTimeMillis()
            // Env-scoped storage
            KVUtils.putLong(envKey(env, SUFFIX_ENROLLED_AT), now)
            KVUtils.putString(envKey(env, SUFFIX_DEVICE_ID), deviceId)
            KVUtils.putString(envKey(env, SUFFIX_ERROR), "")
            // Legacy keys (for backward-compat with the old UI column)
            KVUtils.putLong(KEY_ENROLLED_AT, now)
            KVUtils.putString(KEY_LAST_ENROLL_DEVICE_ID, deviceId)
            KVUtils.putString(KEY_LAST_ENROLL_ERROR, "")
            CloudStatusHelper.reportHeartbeatSuccess()
            XLog.i(TAG, "enroll env=$env status=success deviceId=$deviceId")
            EnrollmentResult.Success(deviceId, now)
        } else {
            val err = result.exceptionOrNull()?.message ?: "unknown"
            KVUtils.putString(envKey(env, SUFFIX_ERROR), err.take(200))
            XLog.w(TAG, "enroll env=$env status=failure deviceId=$deviceId error=$err")
            EnrollmentResult.Failure(err)
        }
    }

    /**
     * 清除 active env 的注册信息（重置为 IDLE）。
     * 其他 env 的 token 不动。
     */
    fun reset() {
        val env = activeBackend()
        KVUtils.remove(envKey(env, SUFFIX_ENROLLED_AT))
        KVUtils.remove(envKey(env, SUFFIX_ERROR))
        KVUtils.remove(envKey(env, SUFFIX_DEVICE_ID))
        KVUtils.remove(KEY_ENROLLED_AT)
        KVUtils.remove(KEY_LAST_ENROLL_ERROR)
        KVUtils.remove(KEY_LAST_ENROLL_DEVICE_ID)
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.DISABLED)
        XLog.i(TAG, "reset: env=$env 注册信息已清除 (其他 env 保留)")
    }

    data class EnvSnapshot(
        val env: BackendEnv,
        val deviceId: String?,
        val enrolledAt: Long,
        val lastError: String?,
    ) {
        val isEnrolled: Boolean get() = enrolledAt > 0L && (lastError.isNullOrBlank())
    }

    /**
     * 注册结果。
     */
    sealed class EnrollmentResult {
        data class Success(val deviceId: String, val enrolledAtMillis: Long) : EnrollmentResult()
        data class Failure(val error: String) : EnrollmentResult()
        object AlreadyEnrolling : EnrollmentResult()
    }
}
