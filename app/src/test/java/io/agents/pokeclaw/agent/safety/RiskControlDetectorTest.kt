package io.agents.pokeclaw.agent.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskControlDetectorTest {

    // --- 通用关键字：验证码 ---

    @Test
    fun `captcha keyword is detected`() {
        val signal = RiskControlDetector.detect("请输入验证码后继续操作")
        assertTrue(signal.matched)
        assertEquals("CAPTCHA", signal.category)
    }

    // --- 滑块 ---

    @Test
    fun `slider verification is detected`() {
        val signal = RiskControlDetector.detect("请按住滑块，拖动完成拼图")
        assertTrue(signal.matched)
        assertEquals("SLIDER", signal.category)
    }

    // --- 登录态失效 ---

    @Test
    fun `session expired maps to login required`() {
        val signal = RiskControlDetector.detect("登录已过期，请重新登录")
        assertTrue(signal.matched)
        assertEquals("LOGIN_REQUIRED", signal.category)
    }

    // --- 平台 1：抖音 ---

    @Test
    fun `douyin security popup is detected with platform tag`() {
        val screen = "为了账号安全，抖音安全中心需要验证后继续"
        val signal = RiskControlDetector.detect(screen, packageName = "com.ss.android.ugc.aweme")
        assertTrue(signal.matched)
        assertEquals("douyin", signal.platform)
    }

    // --- 平台 2：小红书 ---

    @Test
    fun `xiaohongshu security popup is detected`() {
        val screen = "小红书安全中心：为了你的账号安全，请完成滑动验证"
        val signal = RiskControlDetector.detect(screen, packageName = "com.xingin.xhs")
        assertTrue(signal.matched)
        assertEquals("xiaohongshu", signal.platform)
    }

    // --- 平台 3：微博 ---

    @Test
    fun `weibo security popup is detected`() {
        val screen = "微博安全中心：帐号存在异常，点击按钮开始验证"
        val signal = RiskControlDetector.detect(screen, packageName = "com.sina.weibo")
        assertTrue(signal.matched)
        assertEquals("weibo", signal.platform)
    }

    // --- 英文（TikTok） ---

    @Test
    fun `english verification screen is detected`() {
        val signal = RiskControlDetector.detect(
            "Security check: verify to continue",
            packageName = "com.zhiliaoapp.musically",
        )
        assertTrue(signal.matched)
        assertEquals("tiktok", signal.platform)
    }

    // --- isCaptchaScreen 便捷入口 ---

    @Test
    fun `isCaptchaScreen returns true on human verification`() {
        assertTrue(RiskControlDetector.isCaptchaScreen("请完成人机验证，我不是机器人"))
    }

    // --- recoveryHint 含 captcha/verification 关键字（T27b 期望） ---

    @Test
    fun `recoveryHint contains captcha keyword`() {
        val signal = RiskControlDetector.detect("请输入验证码")
        val hint = RiskControlDetector.recoveryHint(signal)
        assertTrue(hint.contains("captcha") || hint.contains("verification"))
        assertTrue(hint.contains("请人工完成登录验证"))
    }

    // --- 负例：正常屏幕不误报 ---

    @Test
    fun `normal feed screen does not trigger`() {
        val signal = RiskControlDetector.detect("推荐 关注 首页 点赞 3.2万 分享 评论 收藏")
        assertFalse(signal.matched)
        assertNull(signal.category)
    }

    @Test
    fun `blank screen returns none`() {
        assertFalse(RiskControlDetector.detect("").matched)
        assertFalse(RiskControlDetector.detect(null).matched)
    }
}
