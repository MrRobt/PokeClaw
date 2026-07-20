// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端侧电量守卫（T49）：任务派发前置检查电量，低于安全阈值拒绝执行并上报 LOW_BATTERY。
//
// 决策逻辑与 Android 读取分离：evaluate() 为纯函数便于 JVM 单测；readStatus() 是
// 只在运行时调用的薄封装（依赖 BatteryManager）。

package io.agents.pokeclaw.agent.safety

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** 电量快照。percent 取值 0..100；充电时视为安全。 */
data class BatteryStatus(
    val percent: Int,
    val isCharging: Boolean,
)

/** 电量守卫决策结果。 */
data class BatteryDecision(
    val allowed: Boolean,
    val reason: String,
)

object BatteryGuard {

    /** 默认安全阈值：低于 15% 且未充电时拒绝执行。 */
    const val DEFAULT_MIN_PERCENT = 15

    /** 纯决策：是否允许在当前电量下执行任务。 */
    fun evaluate(status: BatteryStatus, minPercent: Int = DEFAULT_MIN_PERCENT): BatteryDecision {
        if (status.isCharging) {
            return BatteryDecision(allowed = true, reason = "charging")
        }
        if (status.percent >= minPercent) {
            return BatteryDecision(allowed = true, reason = "battery ${status.percent}% >= $minPercent%")
        }
        return BatteryDecision(
            allowed = false,
            reason = "battery ${status.percent}% < $minPercent% and not charging",
        )
    }

    /** 运行时读取当前电量状态。无法读取时按安全策略视为“允许”，避免误伤正常任务。 */
    fun readStatus(context: Context): BatteryStatus {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        if (intent == null) return BatteryStatus(percent = 100, isCharging = true)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val statusExtra = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = statusExtra == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusExtra == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStatus(percent = percent, isCharging = isCharging)
    }

    /** 便捷入口：运行时检查是否允许执行。 */
    fun allowExecution(context: Context, minPercent: Int = DEFAULT_MIN_PERCENT): BatteryDecision =
        evaluate(readStatus(context), minPercent)
}
