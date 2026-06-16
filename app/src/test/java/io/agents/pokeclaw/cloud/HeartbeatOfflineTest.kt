// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-036：心跳 networkType=offline 显式上报测试。
// 覆盖：枚举映射 / 序列化 / 离线兜底 / 心跳请求带 offline 字串。

package io.agents.pokeclaw.cloud

import com.google.gson.Gson
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse
import io.agents.pokeclaw.cloud.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.0 心跳 networkType=offline 测试套件。
 *
 * 覆盖：
 * 1. NetworkType 枚举 value 与 OpenAPI 字段对齐（wifi/cellular/offline）
 * 2. DeviceHeartbeatRequest 序列化后 networkType 字段存在且为小写
 * 3. 无网络时显式发送 networkType="offline"（不省略字段）
 * 4. 三种状态（wifi / cellular / offline）通过 Gson 序列化均带正确字串
 */
class HeartbeatOfflineTest {

    @Test
    fun `NetworkType 枚举 value 与 device_openapi yaml 一致`() {
        assertEquals("wifi", NetworkType.WIFI.value)
        assertEquals("cellular", NetworkType.CELLULAR.value)
        assertEquals("offline", NetworkType.OFFLINE.value)
    }

    @Test
    fun `DeviceHeartbeatRequest 序列化 networkType 字段存在且小写`() {
        val request = DeviceHeartbeatRequest(
            batteryLevel = 50,
            isCharging = false,
            networkType = NetworkType.OFFLINE.value,
        )
        val json = Gson().toJson(request)
        assertTrue("JSON 必须含 networkType 字段：$json", json.contains("\"networkType\""))
        assertTrue("networkType 字段值必须是 offline：$json", json.contains("\"offline\""))
    }

    @Test
    fun `无网络时 - 心跳请求必须带 networkType offline 不省略字段`() {
        // 模拟：getNetworkType() 返回 OFFLINE.value="offline"（不为 null）
        // 验证：构造的请求里 networkType 不为 null，且 value 是 "offline"
        val networkType = NetworkType.OFFLINE.value
        assertNotNull("OFFLINE 不应序列化为 null", networkType)
        assertEquals("offline", networkType)

        val request = DeviceHeartbeatRequest(
            batteryLevel = 30,
            isCharging = null,
            networkType = networkType,
        )
        // 字段显式存在，序列化时进入 JSON
        assertEquals("offline", request.networkType)
    }

    @Test
    fun `三种 NetworkType 状态 - Gson 序列化均带正确字串`() {
        val gson = Gson()
        for (type in NetworkType.entries) {
            val json = gson.toJson(DeviceHeartbeatRequest(networkType = type.value))
            assertTrue(
                "$type 序列化必须含 \"${type.value}\"",
                json.contains("\"${type.value}\""),
            )
        }
    }

    // --- NetworkType enum structure ---

    @Test
    fun `NetworkType entries 数量为 3`() {
        assertEquals(3, NetworkType.entries.size)
    }

    @Test
    fun `NetworkType name 返回枚举名 WIFI CELLULAR OFFLINE`() {
        assertEquals("WIFI", NetworkType.WIFI.name)
        assertEquals("CELLULAR", NetworkType.CELLULAR.name)
        assertEquals("OFFLINE", NetworkType.OFFLINE.name)
    }

    // --- DeviceHeartbeatRequest 默认值与边界 ---

    @Test
    fun `DeviceHeartbeatRequest 默认所有字段为 null`() {
        val request = DeviceHeartbeatRequest()
        assertNull(request.batteryLevel)
        assertNull(request.isCharging)
        assertNull(request.networkType)
    }

    @Test
    fun `DeviceHeartbeatRequest 默认值序列化后字段都被省略 Gson 默认行为`() {
        // Gson 默认不序列化 null 字段；全 null → 序列化后是 "{}"
        val json = Gson().toJson(DeviceHeartbeatRequest())
        assertEquals("{}", json)
    }

    @Test
    fun `DeviceHeartbeatRequest 三个字段都填时 JSON 包含全部字段`() {
        val request = DeviceHeartbeatRequest(
            batteryLevel = 75,
            isCharging = true,
            networkType = NetworkType.WIFI.value,
        )
        val json = Gson().toJson(request)
        assertTrue("JSON 必须含 batteryLevel: $json", json.contains("\"batteryLevel\":75"))
        assertTrue("JSON 必须含 isCharging: $json", json.contains("\"isCharging\":true"))
        assertTrue("JSON 必须含 networkType: $json", json.contains("\"networkType\":\"wifi\""))
    }

