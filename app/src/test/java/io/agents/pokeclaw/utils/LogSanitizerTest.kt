// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LogSanitizer. Covers 6 categories:
 *  - token / password / secret
 *  - phone (with +86 prefix and without)
 *  - contact name
 *  - path (internal + external storage)
 *  - wechat message preview
 *  - full sanitize() composition
 */
class LogSanitizerTest {

    // ---- maskToken ----

    @Test
    fun `maskToken replaces password value with ***`() {
        val result = LogSanitizer.maskToken("password=abc123")
        assertEquals("password=***", result)
    }

    @Test
    fun `maskToken replaces api_key with quotes`() {
        val result = LogSanitizer.maskToken("api_key=\"sk-1234567890\"")
        assertTrue(result.contains("api_key=***"))
        assertTrue(!result.contains("sk-1234567890"))
    }

    @Test
    fun `maskToken replaces bearer token in header-like text`() {
        val result = LogSanitizer.maskToken("Authorization: Bearer eyJhbGc.payload.sig")
        assertTrue(result.contains("Bearer=***") || result.contains("bearer=***"))
    }

    @Test
    fun `maskToken is case insensitive on key name`() {
        val result = LogSanitizer.maskToken("PASSWORD=topsecret123")
        assertTrue(result.contains("PASSWORD=***"))
    }

    @Test
    fun `maskToken does not match non-sensitive numbers`() {
        val input = "execution time=234ms"
        assertEquals(input, LogSanitizer.maskToken(input))
    }

    // ---- maskPhone ----

    @Test
    fun `maskPhone masks 11-digit Chinese phone`() {
        val result = LogSanitizer.maskPhone("call 13812345678 now")
        assertTrue(result.contains("138****5678"))
    }

    @Test
    fun `maskPhone masks +86 prefix phone`() {
        val result = LogSanitizer.maskPhone("+86 138 1234 5678")
        assertTrue(result.contains("***"))
    }

    @Test
    fun `maskPhone does not match short numbers`() {
        val input = "memory=4096MB pid=12345"
        assertEquals(input, LogSanitizer.maskPhone(input))
    }

    // ---- maskPath ----

    @Test
    fun `maskPath masks internal app data path`() {
        val result = LogSanitizer.maskPath("/data/data/io.agents.pokeclaw/files/secret.txt")
        assertTrue(result.startsWith("<files>/"))
        assertTrue(!result.contains("io.agents.pokeclaw"))
    }

    @Test
    fun `maskPath masks user variant internal path`() {
        val result = LogSanitizer.maskPath("/data/user/0/io.agents.pokeclaw/databases/chat.db")
        assertTrue(result.startsWith("<files>/") || result.startsWith("<files>"))
    }

    @Test
    fun `maskPath masks external app data path`() {
        val result = LogSanitizer.maskPath("/storage/emulated/0/Android/data/com.foo/files/x")
        assertTrue(!result.contains("/storage/emulated/0/"))
        assertTrue(result.contains("Android/data/com.foo/files/x") || result.contains("com.foo"))
    }

    // ---- maskContactName ----

    @Test
    fun `maskContactName replaces wechat contact label with 联系人A`() {
        val result = LogSanitizer.maskContactName("from 张三: hello")
        assertTrue(result.contains("联系人A"))
        assertTrue(!result.contains("张三"))
    }

    @Test
    fun `maskContactName increments contact label on multiple matches`() {
        val result = LogSanitizer.maskContactName("from 张三: hi from 李四: bye")
        assertTrue(result.contains("联系人A"))
        assertTrue(result.contains("联系人B"))
    }

    // ---- maskWechatMessage ----

    @Test
    fun `maskWechatMessage keeps short messages intact`() {
        val msg = "hello"
        assertEquals(msg, LogSanitizer.maskWechatMessage(msg))
    }

    @Test
    fun `maskWechatMessage truncates long messages with ellipsis`() {
        val msg = "x".repeat(100)
        val result = LogSanitizer.maskWechatMessage(msg)
        assertEquals(20 + 3, result.length) // 20 + "..."
    }

    // ---- sanitize (composite) ----

    @Test
    fun `sanitize handles null gracefully`() {
        assertEquals(null, LogSanitizer.sanitize(null))
    }

    @Test
    fun `sanitize handles empty string`() {
        assertEquals("", LogSanitizer.sanitize(""))
    }

    @Test
    fun `sanitize applies all masks in one pass`() {
        val input = "token=abc123 from 张三: call 13812345678 at /data/data/io.agents.pokeclaw/files/x.txt"
        val result = LogSanitizer.sanitize(input) ?: ""
        assertTrue(result.contains("token=***"))
        assertTrue(result.contains("联系人A"))
        assertTrue(result.contains("138****5678"))
        assertTrue(result.contains("<files>/"))
        assertTrue(!result.contains("abc123"))
    }

    @Test
    fun `sanitize leaves plain log messages intact`() {
        val input = "task started execution_time=234ms"
        assertEquals(input, LogSanitizer.sanitize(input))
    }
}
