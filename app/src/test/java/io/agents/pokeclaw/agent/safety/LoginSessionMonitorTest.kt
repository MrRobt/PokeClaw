package io.agents.pokeclaw.agent.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSessionMonitorTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `never logged in is expired`() {
        assertTrue(LoginSessionMonitor.isExpiredByTime(0L, now))
    }

    @Test
    fun `fresh login within ttl is valid`() {
        val last = now - (1 * 60 * 60 * 1000L) // 1h ago
        assertFalse(LoginSessionMonitor.isExpiredByTime(last, now))
    }

    @Test
    fun `login older than ttl is expired`() {
        val last = now - (13 * 60 * 60 * 1000L) // 13h ago > 12h TTL
        assertTrue(LoginSessionMonitor.isExpiredByTime(last, now))
    }

    @Test
    fun `logged out screen detected`() {
        assertTrue(LoginSessionMonitor.isLoggedOutScreen("登录已过期，请重新登录"))
        assertTrue(LoginSessionMonitor.isLoggedOutScreen("Your session expired. Please log in again."))
    }

    @Test
    fun `normal screen not logged out`() {
        assertFalse(LoginSessionMonitor.isLoggedOutScreen("首页 推荐 关注 我的"))
    }

    @Test
    fun `evaluate invalid when screen shows logged out even if time fresh`() {
        val last = now - 1000L
        val state = LoginSessionMonitor.evaluate(last, now, screenText = "请先登录")
        assertFalse(state.valid)
    }

    @Test
    fun `evaluate valid when fresh and normal screen`() {
        val last = now - 1000L
        val state = LoginSessionMonitor.evaluate(last, now, screenText = "推荐视频")
        assertTrue(state.valid)
    }
}
