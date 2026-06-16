// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// CloudStatusHelper 单测 — 模式状态机、reportState 幂等、心跳失败/恢复、statusLabel 文案。
// 通过反射清空 appContext 避免触发 OfflineFallbackManager 单例副作用。

package io.agents.pokeclaw.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudStatusHelperTest {

    @Before
    fun setUp() {
        // 重置单例到 DISABLED 初始值
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.DISABLED, null)
        // 清空 appContext：避免 reportState 触发 OfflineFallbackManager.getInstance(context) 时的 NPE
        clearAppContextViaReflection()
    }

    @After
    fun tearDown() {
        clearAppContextViaReflection()
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.DISABLED, null)
    }

    private fun clearAppContextViaReflection() {
        val field = CloudStatusHelper::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(CloudStatusHelper, null)
    }

    // ── Mode 枚举 ──

    @Test
    fun `Mode enum 集合稳定 - 6 个值`() {
        val values = CloudStatusHelper.Mode.values().toSet()
        assertEquals(
            setOf(
                CloudStatusHelper.Mode.DISABLED,
                CloudStatusHelper.Mode.CONNECTING,
                CloudStatusHelper.Mode.CLOUD_ONLINE,
                CloudStatusHelper.Mode.CLOUD_DEGRADED,
                CloudStatusHelper.Mode.CLOUD_OFFLINE,
                CloudStatusHelper.Mode.ERROR,
            ),
            values,
        )
    }

    @Test
    fun `Mode 各自 displayName 稳定 - UI 契约保护`() {
        assertEquals("Disabled", CloudStatusHelper.Mode.DISABLED.displayName)
        assertEquals("Connecting", CloudStatusHelper.Mode.CONNECTING.displayName)
        assertEquals("Cloud", CloudStatusHelper.Mode.CLOUD_ONLINE.displayName)
        assertEquals("Degraded", CloudStatusHelper.Mode.CLOUD_DEGRADED.displayName)
        assertEquals("Offline (local)", CloudStatusHelper.Mode.CLOUD_OFFLINE.displayName)
        assertEquals("Error", CloudStatusHelper.Mode.ERROR.displayName)
    }

    // ── reportState 基本语义 ──

    @Test
    fun `reportState 相同 mode + 相同 error - 幂等不更新时间戳`() {
        // 准备：先设到 ONLINE
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_ONLINE, null)
        val firstTransition = CloudStatusHelper.lastTransitionAt.value
        Thread.sleep(2)  // 保证若再次写入 timestamp 会不同

        // 再用相同 (mode, error) 推送
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_ONLINE, null)
        assertEquals("相同 mode+error 不应更新时间戳", firstTransition, CloudStatusHelper.lastTransitionAt.value)
    }

    @Test
    fun `reportState mode 变化 - lastTransitionAt 更新`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.DISABLED, null)
        val t0 = CloudStatusHelper.lastTransitionAt.value
        Thread.sleep(2)
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CONNECTING, null)
        assertTrue("mode 变化时 lastTransitionAt 应更新", CloudStatusHelper.lastTransitionAt.value > t0)
    }

    @Test
    fun `reportState error 变化 - 同一 mode 仍更新时间戳`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.ERROR, "first")
        val t0 = CloudStatusHelper.lastTransitionAt.value
        Thread.sleep(2)
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.ERROR, "second")
        assertTrue("error 变化时 lastTransitionAt 应更新", CloudStatusHelper.lastTransitionAt.value > t0)
        assertEquals("second", CloudStatusHelper.lastError.value)
    }

    @Test
    fun `reportState lastError - 写入并可读`() {
        assertNull(CloudStatusHelper.lastError.value)
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.ERROR, "register failed")
        assertEquals("register failed", CloudStatusHelper.lastError.value)
    }

    // ── reportHeartbeatFailure 阈值路由 ──

    @Test
    fun `reportHeartbeatFailure consecutive=0 - 视为 ONLINE（恢复）`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_DEGRADED, "stale")
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = 0, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_ONLINE, CloudStatusHelper.mode.value)
    }

    @Test
    fun `reportHeartbeatFailure consecutive 小于阈值 - DEGRADED 且带 error 描述`() {
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = 1, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_DEGRADED, CloudStatusHelper.mode.value)
        assertNotNull(CloudStatusHelper.lastError.value)
        assertTrue(CloudStatusHelper.lastError.value!!.contains("1/3"))
    }

    @Test
    fun `reportHeartbeatFailure consecutive 负数 - 也视为 ONLINE（防御）`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.ERROR, "old")
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = -1, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_ONLINE, CloudStatusHelper.mode.value)
    }

    @Test
    fun `reportHeartbeatFailure consecutive=threshold-1 - 仍 DEGRADED`() {
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = 2, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_DEGRADED, CloudStatusHelper.mode.value)
    }

    @Test
    fun `reportHeartbeatFailure consecutive=threshold - 触发 OFFLINE 状态（即使 appContext 为空也不崩）`() {
        // OFFLINE 路径会访问 fallback，但 appContext 已被反射清空 → onModeChanged 早 return
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = 3, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_OFFLINE, CloudStatusHelper.mode.value)
    }

    @Test
    fun `reportHeartbeatFailure consecutive 远大于阈值 - 仍 OFFLINE`() {
        CloudStatusHelper.reportHeartbeatFailure(consecutiveFailures = 10, threshold = 3)
        assertEquals(CloudStatusHelper.Mode.CLOUD_OFFLINE, CloudStatusHelper.mode.value)
    }

    // ── reportHeartbeatSuccess ──

    @Test
    fun `reportHeartbeatSuccess - 切回 ONLINE`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_DEGRADED, "x")
        CloudStatusHelper.reportHeartbeatSuccess()
        assertEquals(CloudStatusHelper.Mode.CLOUD_ONLINE, CloudStatusHelper.mode.value)
    }

    // ── statusLabel 文案 ──

    @Test
    fun `statusLabel - 各 mode 文案稳定 - UI 契约保护`() {
        val cases = listOf(
            CloudStatusHelper.Mode.DISABLED to "Cloud: Off",
            CloudStatusHelper.Mode.CONNECTING to "Cloud: Connecting…",
            CloudStatusHelper.Mode.CLOUD_ONLINE to "Cloud: Online",
            CloudStatusHelper.Mode.ERROR to "Cloud: Error (unknown)",
        )
        for ((mode, expected) in cases) {
            CloudStatusHelper.reportState(mode, null)
            // ERROR 文案当无 error 时用 "unknown"
            val actual = CloudStatusHelper.statusLabel()
            assertEquals("mode=$mode 文案不一致", expected, actual)
        }
    }

    @Test
    fun `statusLabel DEGRADED - 嵌入 lastError 描述`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_DEGRADED, "consecutive=2/3")
        assertTrue(CloudStatusHelper.statusLabel().contains("consecutive=2/3"))
    }

    @Test
    fun `statusLabel ERROR - 嵌入 lastError 描述`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.ERROR, "token expired")
        assertTrue(CloudStatusHelper.statusLabel().contains("token expired"))
    }

    @Test
    fun `statusLabel OFFLINE - 固定文案 包含 Gemma 4 字样`() {
        CloudStatusHelper.reportState(CloudStatusHelper.Mode.CLOUD_OFFLINE, null)
        val label = CloudStatusHelper.statusLabel()
        assertTrue("OFFLINE 状态应明确本地 Gemma 4，实际：$label", label.contains("Gemma"))
    }
}
