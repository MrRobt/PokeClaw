// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端侧登录态监测（T30b / T28b）：在执行需登录的任务前判断登录态是否失效，
// 失效时让调用方以 CloudTaskErrorCode.AUTH_REQUIRED 上报并触发 re-login 流程。
//
// 两路信号：
//  1) 时间维度：距上次成功登录超过 TTL 视为过期（session token 通常有寿命）。
//  2) 屏幕维度：当前屏出现“请重新登录/登录已过期”等文案，直接判定已登出。
// 纯函数实现，便于 JVM 单测。

package io.agents.pokeclaw.agent.safety

/** 登录态判定结果。 */
data class SessionState(
    val valid: Boolean,
    val reason: String,
)

object LoginSessionMonitor {

    /** 默认登录态寿命：12 小时。 */
    const val DEFAULT_TTL_MS = 12 * 60 * 60 * 1000L

    // 登出 / 登录态失效的屏幕文案信号（与 RiskControlDetector 的 LOGIN_REQUIRED 对齐并扩展）。
    private val loggedOutKeywords = listOf(
        "登录已过期", "请重新登录", "登录状态失效", "账号已在其他设备登录",
        "你尚未登录", "请先登录", "立即登录", "登录后查看",
        "session expired", "please log in again", "sign in to continue",
        "you are signed out", "log in to continue",
    )

    /** 时间维度：距上次成功登录是否超过 TTL。lastLoginEpochMs <= 0 表示从未登录。 */
    fun isExpiredByTime(lastLoginEpochMs: Long, nowEpochMs: Long, ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        if (lastLoginEpochMs <= 0L) return true
        return nowEpochMs - lastLoginEpochMs >= ttlMs
    }

    /** 屏幕维度：当前屏是否显示已登出/需登录。 */
    fun isLoggedOutScreen(screenText: String?): Boolean {
        if (screenText.isNullOrBlank()) return false
        val haystack = screenText.lowercase()
        return loggedOutKeywords.any { haystack.contains(it.lowercase()) }
    }

    /**
     * 综合判定登录态是否仍然有效。任一信号命中失效则返回 invalid，
     * 调用方据此上报 AUTH_REQUIRED 并触发 re-login。
     */
    fun evaluate(
        lastLoginEpochMs: Long,
        nowEpochMs: Long,
        screenText: String? = null,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): SessionState {
        if (isLoggedOutScreen(screenText)) {
            return SessionState(valid = false, reason = "logged-out screen detected")
        }
        if (isExpiredByTime(lastLoginEpochMs, nowEpochMs, ttlMs)) {
            return SessionState(valid = false, reason = "session expired by TTL")
        }
        return SessionState(valid = true, reason = "session valid")
    }
}
