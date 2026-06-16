// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import android.os.Build
import io.agents.pokeclaw.BuildConfig
import io.agents.pokeclaw.cloud.model.NetworkType
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.UUID

/**
 * Abstraction over Android-specific device info sources (battery / network /
 * Build.* / KVUtils). Allows [CloudNodeOrchestrator] to be unit-tested on the
 * JVM without instantiating a real [Context].
 *
 * Production wiring uses [AndroidDeviceInfoProvider]; tests inject a fake.
 */
interface DeviceInfoProvider {
    /** Stable per-install device id. Persists across process restarts. */
    fun loadOrGenerateDeviceId(): String

    /** Battery level (0-100) and charging state. May be (null, null) on read failure. */
    fun readBatteryInfo(): Pair<Int?, Boolean?>

    /** Current network type, with safe OFFLINE fallback. */
    fun readNetworkType(): NetworkType

    /** Build.MODEL — used as deviceName/deviceModel for registration. */
    fun deviceModel(): String

    /** Build.VERSION.RELEASE — used as androidVersion. */
    fun androidVersion(): String

    /** BuildConfig.VERSION_NAME — used as appVersion. */
    fun appVersion(): String
}

/**
 * Default Android-backed implementation.
 *
 * - [loadOrGenerateDeviceId] persists into [KVUtils] under [KEY_DEVICE_ID].
 * - [readBatteryInfo] / [readNetworkType] wrap their calls in try-catch and
 *   fall back to safe defaults (nulls / OFFLINE) so the orchestrator never
 *   throws on transient Android framework errors.
 */
class AndroidDeviceInfoProvider(
    private val context: Context,
) : DeviceInfoProvider {

    override fun loadOrGenerateDeviceId(): String {
        val existing = KVUtils.getString(KEY_DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val newId = "pokeclaw-${UUID.randomUUID()}"
        KVUtils.putString(KEY_DEVICE_ID, newId)
        XLog.i(TAG, "loadOrGenerateDeviceId: 生成新设备编号 $newId")
        return newId
    }

    override fun readBatteryInfo(): Pair<Int?, Boolean?> {
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

    override fun readNetworkType(): NetworkType {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork
                val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                when {
                    network == null || capabilities == null -> NetworkType.OFFLINE
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    else -> NetworkType.OFFLINE
                }
            } else {
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

    override fun deviceModel(): String = Build.MODEL
    override fun androidVersion(): String = Build.VERSION.RELEASE
    override fun appVersion(): String = BuildConfig.VERSION_NAME

    companion object {
        private const val TAG = "PokeClaw/DeviceInfoProvider"
        const val KEY_DEVICE_ID = "cloud_device_id"
    }
}
