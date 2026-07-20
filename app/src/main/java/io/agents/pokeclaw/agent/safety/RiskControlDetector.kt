// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 端侧风控 / 验证码屏幕识别工具（T30c）。
//
// 目的：在 agent loop 每一轮拿到屏幕无障碍文本后，识别平台是否弹出了
// 验证码 / 安全验证 / 滑块 / 人机校验等风控拦截。一旦命中，调用方应立即
// 停止后续自动化操作、以 CloudTaskErrorCode.RISK_CONTROL 上报并请求人工介入。
//
// 设计为纯函数、无 Android 依赖，方便 JVM 单测覆盖。

package io.agents.pokeclaw.agent.safety

/**
 * 风控识别结果。
 *
 * @property matched        是否命中风控/验证特征
 * @property category       命中类别（CAPTCHA / SECURITY_VERIFY / SLIDER / HUMAN_VERIFY / LOGIN_REQUIRED）
 * @property matchedKeyword 命中的关键字（用于日志与 recoveryHint）
 * @property platform       命中的平台标识（douyin / tiktok / xiaohongshu / weibo / unknown）
 */
data class RiskSignal(
    val matched: Boolean,
    val category: String? = null,
    val matchedKeyword: String? = null,
    val platform: String? = null,
) {
    companion object {
        val NONE = RiskSignal(matched = false)
    }
}

object RiskControlDetector {

    // 通用风控/验证关键字（中英混合），按类别归组。
    private val captchaKeywords = listOf(
        "验证码", "输入验证码", "图形验证", "captcha", "verification code",
    )
    private val securityVerifyKeywords = listOf(
        "安全验证", "身份验证", "账号异常", "环境异常", "security verification",
        "verify it's you", "verify your identity", "unusual activity",
    )
    private val sliderKeywords = listOf(
        "拖动滑块", "拖动下方滑块", "滑动验证", "按住滑块", "拖动完成拼图",
        "slide to verify", "drag the slider",
    )
    private val humanVerifyKeywords = listOf(
        "人机验证", "我不是机器人", "完成验证", "点击完成验证", "请完成安全验证",
        "i'm not a robot", "are you human", "press and hold",
    )
    private val loginRequiredKeywords = listOf(
        "登录已过期", "请重新登录", "登录状态失效", "账号已在其他设备登录",
        "session expired", "please log in again", "sign in to continue",
    )

    // 平台弹窗包名 / 特征，用于给命中结果标注来源平台。
    private val platformByPackage = mapOf(
        "com.ss.android.ugc.aweme" to "douyin",
        "com.ss.android.ugc.aweme.lite" to "douyin",
        "com.zhiliaoapp.musically" to "tiktok",
        "com.ss.android.ugc.trill" to "tiktok",
        "com.xingin.xhs" to "xiaohongshu",
        "com.sina.weibo" to "weibo",
    )

    // 平台特有的弹窗文案特征（至少覆盖抖音/小红书/微博三平台）。
    private val platformPopupHints = mapOf(
        "douyin" to listOf("为了账号安全", "抖音安全中心", "验证后继续"),
        "xiaohongshu" to listOf("小红书安全中心", "为了你的账号安全", "滑动验证"),
        "weibo" to listOf("微博安全中心", "帐号存在异常", "点击按钮开始验证"),
        "tiktok" to listOf("verify to continue", "security check", "we've detected"),
    )

    private val categorizedKeywords: List<Pair<String, List<String>>> = listOf(
        "CAPTCHA" to captchaKeywords,
        "SLIDER" to sliderKeywords,
        "SECURITY_VERIFY" to securityVerifyKeywords,
        "HUMAN_VERIFY" to humanVerifyKeywords,
        "LOGIN_REQUIRED" to loginRequiredKeywords,
    )

    /**
     * 从屏幕无障碍文本 + 可选前台包名识别风控/验证。命中返回具体信号。
     */
    fun detect(screenText: String?, packageName: String? = null): RiskSignal {
        if (screenText.isNullOrBlank()) return RiskSignal.NONE
        val haystack = screenText.lowercase()
        val platform = packageName?.let { platformByPackage[it] }

        // 1) 通用关键字命中
        for ((category, keywords) in categorizedKeywords) {
            for (kw in keywords) {
                if (haystack.contains(kw.lowercase())) {
                    return RiskSignal(
                        matched = true,
                        category = category,
                        matchedKeyword = kw,
                        platform = platform ?: detectPlatformByHint(haystack),
                    )
                }
            }
        }

        // 2) 平台特有弹窗文案命中（关键字未覆盖时的兜底）
        val hintPlatform = detectPlatformByHint(haystack)
        if (hintPlatform != null) {
            val hit = platformPopupHints[hintPlatform]?.firstOrNull { haystack.contains(it.lowercase()) }
            if (hit != null) {
                return RiskSignal(
                    matched = true,
                    category = "SECURITY_VERIFY",
                    matchedKeyword = hit,
                    platform = hintPlatform,
                )
            }
        }
        return RiskSignal.NONE
    }

    /** T27b 便捷入口：当前屏幕是否为验证码/风控屏。 */
    fun isCaptchaScreen(screenText: String?, packageName: String? = null): Boolean =
        detect(screenText, packageName).matched

    /** 命中风控时给 recoveryHint 用的可读提示，携带 captcha/verification 关键字（T27b 期望）。 */
    fun recoveryHint(signal: RiskSignal): String {
        if (!signal.matched) return ""
        val platform = signal.platform?.let { "[$it] " } ?: ""
        return "${platform}detected captcha/verification screen (${signal.category}: ${signal.matchedKeyword}); 请人工完成登录验证"
    }

    private fun detectPlatformByHint(haystackLower: String): String? {
        for ((platform, hints) in platformPopupHints) {
            if (hints.any { haystackLower.contains(it.lowercase()) }) return platform
        }
        return null
    }
}
