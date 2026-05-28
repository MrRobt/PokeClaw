// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 日志脱敏工具单元测试 — 确保敏感信息不泄露

package io.agents.pokeclaw.cloud.auth

import org.junit.Test
import org.junit.Assert.*

/**
 * LogSanitizer 单元测试。
 *
 * 验证：
 * 1. HTTP 头脱敏：Authorization、X-Claw-Signature、X-Claw-Nonce
 * 2. deviceToken 脱敏：显示前后部分
 * 3. 签名完全脱敏
 * 4. 通用字符串脱敏
 */
class LogSanitizerTest {

    @Test
    fun `sanitizeHttpHeaders 脱敏 Authorization Bearer token`() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertTrue("应包含 [REDACTED]", result.contains("[REDACTED]"))
        assertFalse("不应包含原始 token", result.contains("eyJhbGci"))
        assertTrue("应保留头名称", result.contains("Authorization:"))
        assertTrue("应保留 Bearer 前缀", result.contains("Bearer"))
    }

    @Test
    fun `sanitizeHttpHeaders 脱敏 X-Claw-Signature`() {
        val input = "X-Claw-Signature: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertTrue("应包含 [REDACTED]", result.contains("[REDACTED]"))
        assertFalse("不应包含原始签名", result.contains("a1b2c3d4"))
        assertTrue("应保留头名称", result.contains("X-Claw-Signature:"))
    }

    @Test
    fun `sanitizeHttpHeaders 脱敏 X-Claw-Signature 大小写不敏感`() {
        val input = "x-claw-signature: a1b2c3d4e5f6g7h8i9j0"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertTrue("应包含 [REDACTED]", result.contains("[REDACTED]"))
        assertFalse("不应包含原始签名", result.contains("a1b2c3d4"))
    }

    @Test
    fun `sanitizeHttpHeaders 脱敏 X-Claw-Nonce`() {
        val input = "X-Claw-Nonce: 550e8400-e29b-41d4-a716-446655440000"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertTrue("应包含 [REDACTED]", result.contains("[REDACTED]"))
        assertFalse("不应包含原始 nonce", result.contains("550e8400"))
        assertTrue("应保留头名称", result.contains("X-Claw-Nonce:"))
    }

    @Test
    fun `sanitizeHttpHeaders 保留 X-Claw-Timestamp`() {
        val input = "X-Claw-Timestamp: 1716284400000"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertEquals("时间戳不应被脱敏", input, result)
        assertTrue("应保留原始时间戳", result.contains("1716284400000"))
    }

    @Test
    fun `sanitizeHttpHeaders 处理多行日志`() {
        val input = """GET /api/claw-device/tasks/test/result HTTP/1.1
                |Authorization: Bearer secret_token_12345
                |X-Claw-Timestamp: 1716284400000
                |X-Claw-Nonce: 550e8400-e29b-41d4-a716-446655440000
                |X-Claw-Signature: abc123def456
                |Content-Type: application/json
            """.trimMargin()

        val result = LogSanitizer.sanitizeHttpHeaders(input)

        // 验证敏感信息被脱敏
        assertFalse("不应包含原始 token", result.contains("secret_token"))
        assertFalse("不应包含原始 nonce", result.contains("550e8400"))
        assertFalse("不应包含原始签名", result.contains("abc123def"))

        // 验证不敏感信息保留
        assertTrue("应保留时间戳", result.contains("1716284400000"))
        assertTrue("应保留 Content-Type", result.contains("Content-Type:"))
    }

    @Test
    fun `sanitizeHttpHeaders 处理无敏感信息的日志`() {
        val input = "Content-Type: application/json\nContent-Length: 123"
        val result = LogSanitizer.sanitizeHttpHeaders(input)

        assertEquals("无敏感信息的日志不应改变", input, result)
    }

    @Test
    fun `maskDeviceToken 显示前后部分`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0"
        val result = LogSanitizer.maskDeviceToken(token)

        assertTrue("应包含 ...", result.contains("..."))
        assertTrue("应显示前缀 6 字符", result.startsWith("eyJhbG"))
        assertTrue("应显示后缀 6 字符", result.endsWith("kwIn0"))
        assertFalse("不应包含中间部分", result.contains("IUzI1NiIs"))
    }

    @Test
    fun `maskDeviceToken 短 token 完全脱敏`() {
        val token = "short"
        val result = LogSanitizer.maskDeviceToken(token)

        assertEquals("短 token 应完全脱敏", "[REDACTED]", result)
    }

    @Test
    fun `maskSignature 完全脱敏`() {
        val signature = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6"
        val result = LogSanitizer.maskSignature(signature)

        assertEquals("签名应完全脱敏", "[REDACTED]", result)
    }

    @Test
    fun `maskString 通用脱敏`() {
        val value = "this-is-a-sensitive-value"
        val result = LogSanitizer.maskString(value, visiblePrefix = 4, visibleSuffix = 4)

        assertEquals("this...alue", result)
    }

    @Test
    fun `maskString 短字符串完全脱敏`() {
        val value = "short"
        val result = LogSanitizer.maskString(value, visiblePrefix = 3, visibleSuffix = 3)

        assertEquals("短字符串应完全脱敏", "[REDACTED]", result)
    }

    @Test
    fun `maskString 默认参数`() {
        val value = "abcdefghijklmnopqrstuvwxyz"
        val result = LogSanitizer.maskString(value)

        assertEquals("abcd...wxyz", result)
    }

    @Test
    fun `完整脱敏场景 - HTTP 请求日志`() {
        // 模拟真实的 HTTP 请求日志
        val httpLog = """
            |--> POST /api/claw-device/tasks/test-uuid/result
            |Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secret.signature
            |X-Claw-Timestamp: 1716284400000
            |X-Claw-Nonce: 550e8400-e29b-41d4-a716-446655440000
            |X-Claw-Signature: 1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3w4x5y6z7a8b9c0d1e2f
            |Content-Type: application/json
            |Content-Length: 256
            |Body: {"status":"SUCCESS"}
        """.trimMargin()

        val result = LogSanitizer.sanitizeHttpHeaders(httpLog)

        // 验证敏感信息被脱敏
        assertFalse("不应包含原始 token", result.contains("eyJhbGci"))
        assertFalse("不应包含原始 nonce", result.contains("550e8400"))
        assertFalse("不应包含原始签名", result.contains("1a2b3c4d"))

        // 验证关键信息保留
        assertTrue("应保留时间戳", result.contains("1716284400000"))
        assertTrue("应保留请求方法", result.contains("POST"))
        assertTrue("应保留路径", result.contains("/api/claw-device/tasks"))
    }
}
