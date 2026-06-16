package io.agents.pokeclaw.cloud.api

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.model.ApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

class CloudDeviceApiContractTest {

    @Test
    fun `设备端接口路径必须对齐后端契约`() {
        val methods = CloudDeviceApi::class.java.declaredMethods.associateBy { it.name }

        assertEquals("/api/claw-device/register", methods.getValue("register").getAnnotation(POST::class.java).value)
        assertEquals("/api/claw-device/heartbeat", methods.getValue("heartbeat").getAnnotation(POST::class.java).value)
        assertEquals("/api/claw-device/devices/{deviceId}/pending-tasks", methods.getValue("getPendingTasks").getAnnotation(GET::class.java).value)
        assertEquals("/api/claw-device/tasks/{taskUuid}/result", methods.getValue("submitTaskResult").getAnnotation(POST::class.java).value)
        assertEquals("/api/claw-device/token/refresh", methods.getValue("refreshDeviceToken").getAnnotation(POST::class.java).value)
    }

    @Test
    fun `云端地址规范化会补齐结尾斜杠`() {
        assertEquals("http://192.168.250.3:8080/", CloudDeviceApiFactory.normalizeBaseUrl("http://192.168.250.3:8080"))
        assertEquals("https://dyq.example.com/api/", CloudDeviceApiFactory.normalizeBaseUrl("https://dyq.example.com/api/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `云端地址必须是明确的 http 或 https 地址`() {
        CloudDeviceApiFactory.normalizeBaseUrl("192.168.250.3:8080")
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
}
