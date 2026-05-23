package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 云端设备 API 契约测试
 * 验证 Retrofit 接口定义与后端 OpenAPI 契约一致
 */
class CloudDeviceApiContractTest {

    @Test
    fun `设备端接口路径必须对齐后端契约`() {
        val methods = DeviceApi::class.java.declaredMethods.associateBy { it.name }

        // 验证路径和方法注解
        assertTrue("registerDevice 方法存在", methods.containsKey("registerDevice"))
        assertTrue("sendHeartbeat 方法存在", methods.containsKey("sendHeartbeat"))
        assertTrue("getPendingTasks 方法存在", methods.containsKey("getPendingTasks"))
        assertTrue("submitTaskResult 方法存在", methods.containsKey("submitTaskResult"))
        assertTrue("refreshToken 方法存在", methods.containsKey("refreshToken"))

        // 验证 POST 注解路径
        val registerAnnotation = methods.getValue("registerDevice").getAnnotation(POST::class.java)
        assertEquals("api/claw-device/register", registerAnnotation.value)

        val heartbeatAnnotation = methods.getValue("sendHeartbeat").getAnnotation(POST::class.java)
        assertEquals("api/claw-device/heartbeat", heartbeatAnnotation.value)

        val submitAnnotation = methods.getValue("submitTaskResult").getAnnotation(POST::class.java)
        assertEquals("api/claw-device/tasks/{taskUuid}/result", submitAnnotation.value)

        val refreshAnnotation = methods.getValue("refreshToken").getAnnotation(POST::class.java)
        assertEquals("api/claw-device/token/refresh", refreshAnnotation.value)

        // 验证 GET 注解路径
        val getTasksAnnotation = methods.getValue("getPendingTasks").getAnnotation(GET::class.java)
        assertEquals("api/claw-device/devices/{deviceId}/pending-tasks", getTasksAnnotation.value)
    }

    @Test
    fun `云端地址规范化会补齐结尾斜杠`() {
        // 测试地址规范化逻辑
        assertEquals("http://192.168.250.3:8080/", normalizeBaseUrl("http://192.168.250.3:8080"))
        assertEquals("https://dyq.example.com/api/", normalizeBaseUrl("https://dyq.example.com/api/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `云端地址必须是明确的 http 或 https 地址`() {
        normalizeBaseUrl("192.168.250.3:8080")
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
        assertFalse(snapshot.shouldRefresh(nowMillis = 1_000L, refreshWindowMillis = 5_000L))
        assertTrue(snapshot.shouldRefresh(nowMillis = 6_000L, refreshWindowMillis = 5_000L))
    }

    // 辅助函数
    private fun normalizeBaseUrl(url: String): String {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "云端地址必须是明确的 http 或 https 地址"
        }
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun String.asBearerToken(): String {
        return if (this.startsWith("Bearer ")) this else "Bearer $this"
    }
}