    @Test
    fun `DeviceHeartbeatRequest isCharging false 显式序列化`() {
        val request = DeviceHeartbeatRequest(isCharging = false, networkType = "cellular")
        val json = Gson().toJson(request)
        assertTrue("isCharging=false 必须显式序列化: $json", json.contains("\"isCharging\":false"))
    }

    @Test
    fun `DeviceHeartbeatRequest batteryLevel 边界 0 和 100 都可序列化`() {
        val json0 = Gson().toJson(DeviceHeartbeatRequest(batteryLevel = 0))
        assertTrue("batteryLevel=0 序列化: $json0", json0.contains("\"batteryLevel\":0"))

        val json100 = Gson().toJson(DeviceHeartbeatRequest(batteryLevel = 100))
        assertTrue("batteryLevel=100 序列化: $json100", json100.contains("\"batteryLevel\":100"))
    }

    @Test
    fun `DeviceHeartbeatRequest 负 batteryLevel 也可序列化 不做范围校验`() {
        // Int? 不在 DTO 层做范围校验；调用方负责 clamp
        val json = Gson().toJson(DeviceHeartbeatRequest(batteryLevel = -10))
        assertTrue(json.contains("\"batteryLevel\":-10"))
    }

    // --- DeviceHeartbeatRequest 状态变体 ---

    @Test
    fun `DeviceHeartbeatRequest WIFI 状态序列化含 wifi 字符串`() {
        val request = DeviceHeartbeatRequest(
            batteryLevel = 80,
            isCharging = true,
            networkType = NetworkType.WIFI.value,
        )
        val json = Gson().toJson(request)
        assertTrue(json.contains("\"networkType\":\"wifi\""))
    }

    @Test
    fun `DeviceHeartbeatRequest CELLULAR 状态序列化含 cellular 字符串`() {
        val request = DeviceHeartbeatRequest(
            batteryLevel = 50,
            isCharging = false,
            networkType = NetworkType.CELLULAR.value,
        )
        val json = Gson().toJson(request)
        assertTrue(json.contains("\"networkType\":\"cellular\""))
    }

    @Test
    fun `DeviceHeartbeatRequest OFFLINE 状态网络类型字段不省略`() {
        val request = DeviceHeartbeatRequest(
            batteryLevel = 30,
            isCharging = false,
            networkType = NetworkType.OFFLINE.value,
        )
        val json = Gson().toJson(request)
        assertTrue("OFFLINE 状态 networkType 字段必须存在: $json", json.contains("\"networkType\""))
        assertTrue("OFFLINE 状态 networkType 必须是 offline: $json", json.contains("\"offline\""))
        assertFalse("OFFLINE 不应被序列化为 null", json.contains("\"networkType\":null"))
    }

    // --- DeviceHeartbeatResponse 默认值 ---

    @Test
    fun `DeviceHeartbeatResponse 默认值 pendingTaskCount=0 skillVersion=0`() {
        val response = DeviceHeartbeatResponse()
        assertEquals(0, response.pendingTaskCount)
        assertEquals(0, response.skillVersion)
    }

    // --- Round-trip ---

    @Test
    fun `DeviceHeartbeatRequest 序列化后反序列化字段值保持`() {
        val original = DeviceHeartbeatRequest(
            batteryLevel = 88,
            isCharging = true,
            networkType = NetworkType.WIFI.value,
        )
        val gson = Gson()
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, DeviceHeartbeatRequest::class.java)
        assertEquals(original, parsed)
    }

    @Test
    fun `DeviceHeartbeatRequest 反序列化未知字段不抛异常`() {
        val json = """{"batteryLevel":50,"isCharging":false,"networkType":"offline","unknownField":"xyz"}"""
        val parsed = Gson().fromJson(json, DeviceHeartbeatRequest::class.java)
        assertEquals(50, parsed.batteryLevel)
        assertEquals(false, parsed.isCharging)
        assertEquals("offline", parsed.networkType)
    }
}
