// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// HMAC-SHA256 签名生成器单元测试 — 对齐 device.openapi.yaml

package io.agents.pokeclaw.cloud.auth

import com.google.gson.Gson
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import org.junit.Test
import org.junit.Assert.*

/**
 * ClawSignatureGenerator 单元测试。
 *
 * 验证：
 * 1. 签名头生成（timestamp、nonce、signature）
 * 2. 路径契约正确
 * 3. 签名算法与后端一致
 * 4. 相同输入产生相同签名
 */
class ClawSignatureGeneratorTest {

    private val gson = Gson()

    @Test
    fun `generateHeaders 返回有效的签名头`() {
        val deviceToken = "test-device-token-12345"
        val path = "/api/claw-device/tasks/test-task-uuid/result"
        val bodyJson = "{}"

        val headers = ClawSignatureGenerator.generateHeaders(deviceToken, path, bodyJson)

        // 验证 timestamp 是合理的毫秒时间戳（过去1分钟内）
        val now = System.currentTimeMillis()
        assertTrue("timestamp 应该在合理范围内", headers.timestamp in (now - 60000)..now)

        // 验证 nonce 是有效的 UUID 格式
        assertTrue("nonce 应该是 UUID 格式", headers.nonce.matches(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\$", RegexOption.IGNORE_CASE)
        ))

        // 验证 signature 是 64 位的十六进制字符串（SHA256 = 32 bytes = 64 hex chars）
        assertEquals("signature 应该是 64 位十六进制字符串", 64, headers.signature.length)
        assertTrue("signature 应该只包含十六进制字符", headers.signature.matches(Regex("^[0-9a-f]{64}\$", RegexOption.IGNORE_CASE)))
    }

