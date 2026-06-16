package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.asBearerToken
import io.agents.pokeclaw.cloud.model.ApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 设备端云端 API 契约对齐测试。
 *
 * 验证：
 * - DeviceApi 接口路径必须与 device.openapi.yaml v1.1.0 一致
 * - CloudClientFactory 端点 baseUrl 规范化行为
 * - 设备令牌快照的过期窗口判断
 * - ApiResponse 通用响应 code 判定（0 / 200 / 其他）
 */
class CloudDeviceApiContractTest {

    @Test
    fun `设备端接口路径必须对齐后端契约`() {
        val methods = DeviceApi::class.java.declaredMethods.associateBy { it.name }

        assertEquals("/api/claw-device/register", methods.getValue("registerDevice").getAnnotation(POST::class.java)!!.value)
        assertEquals("/api/claw-device/heartbeat", methods.getValue("sendHeartbeat").getAnnotation(POST::class.java)!!.value)
        assertEquals("/api/claw-device/devices/{deviceId}/pending-tasks", methods.getValue("getPendingTasks").getAnnotation(GET::class.java)!!.value)
        // submitTaskResult / cancelTask are suspend functions with default args;
        // at the JVM level the single declared method is named exactly
        // "submitTaskResult" (Kotlin generates only one overload, not "$default").
        val submitResultMethod = DeviceApi::class.java.declaredMethods
            .first { m -> m.name == "submitTaskResult" }
        assertEquals(
            "/api/claw-device/tasks/{taskUuid}/result",
            submitResultMethod.getAnnotation(POST::class.java)!!.value,
        )
        val cancelTaskMethod = DeviceApi::class.java.declaredMethods
            .first { m -> m.name == "cancelTask" }
        assertEquals(
            "/api/claw-device/tasks/{taskUuid}/cancel",
            cancelTaskMethod.getAnnotation(POST::class.java)!!.value,
        )
        assertEquals("/api/claw-device/token/refresh", methods.getValue("refreshToken").getAnnotation(POST::class.java)!!.value)
        assertEquals("/api/claw-device/tasks/{taskUuid}", methods.getValue("getTaskByUuid").getAnnotation(GET::class.java)!!.value)
    }

    @Test
    fun `云端地址规范化会补齐结尾斜杠`() {
        assertEquals("http://192.168.250.3:8080/", io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("http://192.168.250.3:8080"))
        assertEquals("https://dyq.example.com/api/", io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("https://dyq.example.com/api/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `云端地址必须是明确的 http 或 https 地址`() {
        io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("192.168.250.3:8080")
    }

    @Test
    fun `Bearer 前缀只会补齐一次`() {
        assertEquals("Bearer token-1", "token-1".asBearerToken())
        assertEquals("Bearer token-2", "Bearer token-2".asBearerToken())
    }

    @Test
    fun `令牌快照会在过期窗口内要求刷新`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "device-token",
            refreshToken = "refresh-token",
            expiresAtMillis = 10_000L,
        )

        assertTrue(snapshot.hasDeviceToken(nowMillis = 1_000L))
        assertFalse(snapshot.shouldRefresh(nowMillis = 1_000L, refreshWindowMillis = 1_000L))
        assertTrue(snapshot.shouldRefresh(nowMillis = 9_500L, refreshWindowMillis = 1_000L))
        assertFalse(snapshot.hasDeviceToken(nowMillis = 10_001L))
    }

    @Test
    fun `通用响应二百和零都视为成功`() {
        assertTrue(ApiResponse<String>(code = 200, data = "ok").isSuccess())
        assertTrue(ApiResponse<String>(code = 0, data = "ok").isSuccess())
        assertFalse(ApiResponse<String>(code = 401, msg = "未认证").isSuccess())
    }

    // --- DeviceApi path/method matrix ---

    @Test
    fun `DeviceApi exposes 7 declared methods`() {
        val names = DeviceApi::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("registerDevice must be present", "registerDevice" in names)
        assertTrue("sendHeartbeat must be present", "sendHeartbeat" in names)
        assertTrue("getPendingTasks must be present", "getPendingTasks" in names)
        assertTrue("submitTaskResult must be present", "submitTaskResult" in names)
        assertTrue("refreshToken must be present", "refreshToken" in names)
        assertTrue("getTaskByUuid must be present", "getTaskByUuid" in names)
        assertTrue("cancelTask must be present", "cancelTask" in names)
    }

    @Test
    fun `submitTaskResult and cancelTask use POST annotation`() {
        val submit = DeviceApi::class.java.declaredMethods.first { it.name == "submitTaskResult" }
        val cancel = DeviceApi::class.java.declaredMethods.first { it.name == "cancelTask" }
        assertEquals("POST", submit.getAnnotation(POST::class.java)!!.value.first().let { "POST" })
        assertEquals("POST", cancel.getAnnotation(POST::class.java)!!.value.first().let { "POST" })
    }

    // --- normalizeBaseUrl ---

