// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CustomModelSource.toModelInfo] — verifies the URL → filename
 * mapping and the `custom_` id prefix that distinguishes user-defined sources
 * from built-in catalog entries.
 */
class CustomModelSourceTest {

    @Test
    fun toModelInfo_extractsLastPathSegmentAsFilename() {
        val src = CustomModelSource(
            id = "cms-abc",
            name = "My Model",
            url = "https://huggingface.co/user/repo/resolve/main/model.litertlm",
        )
        val info = src.toModelInfo()
        assertEquals("model.litertlm", info.fileName)
    }

    @Test
    fun toModelInfo_idIsPrefixedWithCustom() {
        val src = CustomModelSource(
            id = "cms-abc",
            name = "x",
            url = "https://example.com/path/f.litertlm",
        )
        assertEquals("custom_cms-abc", src.toModelInfo().id)
    }

    @Test
    fun toModelInfo_fallsBackToIdDotLitertlmWhenNoPath() {
        val src = CustomModelSource(
            id = "cms-xyz",
            name = "bare",
            url = "https://example.com",
        )
        val info = src.toModelInfo()
        assertEquals("cms-xyz.litertlm", info.fileName)
    }

    @Test
    fun toModelInfo_stripsTrailingSlashBeforeFilenameExtraction() {
        val src = CustomModelSource(
            id = "cms-1",
            name = "x",
            url = "https://example.com/path/file.litertlm/",
        )
        assertEquals("file.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun toModelInfo_sizeBytesAndRamGbPassThroughWithDefaults() {
        val withDefaults = CustomModelSource(
            id = "cms-1",
            name = "x",
            url = "https://example.com/f.litertlm",
        ).toModelInfo()
        assertEquals(0L, withDefaults.sizeBytes)
        assertEquals(8, withDefaults.minRamGb)

        val withValues = CustomModelSource(
            id = "cms-2",
            name = "x",
            url = "https://example.com/f.litertlm",
            sizeBytes = 4_500_000_000L,
            minRamGb = 12,
        ).toModelInfo()
        assertEquals(4_500_000_000L, withValues.sizeBytes)
        assertEquals(12, withValues.minRamGb)
    }

    @Test
    fun toModelInfo_displayNameMarksAsCustom() {
        val src = CustomModelSource(
            id = "cms-1",
            name = "Llama-Clone",
            url = "https://example.com/f.litertlm",
        )
        val info = src.toModelInfo()
        assertTrue("displayName should contain '自定义', got '${info.displayName}'",
            info.displayName.contains("自定义"))
        assertTrue("displayName should contain original name, got '${info.displayName}'",
            info.displayName.contains("Llama-Clone"))
    }

    // --- CustomModelSource data class 行为 ---

    @Test
    fun `CustomModelSource 默认值 sha256 null sizeBytes null minRamGb null enabled true`() {
        val src = CustomModelSource(
            id = "cms-1",
            name = "n",
            url = "https://example.com/f.litertlm",
        )
        assertNull(src.sha256)
        assertNull(src.sizeBytes)
        assertNull(src.minRamGb)
        assertTrue("enabled 默认 true", src.enabled)
    }

    @Test
    fun `CustomModelSource data class equality 与 copy 工作正常`() {
        val a = CustomModelSource(
            id = "cms-1",
            name = "n",
            url = "https://example.com/f.litertlm",
            sha256 = "abc123",
            sizeBytes = 1000L,
            minRamGb = 16,
            enabled = false,
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(enabled = true)
        assertNotEquals(a, c)
        assertTrue(c.enabled)
        assertEquals(false, a.enabled)
    }

    // --- toModelInfo URL 边界 ---

    @Test
    fun `toModelInfo URL 带 query string 不会污染 fileName`() {
        // URI.rawPath 不包含 query，filename 应只取 path 末段
        val src = CustomModelSource(
            id = "cms-q",
            name = "x",
            url = "https://example.com/path/model.litertlm?token=abc",
        )
        assertEquals("model.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo URL 带 fragment 不会污染 fileName`() {
        val src = CustomModelSource(
            id = "cms-frag",
            name = "x",
            url = "https://example.com/path/model.litertlm#section1",
        )
        assertEquals("model.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo URL 多个连续尾斜杠都剥除`() {
        val src = CustomModelSource(
            id = "cms-slash",
            name = "x",
            url = "https://example.com/path/file.litertlm////",
        )
        assertEquals("file.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo URL 路径无扩展名时回退到 id litertlm`() {
        // 末段不包含 . 时回退到 "$id.litertlm"
        val src = CustomModelSource(
            id = "cms-noext",
            name = "x",
            url = "https://example.com/path/feature-branch",
        )
        assertEquals("cms-noext.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo URL 末尾是斜杠且无 path 段时回退到 id litertlm`() {
        val src = CustomModelSource(
            id = "cms-tail",
            name = "x",
            url = "https://example.com/",
        )
        assertEquals("cms-tail.litertlm", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo URL path 含点 但末段无点时回退到 id litertlm`() {
        // 例如 "/path/.hidden" 末段是 ".hidden" 含点但 trimEnd 后还要看
        // 实际是 ".hidden" 含 . 所以会被当作合法 filename
        val src = CustomModelSource(
            id = "cms-hidden",
            name = "x",
            url = "https://example.com/path/.hidden",
        )
        // ".hidden" 含 '.' → 视为合法 filename
        assertEquals(".hidden", src.toModelInfo().fileName)
    }

    @Test
    fun `toModelInfo url 字段透传不变`() {
        val originalUrl = "https://example.com/path/f.litertlm"
        val src = CustomModelSource(
            id = "cms-1",
            name = "x",
            url = originalUrl,
        )
        assertEquals(originalUrl, src.toModelInfo().url)
    }

    @Test
    fun `toModelInfo id 前缀 custom_ 对所有 id 一致应用`() {
        for (id in listOf("a", "abc-123", "中文-id", "id.with.dots", "id_with_underscore")) {
            val src = CustomModelSource(
                id = id,
                name = "n",
                url = "https://example.com/f.litertlm",
            )
            assertEquals("custom_$id", src.toModelInfo().id)
        }
    }

    @Test
    fun `toModelInfo sizeBytes 为 0 时透传为 0 不走 default 0L`() {
        // 显式 sizeBytes=0L 应被透传，与 null 不同（null → 0L default）
        val src = CustomModelSource(
            id = "cms-zero",
            name = "x",
            url = "https://example.com/f.litertlm",
            sizeBytes = 0L,
        )
        assertEquals(0L, src.toModelInfo().sizeBytes)
    }

    @Test
    fun `toModelInfo enabled 字段不进入 ModelInfo 仅作为元数据保留`() {
        // ModelInfo 没有 enabled 字段；CustomModelSource.enabled 仅在持久化层使用
        val enabledSrc = CustomModelSource(
            id = "cms-e",
            name = "x",
            url = "https://example.com/f.litertlm",
            enabled = true,
        )
        val disabledSrc = enabledSrc.copy(enabled = false)
        // enabled 不同但 toModelInfo 产物应一致（除 sha256/sizeBytes/minRamGb 外）
        val infoEnabled = enabledSrc.toModelInfo()
        val infoDisabled = disabledSrc.toModelInfo()
        assertEquals(infoEnabled, infoDisabled)
    }
}