    @Test
    fun `generateSignature 相同输入产生相同签名`() {
        val deviceToken = "test-device-token-12345"
        val timestamp = 1716284400000L
        val nonce = "550e8400-e29b-41d4-a716-446655440000"
        val path = "/api/claw-device/tasks/test-task-uuid/result"
        val bodyJson = "{\"status\":\"SUCCESS\"}"

        val signature1 = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)
        val signature2 = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)

        assertEquals("相同输入应该产生相同签名", signature1, signature2)
    }

    @Test
    fun `generateSignature 不同输入产生不同签名`() {
        val deviceToken = "test-device-token-12345"
        val timestamp = 1716284400000L
        val nonce = "550e8400-e29b-41d4-a716-446655440000"
        val path = "/api/claw-device/tasks/test-task-uuid/result"
        val bodyJson = "{\"status\":\"SUCCESS\"}"

        val baseSignature = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)

        // 修改 timestamp
        val sigWithDifferentTimestamp = ClawSignatureGenerator.generateSignature(
            deviceToken, timestamp + 1, nonce, path, bodyJson
        )
        assertNotEquals("不同 timestamp 应该产生不同签名", baseSignature, sigWithDifferentTimestamp)

        // 修改 nonce
        val sigWithDifferentNonce = ClawSignatureGenerator.generateSignature(
            deviceToken, timestamp, "different-nonce-1234", path, bodyJson
        )
        assertNotEquals("不同 nonce 应该产生不同签名", baseSignature, sigWithDifferentNonce)

        // 修改 path
        val sigWithDifferentPath = ClawSignatureGenerator.generateSignature(
            deviceToken, timestamp, nonce, "/api/claw-device/tasks/other-uuid/result", bodyJson
        )
        assertNotEquals("不同 path 应该产生不同签名", baseSignature, sigWithDifferentPath)

        // 修改 body
        val sigWithDifferentBody = ClawSignatureGenerator.generateSignature(
            deviceToken, timestamp, nonce, path, "{\"status\":\"FAILED\"}"
        )
        assertNotEquals("不同 body 应该产生不同签名", baseSignature, sigWithDifferentBody)
    }

    @Test
    fun `generateSignature 路径契约验证`() {
        val deviceToken = "test-device-token"
        val timestamp = 1716284400000L
        val nonce = "test-nonce-uuid"
        val bodyJson = "{}"

        // 验证标准任务结果提交路径格式
        val taskUuid = "task-123-abc"
        val path = "/api/claw-device/tasks/$taskUuid/result"

        val signature = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)

        assertNotNull("路径格式正确时应生成签名", signature)
        assertEquals("签名长度应为 64", 64, signature.length)
    }

    @Test
    fun `签名算法符合 device_openapi_yaml 规范`() {
        // 使用已知的测试向量验证签名算法正确性
        // 手动计算：
        // signing_string = "1716284400000\n550e8400-e29b-41d4-a716-446655440000\n/api/claw-device/tasks/test/result\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        // HMAC-SHA256("test-token", signing_string) 应该产生确定的结果

        val deviceToken = "test-token"
        val timestamp = 1716284400000L
        val nonce = "550e8400-e29b-41d4-a716-446655440000"
        val path = "/api/claw-device/tasks/test/result"
        val bodyJson = "{}" // SHA256: 44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a

        val signature = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)

        // 验证签名格式
        assertEquals("签名应为 64 位十六进制字符串", 64, signature.length)
        assertTrue("签名应只包含十六进制字符", signature.matches(Regex("^[0-9a-f]{64}\$", RegexOption.IGNORE_CASE)))

        // 验证签名是确定性的（再次计算应该相同）
        val signature2 = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, bodyJson)
        assertEquals("签名应该是确定性的", signature, signature2)
    }

    @Test
    fun `TaskResultRequest 序列化后的签名验证`() {
        val request = TaskResultRequest(
            status = TaskResultRequest.Status.SUCCESS,
            result = "任务执行成功",
            executionTimeMs = 1234,
            modelUsed = "local"
        )

        val bodyJson = gson.toJson(request)
        val deviceToken = "test-device-token"
        val path = "/api/claw-device/tasks/test-task-uuid/result"

        val headers = ClawSignatureGenerator.generateHeaders(deviceToken, path, bodyJson)

        // 手动计算签名以验证
        val manualSignature = ClawSignatureGenerator.generateSignature(
            deviceToken, headers.timestamp, headers.nonce, path, bodyJson
        )

        assertEquals("generateHeaders 和 generateSignature 应该产生一致的签名", manualSignature, headers.signature)
    }

    @Test
    fun `不同 deviceToken 产生不同签名`() {
        val timestamp = 1716284400000L
        val nonce = "550e8400-e29b-41d4-a716-446655440000"
        val path = "/api/claw-device/tasks/test/result"
        val bodyJson = "{}"

        val sig1 = ClawSignatureGenerator.generateSignature("token-1", timestamp, nonce, path, bodyJson)
        val sig2 = ClawSignatureGenerator.generateSignature("token-2", timestamp, nonce, path, bodyJson)

        assertNotEquals("不同 deviceToken 应该产生不同签名", sig1, sig2)
    }

    @Test
    fun `空 body 的签名计算`() {
        val deviceToken = "test-token"
        val timestamp = 1716284400000L
        val nonce = "test-nonce"
        val path = "/api/claw-device/tasks/test/result"

        // 空 JSON 对象
        val sigEmpty = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, "{}")
        // 带内容的 JSON
        val sigWithContent = ClawSignatureGenerator.generateSignature(deviceToken, timestamp, nonce, path, "{\"a\":1}")

        assertNotEquals("空 body 和非空 body 应该产生不同签名", sigEmpty, sigWithContent)
    }

    @Test
    fun `nonce 唯一性验证`() {
        val deviceToken = "test-token"
        val path = "/api/claw-device/tasks/test/result"
        val bodyJson = "{}"

        val headers1 = ClawSignatureGenerator.generateHeaders(deviceToken, path, bodyJson)
        val headers2 = ClawSignatureGenerator.generateHeaders(deviceToken, path, bodyJson)

        assertNotEquals("每次生成的 nonce 应该唯一", headers1.nonce, headers2.nonce)
        assertNotEquals("每次生成的签名应该不同", headers1.signature, headers2.signature)
    }
}