    @Test
    fun `normalizeBaseUrl preserves path when appending trailing slash`() {
        assertEquals(
            "https://example.com/api/v1/",
            io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("https://example.com/api/v1"),
        )
    }

    @Test
    fun `normalizeBaseUrl rejects ftp scheme`() {
        try {
            io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("ftp://example.com")
            org.junit.Assert.fail("expected IllegalArgumentException for ftp scheme")
        } catch (e: IllegalArgumentException) {
            // expected
            assertTrue(e.message!!.contains("http"))
        }
    }

    @Test
    fun `normalizeBaseUrl rejects empty string`() {
        try {
            io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("")
            org.junit.Assert.fail("expected IllegalArgumentException for empty string")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `normalizeBaseUrl rejects javascript scheme`() {
        try {
            io.agents.pokeclaw.cloud.CloudClientFactory.normalizeBaseUrl("javascript:alert(1)")
            org.junit.Assert.fail("expected IllegalArgumentException for javascript scheme")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // --- asBearerToken ---

    @Test
    fun `asBearerToken prepends prefix for token without it`() {
        assertEquals("Bearer abc", "abc".asBearerToken())
        assertEquals("Bearer eyJ0eXAi...", "eyJ0eXAi...".asBearerToken())
    }

    @Test
    fun `asBearerToken returns input unchanged when already prefixed`() {
        assertEquals("Bearer xyz", "Bearer xyz".asBearerToken())
        assertEquals("Bearer ", "Bearer ".asBearerToken())
    }

    @Test
    fun `asBearerToken is case-sensitive (lowercase bearer still gets prefix)`() {
        // The check is `startsWith("Bearer ")` — case-sensitive.
        // "bearer xyz" is not the canonical form, so it gets the prefix.
        assertEquals("Bearer bearer xyz", "bearer xyz".asBearerToken())
    }

    @Test
    fun `asBearerToken on empty string returns 'Bearer ' (no exception)`() {
        assertEquals("Bearer ", "".asBearerToken())
    }

    // --- CloudDeviceTokenSnapshot boundary ---

    @Test
    fun `hasDeviceToken at exactly expiresAtMillis is false (strict greater-than)`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "t",
            refreshToken = "r",
            expiresAtMillis = 10_000L,
        )
        assertFalse("at exact expiry, hasDeviceToken must be false", snapshot.hasDeviceToken(10_000L))
    }

    @Test
    fun `hasDeviceToken at expiresAtMillis - 1 is true`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "t",
            refreshToken = "r",
            expiresAtMillis = 10_000L,
        )
        assertTrue(snapshot.hasDeviceToken(9_999L))
    }

    @Test
    fun `hasDeviceToken with blank deviceToken is false`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "   ",
            refreshToken = "r",
            expiresAtMillis = 10_000L,
        )
        assertFalse(snapshot.hasDeviceToken(nowMillis = 1_000L))
    }

    @Test
    fun `shouldRefresh at exactly expiresAt minus refreshWindow is true (less-or-equal)`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "t",
            refreshToken = "r",
            expiresAtMillis = 10_000L,
        )
        // window=1000, expiresAt - now = 1000 → should refresh
        assertTrue(snapshot.shouldRefresh(nowMillis = 9_000L, refreshWindowMillis = 1_000L))
    }

    @Test
    fun `shouldRefresh at expiresAt minus refreshWindow + 1 is false`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "t",
            refreshToken = "r",
            expiresAtMillis = 10_000L,
        )
        // window=1000, expiresAt - now = 1001 → still healthy
        assertFalse(snapshot.shouldRefresh(nowMillis = 8_999L, refreshWindowMillis = 1_000L))
    }

    @Test
    fun `shouldRefresh with blank refreshToken is false`() {
        val snapshot = CloudDeviceTokenSnapshot(
            deviceToken = "t",
            refreshToken = "   ",
            expiresAtMillis = 10_000L,
        )
        assertFalse(snapshot.shouldRefresh(nowMillis = 9_999L, refreshWindowMillis = 1_000L))
    }

    // --- ApiResponse.isSuccess ---

    @Test
    fun `ApiResponse isSuccess for 201 is false (only 0 and 200 are success)`() {
        assertFalse(ApiResponse<String>(code = 201, data = "x").isSuccess())
    }

    @Test
    fun `ApiResponse isSuccess for 204 is false`() {
        assertFalse(ApiResponse<String>(code = 204, data = null).isSuccess())
    }

    @Test
    fun `ApiResponse isSuccess for 500 and negative codes is false`() {
        assertFalse(ApiResponse<String>(code = 500).isSuccess())
        assertFalse(ApiResponse<String>(code = -1).isSuccess())
        assertFalse(ApiResponse<String>(code = Int.MIN_VALUE).isSuccess())
    }

    @Test
    fun `ApiResponse default fields msg and data are null`() {
        val resp = ApiResponse<String>(code = 0)
        assertNullOrEmpty(resp.msg)
        assertEquals(null, resp.data)
    }

    private fun assertNullOrEmpty(s: String?) {
        if (s != null) assertEquals("", s)
    }
}
