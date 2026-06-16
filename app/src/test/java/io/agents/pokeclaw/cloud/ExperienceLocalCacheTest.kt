// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// ExperienceLocalCache 单测 — save 序列化/MAX_ENTRIES 裁剪、load 反序列化、字段缺省、JSON 损坏降级。

package io.agents.pokeclaw.cloud

import android.content.Context
import android.content.ContextWrapper
import io.agents.pokeclaw.utils.KVUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExperienceLocalCacheTest {

    /**
     * 空壳 Context：ExperienceLocalCache 内部实际使用 KVUtils（不读 Context），
     * 但 API 签名要求 Context 参数。用 ContextWrapper(null) 提供任意 Context 实例即可。
     * 注意：不能在测试中调任何 Context 方法，否则 NPE。
     */
    private val noopContext: Context = object : ContextWrapper(null) {}

    @Before
    fun setUp() {
        KVUtils.resetTestBacking()
    }

    @After
    fun tearDown() {
        KVUtils.resetTestBacking()
    }

    private fun sample(
        id: String = "task-1",
        type: ExperienceReader.Experience.Type = ExperienceReader.Experience.Type.SUCCESS,
        summary: String = "Test summary",
        category: String = "PERMISSION",
        code: String = "PERMISSION_MISSING",
        hint: String = "open settings",
        recordedAt: Long = 1_700_000_000L,
        keywords: List<String> = listOf("settings", "permission"),
    ) = ExperienceReader.Experience(
        commercialTaskId = id,
        type = type,
        summary = summary,
        errorCategory = category,
        errorCode = code,
        recoveryHint = hint,
        recordedAt = recordedAt,
        strategyKeywords = keywords,
    )

    // ── save → load 往返 ──

    @Test
    fun `save 后 load 完整还原所有字段`() {
        val list = listOf(
            sample(id = "t-1"),
            sample(id = "t-2", type = ExperienceReader.Experience.Type.FAILURE, summary = "fail"),
        )
        ExperienceLocalCache.save(context = noopContext, experiences = list)  // Context 未被实际使用
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(2, loaded.size)
        assertEquals("t-1", loaded[0].commercialTaskId)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, loaded[0].type)
        assertEquals("t-2", loaded[1].commercialTaskId)
        assertEquals(ExperienceReader.Experience.Type.FAILURE, loaded[1].type)
    }

    @Test
    fun `save 后 load - 保留 strategyKeywords 列表`() {
        ExperienceLocalCache.save(
            context = noopContext,
            experiences = listOf(sample(keywords = listOf("a", "b", "c"))),
        )
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(listOf("a", "b", "c"), loaded[0].strategyKeywords)
    }

    @Test
    fun `load - 空 KV 返回空列表`() {
        assertEquals(emptyList<ExperienceReader.Experience>(), ExperienceLocalCache.load(context = noopContext))
    }

    @Test
    fun `load - 空字符串 返回空列表`() {
        KVUtils.putString("experience_local_cache_v1", "")
        assertEquals(emptyList<ExperienceReader.Experience>(), ExperienceLocalCache.load(context = noopContext))
    }

    // ── MAX_ENTRIES 裁剪 ──

    @Test
    fun `save - 超过 MAX_ENTRIES 时仅保留前 MAX_ENTRIES 个`() {
        // ExperienceLocalCache.MAX_ENTRIES = 200
        // 业务契约：超过时只保留前面 MAX_ENTRIES 个（take(MAX_ENTRIES) 截断）
        val list = (1..205).map { sample(id = "task-$it") }
        ExperienceLocalCache.save(context = noopContext, experiences = list)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals("应被截断到 MAX_ENTRIES=200", 200, loaded.size)
        assertEquals("保留前 MAX_ENTRIES 个", "task-1", loaded[0].commercialTaskId)
        assertEquals("保留第 MAX_ENTRIES 个", "task-200", loaded[199].commercialTaskId)
    }

    @Test
    fun `save - 恰好 MAX_ENTRIES 个 全部保留`() {
        val list = (1..200).map { sample(id = "task-$it") }
        ExperienceLocalCache.save(context = noopContext, experiences = list)
        assertEquals(200, ExperienceLocalCache.load(context = noopContext).size)
    }

    // ── 字段缺省 / 解析容错 ──

    @Test
    fun `load - 缺 type 字段时回退到 SUCCESS`() {
        // 手动注入缺 type 的 JSON
        val bad = """[{"commercialTaskId":"t","summary":"x"}]"""
        KVUtils.putString("experience_local_cache_v1", bad)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(1, loaded.size)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, loaded[0].type)
    }

    @Test
    fun `load - type 为无效枚举时回退到 SUCCESS`() {
        val bad = """[{"commercialTaskId":"t","type":"INVALID_ENUM_VALUE","summary":"x"}]"""
        KVUtils.putString("experience_local_cache_v1", bad)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(1, loaded.size)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, loaded[0].type)
    }

    @Test
    fun `load - 字段全部缺失时使用空字符串默认`() {
        val bare = """[{}]"""
        KVUtils.putString("experience_local_cache_v1", bare)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(1, loaded.size)
        val e = loaded[0]
        assertEquals("", e.commercialTaskId)
        assertEquals("", e.summary)
        assertEquals("", e.errorCategory)
        assertEquals("", e.errorCode)
        assertEquals("", e.recoveryHint)
        assertEquals(0L, e.recordedAt)
        assertEquals(emptyList<String>(), e.strategyKeywords)
    }

    @Test
    fun `load - 损坏的 JSON 返回空列表不抛错`() {
        KVUtils.putString("experience_local_cache_v1", "{not valid json")
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(emptyList<ExperienceReader.Experience>(), loaded)
    }

    @Test
    fun `load - 数组里某元素不是 JSON object 跳过该元素`() {
        val mixed = """[{"commercialTaskId":"t-1","summary":"ok"}, "not-an-object", {"commercialTaskId":"t-2","summary":"ok"}]"""
        KVUtils.putString("experience_local_cache_v1", mixed)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        // 期望保留 t-1 和 t-2（optJSONObject 在 "not-an-object" 上返回 null → continue）
        assertEquals(2, loaded.size)
        assertEquals("t-1", loaded[0].commercialTaskId)
        assertEquals("t-2", loaded[1].commercialTaskId)
    }

    @Test
    fun `load - strategyKeywords 中空字符串被过滤`() {
        // 关键字数组里夹空串应被 filterNot { it.isEmpty() } 过滤
        val json = """[{"commercialTaskId":"t","strategyKeywords":["a","","b"]}]"""
        KVUtils.putString("experience_local_cache_v1", json)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(listOf("a", "b"), loaded[0].strategyKeywords)
    }

    // ── 多次 save 覆盖语义 ──

    @Test
    fun `save 二次调用 - 后者完全覆盖前者（不合并）`() {
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(sample(id = "t-1")))
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(sample(id = "t-2"), sample(id = "t-3")))
        val loaded = ExperienceLocalCache.load(context = noopContext)
        assertEquals(2, loaded.size)
        assertEquals("t-2", loaded[0].commercialTaskId)
        assertEquals("t-3", loaded[1].commercialTaskId)
    }

    @Test
    fun `save 传空列表 - KV 置为空 JSON 数组（load 仍返回空列表）`() {
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(sample(id = "t-1")))
        ExperienceLocalCache.save(context = noopContext, experiences = emptyList())
        assertEquals(emptyList<ExperienceReader.Experience>(), ExperienceLocalCache.load(context = noopContext))
    }

    // ── 业务契约保护 ──

    @Test
    fun `MAX_ENTRIES 固定 200 (业务契约)`() {
        // 通过反射读 private const
        val field = ExperienceLocalCache::class.java.getDeclaredField("MAX_ENTRIES")
        field.isAccessible = true
        assertEquals(200, field.get(ExperienceLocalCache))
    }

    @Test
    fun `save 大列表性能 - 1000 项 save load 不应阻塞`() {
        val list = (1..1000).map { sample(id = "task-$it") }
        val start = System.currentTimeMillis()
        ExperienceLocalCache.save(context = noopContext, experiences = list)
        val loaded = ExperienceLocalCache.load(context = noopContext)
        val elapsed = System.currentTimeMillis() - start
        assertEquals(200, loaded.size)  // 截断到 200
        assertTrue("save+load 1000 项应在 2s 内完成，实际 ${elapsed}ms", elapsed < 2_000)
    }

    @Test
    fun `load 后字段 - sample 数据完整保真 (回归保护)`() {
        val original = ExperienceReader.Experience(
            commercialTaskId = "abc-123",
            type = ExperienceReader.Experience.Type.FAILURE,
            summary = "Operation failed at step 3",
            errorCategory = "TOOL",
            errorCode = "TOOL_FAILED",
            recoveryHint = "Retry after closing target app",
            recordedAt = 1718000000000L,
            strategyKeywords = listOf("retry", "close-app"),
        )
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(original))
        val loaded = ExperienceLocalCache.load(context = noopContext).single()
        assertEquals(original, loaded)
    }

    @Test
    fun `save - 错误信息字段 (errorCategory errorCode recoveryHint) 完整保存`() {
        val e = sample(
            id = "t1",
            type = ExperienceReader.Experience.Type.FAILURE,
            category = "TIMEOUT",
            code = "EXECUTION_TIMEOUT",
            hint = "simplify task",
        )
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(e))
        val loaded = ExperienceLocalCache.load(context = noopContext).single()
        assertEquals("TIMEOUT", loaded.errorCategory)
        assertEquals("EXECUTION_TIMEOUT", loaded.errorCode)
        assertEquals("simplify task", loaded.recoveryHint)
    }

    @Test
    fun `save - 中文 summary 和 strategyKeywords 正确序列化`() {
        val e = sample(
            id = "t-中文",
            summary = "测试摘要",
            keywords = listOf("关键词1", "关键词2"),
        )
        ExperienceLocalCache.save(context = noopContext, experiences = listOf(e))
        val loaded = ExperienceLocalCache.load(context = noopContext).single()
        assertEquals("t-中文", loaded.commercialTaskId)
        assertEquals("测试摘要", loaded.summary)
        assertEquals(listOf("关键词1", "关键词2"), loaded.strategyKeywords)
    }
}
