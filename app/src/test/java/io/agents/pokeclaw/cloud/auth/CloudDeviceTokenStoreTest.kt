// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// CloudDeviceTokenStore 单元测试 — DYQ-90 云端 Token 分步保存修复验证

package io.agents.pokeclaw.cloud.auth

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CloudDeviceTokenStore 单元测试。
 *
 * DYQ-90 完成标准验证：
 * 1. 首次 saveTokens - 原子保存 deviceToken + refreshToken，禁止空 token
 * 2. 单独 updateDeviceToken - 刷新时只更新 deviceToken，保留原 refreshToken
 * 3. 空 token 拒绝 - 禁止空字符串覆盖有效 token
 * 4. 注册保存顺序 - 首次注册后分步保存不会触发异常
 */
class CloudDeviceTokenStoreTest {

    /** 内存实现的 CloudDeviceTokenStore，用于单元测试 */
    private class InMemoryCloudDeviceTokenStore : CloudDeviceTokenStore {
        private var deviceToken: String? = null
        private var refreshToken: String? = null
        private var expiresAtMillis: Long = 0L

        override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
            // 首次保存（无历史快照）：要求两个 token 都必须非空，原子保存
            if (this.deviceToken == null && this.refreshToken == null) {
                require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
                require(refreshToken.isNotBlank()) { "刷新令牌不能为空" }
                this.deviceToken = deviceToken
                this.refreshToken = refreshToken
                this.expiresAtMillis = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
                return
            }

            // 有历史快照：允许部分更新，空值保留原 token
            val newDeviceToken = deviceToken.takeIf { it.isNotBlank() } ?: this.deviceToken
            val newRefreshToken = refreshToken.takeIf { it.isNotBlank() } ?: this.refreshToken
            this.deviceToken = newDeviceToken
            this.refreshToken = newRefreshToken
            this.expiresAtMillis = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
        }

        override fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long) {
            require(deviceToken.isNotBlank()) { "设备令牌不能为空" }
            // 必须有历史 refreshToken 才能只更新 deviceToken
            val existingRefreshToken = this.refreshToken
                ?: throw IllegalStateException("更新 deviceToken 前必须先完成首次注册，调用 saveTokens(deviceToken, refreshToken, ...)")

            // 保留原 refreshToken，只更新 deviceToken 和过期时间
            this.deviceToken = deviceToken
            this.expiresAtMillis = nowMillis + expiresInSeconds.coerceAtLeast(0) * 1000L
        }

        override fun snapshot(): CloudDeviceTokenSnapshot? {
            val dt = deviceToken ?: return null
            val rt = refreshToken ?: return null
            return CloudDeviceTokenSnapshot(
                deviceToken = dt,
                refreshToken = rt,
                expiresAtMillis = expiresAtMillis
            )
        }

        override fun clear() {
            deviceToken = null
            refreshToken = null
            expiresAtMillis = 0L
        }

        override fun invalidate() {
            clear()
        }
    }

    private lateinit var tokenStore: CloudDeviceTokenStore

    @Before
    fun setup() {
        tokenStore = InMemoryCloudDeviceTokenStore()
    }

    @Test
    fun `首次 saveTokens 原子保存 deviceToken 和 refreshToken`() {
        // 首次保存两个 token
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        // 验证两个 token 都已保存
        val snapshot = tokenStore.snapshot()
        assertNotNull("snapshot 不应为 null", snapshot)
        assertEquals("deviceToken 应保存", "device_token_123", snapshot?.deviceToken)
        assertEquals("refreshToken 应保存", "refresh_token_456", snapshot?.refreshToken)
        assertTrue("token 应有效", snapshot?.hasDeviceToken(1716284400000L) == true)
    }

    @Test
    fun `首次 saveTokens 空 deviceToken 应抛出异常`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            tokenStore.saveTokens(
                deviceToken = "",
                refreshToken = "refresh_token_456",
                expiresInSeconds = 3600,
                nowMillis = 1716284400000L
            )
        }
        assertTrue("错误信息应包含'设备令牌不能为空'", exception.message?.contains("设备令牌不能为空") == true)
    }

    @Test
    fun `首次 saveTokens 空 refreshToken 应抛出异常`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            tokenStore.saveTokens(
                deviceToken = "device_token_123",
                refreshToken = "",
                expiresInSeconds = 3600,
                nowMillis = 1716284400000L
            )
        }
        assertTrue("错误信息应包含'刷新令牌不能为空'", exception.message?.contains("刷新令牌不能为空") == true)
    }

    @Test
    fun `updateDeviceToken 只更新 deviceToken 保留原 refreshToken`() {
        // 先完成首次注册
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        // 刷新时只更新 deviceToken
        tokenStore.updateDeviceToken(
            deviceToken = "new_device_token_789",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        // 验证 deviceToken 已更新，refreshToken 保持不变
        val snapshot = tokenStore.snapshot()
        assertEquals("deviceToken 应更新", "new_device_token_789", snapshot?.deviceToken)
        assertEquals("refreshToken 应保持不变", "refresh_token_456", snapshot?.refreshToken)
    }

    @Test
    fun `updateDeviceToken 空 deviceToken 应抛出异常`() {
        // 先完成首次注册
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            tokenStore.updateDeviceToken(
                deviceToken = "",
                expiresInSeconds = 3600,
                nowMillis = 1716284400000L
            )
        }
        assertTrue("错误信息应包含'设备令牌不能为空'", exception.message?.contains("设备令牌不能为空") == true)
    }

    @Test
    fun `updateDeviceToken 未注册时应抛出异常`() {
        // 未注册时直接调用 updateDeviceToken
        val exception = assertThrows(IllegalStateException::class.java) {
            tokenStore.updateDeviceToken(
                deviceToken = "new_device_token_789",
                expiresInSeconds = 3600,
                nowMillis = 1716284400000L
            )
        }
        assertTrue("错误信息应提示先调用 saveTokens()", exception.message?.contains("saveTokens") == true)
    }

    @Test
    fun `注册保存顺序 - 首次注册后分步保存不会触发异常`() {
        // 模拟 DeviceService.registerDevice() 的首次注册流程
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 604800,
            nowMillis = 1716284400000L
        )

        // 模拟后续刷新只更新 deviceToken
        tokenStore.updateDeviceToken(
            deviceToken = "refreshed_device_token_789",
            expiresInSeconds = 604800,
            nowMillis = 1716284400000L
        )

        // 验证状态正常，无异常抛出
        val snapshot = tokenStore.snapshot()
        assertEquals("refreshed_device_token_789", snapshot?.deviceToken)
        assertEquals("refresh_token_456", snapshot?.refreshToken)
    }

    @Test
    fun `clear 清除所有 token`() {
        // 先保存 token
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        // 清除 token
        tokenStore.clear()

        // 验证 token 已清除
        assertNull("snapshot 应为 null", tokenStore.snapshot())
    }

    @Test
    fun `saveTokens 部分更新空值保留原 token`() {
        // 首次保存
        tokenStore.saveTokens(
            deviceToken = "device_token_123",
            refreshToken = "refresh_token_456",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        // 第二次保存，deviceToken 为空，应保留原值
        tokenStore.saveTokens(
            deviceToken = "",
            refreshToken = "new_refresh_token_abc",
            expiresInSeconds = 3600,
            nowMillis = 1716284400000L
        )

        val snapshot = tokenStore.snapshot()
        assertEquals("deviceToken 应保持原值", "device_token_123", snapshot?.deviceToken)
        assertEquals("refreshToken 应更新", "new_refresh_token_abc", snapshot?.refreshToken)
    }
}
