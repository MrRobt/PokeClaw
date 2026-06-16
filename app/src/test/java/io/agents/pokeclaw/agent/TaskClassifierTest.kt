// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// TaskClassifier 单测 — buildClassifierPrompt 模板渲染、parseResponse 容错（markdown / 周围文本 / 损坏 JSON）。

package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskClassifierTest {

    // ── buildClassifierPrompt ──

    @Test
    fun `buildClassifierPrompt - 包含所有支持的 type 定义`() {
        val prompt = TaskClassifier.buildClassifierPrompt(emptyList())
        // 必须明确列出 5 种 type
        assertTrue("应包含 intent", prompt.contains("\"intent\""))
        assertTrue("应包含 skill", prompt.contains("\"skill\""))
        assertTrue("应包含 agent", prompt.contains("\"agent\""))
        assertTrue("应包含 chat", prompt.contains("\"chat\""))
        assertTrue("应包含 impossible", prompt.contains("\"impossible\""))
    }

    @Test
    fun `buildClassifierPrompt - 包含输出 JSON schema 提示`() {
        val prompt = TaskClassifier.buildClassifierPrompt(emptyList())
        assertTrue("应包含 output format", prompt.contains("Output format"))
        assertTrue("应包含 type field", prompt.contains("\"type\""))
        assertTrue("应包含 skill_id field", prompt.contains("\"skill_id\""))
        assertTrue("应包含 params field", prompt.contains("\"params\""))
    }

    @Test
    fun `buildClassifierPrompt - 空 skill 列表时显示 None available`() {
        val prompt = TaskClassifier.buildClassifierPrompt(emptyList())
        assertTrue("空 skill 时应显示 None available，实际：$prompt", prompt.contains("None available"))
    }

    @Test
    fun `buildClassifierPrompt - skill 列表非空时列出所有 skills`() {
        val skills = listOf(
            "search_in_app: search for a query inside a target app",
            "play_music: play a song on a music app",
            "weather: get the current weather",
        )
        val prompt = TaskClassifier.buildClassifierPrompt(skills)
        assertTrue(prompt.contains("search_in_app"))
        assertTrue(prompt.contains("play_music"))
        assertTrue(prompt.contains("weather"))
        assertFalse(prompt.contains("None available"))
        // 每条 skill 应以 "- " 开头
        assertTrue("应包含 markdown bullet 格式", prompt.contains("- search_in_app"))
    }

    @Test
    fun `buildClassifierPrompt - 包含关键业务规则 (search_in_app routing)`() {
        val prompt = TaskClassifier.buildClassifierPrompt(emptyList())
        assertTrue(prompt.contains("search_in_app"))
        assertTrue("应提示 messaging 类任务走 agent", prompt.contains("messaging") && prompt.contains("agent"))
    }

    @Test
    fun `buildClassifierPrompt - 短提示 (业务契约 LLM 节省 token)`() {
        // 设计目标：~200 词，比 agent loop 的 ~2000 词小一个数量级
        val prompt = TaskClassifier.buildClassifierPrompt(listOf("a: b"))
        val wordCount = prompt.split(Regex("\\s+")).size
        assertTrue("classifier prompt 应 < 300 词，实际 $wordCount", wordCount < 300)
    }

    // ── parseResponse: 正常 JSON ──

    @Test
    fun `parseResponse - 干净 JSON 正确解析所有字段`() {
        val json = """{"type":"intent","app":null,"skill_id":null,"sub_goal":"open settings","params":{"target":"wifi"}}"""
        val c = TaskClassifier.parseResponse(json)
        assertEquals("intent", c.type)
        assertEquals("open settings", c.subGoal)
        assertEquals(mapOf("target" to "wifi"), c.params)
    }

    @Test
    fun `parseResponse - skill 分类带 skill_id`() {
        val json = """{"type":"skill","skill_id":"search_in_app","app":"YouTube","params":{"query":"cats"}}"""
        val c = TaskClassifier.parseResponse(json)
        assertEquals("skill", c.type)
        assertEquals("search_in_app", c.skillId)
        assertEquals("YouTube", c.app)
        assertEquals("cats", c.params!!["query"])
    }

    @Test
    fun `parseResponse - agent 分类带 sub_goal`() {
        val json = """{"type":"agent","app":"Chrome","sub_goal":"open example.com"}"""
        val c = TaskClassifier.parseResponse(json)
        assertEquals("agent", c.type)
        assertEquals("Chrome", c.app)
        assertEquals("open example.com", c.subGoal)
    }

    @Test
    fun `parseResponse - chat 分类`() {
        val json = """{"type":"chat"}"""
        val c = TaskClassifier.parseResponse(json)
        assertEquals("chat", c.type)
    }

    @Test
    fun `parseResponse - impossible 分类`() {
        val json = """{"type":"impossible","sub_goal":"cannot do"}"""
        val c = TaskClassifier.parseResponse(json)
        assertEquals("impossible", c.type)
        assertEquals("cannot do", c.subGoal)
    }

    // ── parseResponse: markdown 包装 ──

    @Test
    fun `parseResponse - markdown code fence json 应被剥除`() {
        val wrapped = "```json\n{\"type\":\"intent\",\"sub_goal\":\"open settings\"}\n```"
        val c = TaskClassifier.parseResponse(wrapped)
        assertEquals("intent", c.type)
        assertEquals("open settings", c.subGoal)
    }

    @Test
    fun `parseResponse - markdown code fence 无 language tag 也应剥除`() {
        val wrapped = "```\n{\"type\":\"chat\"}\n```"
        val c = TaskClassifier.parseResponse(wrapped)
        assertEquals("chat", c.type)
    }

    @Test
    fun `parseResponse - 前后空白应被 trim`() {
        val json = """   {"type":"agent","sub_goal":"x"}   """
        val c = TaskClassifier.parseResponse(json)
        assertEquals("agent", c.type)
        assertEquals("x", c.subGoal)
    }

    // ── parseResponse: 周围文本 (LLM 经常在前后加废话) ──

    @Test
    fun `parseResponse - 前置废话后接 JSON 应能提取`() {
        val messy = "Sure! Here's the classification:\n{\"type\":\"agent\",\"sub_goal\":\"search cats\"}"
        val c = TaskClassifier.parseResponse(messy)
        assertEquals("agent", c.type)
        assertEquals("search cats", c.subGoal)
    }

    @Test
    fun `parseResponse - JSON 后接解释文字 应只解析 JSON 部分`() {
        val messy = """{"type":"skill","skill_id":"weather"} This task is best handled by a weather skill."""
        val c = TaskClassifier.parseResponse(messy)
        assertEquals("skill", c.type)
        assertEquals("weather", c.skillId)
    }

    @Test
    fun `parseResponse - 前后都有废话 应提取中间 JSON`() {
        val messy = "Looking at this... {\"type\":\"intent\",\"sub_goal\":\"call mom\"} ...hope that helps"
        val c = TaskClassifier.parseResponse(messy)
        assertEquals("intent", c.type)
        assertEquals("call mom", c.subGoal)
    }

    // ── parseResponse: 错误容错 ──

    @Test
    fun `parseResponse - 完全损坏的 JSON 返回默认 fallback`() {
        val c = TaskClassifier.parseResponse("not json at all")
        // fallback 是 type=agent, sub_goal=原始响应
        assertEquals("损坏 JSON 应 fallback 到 agent", "agent", c.type)
        assertEquals("fallback subGoal 应回填原始响应", "not json at all", c.subGoal)
    }

    @Test
    fun `parseResponse - 空字符串返回 fallback`() {
        val c = TaskClassifier.parseResponse("")
        assertEquals("agent", c.type)
        assertEquals("", c.subGoal)
    }

    @Test
    fun `parseResponse - 仅空白字符串返回 fallback`() {
        val c = TaskClassifier.parseResponse("   \n\t  ")
        assertEquals("agent", c.type)
        assertEquals("   \n\t  ", c.subGoal)
    }

    @Test
    fun `parseResponse - 不闭合的 JSON 返回 fallback`() {
        val c = TaskClassifier.parseResponse("""{"type":"agent",""")
        assertEquals("agent", c.type)
        assertEquals("""{"type":"agent",""", c.subGoal)
    }

    @Test
    fun `parseResponse - 缺右大括号 取最后右括号`() {
        // 残缺 JSON: 只有开括号, 没有闭括号 → start>=0 但 end==-1 → 不进入 substring
        // 但 Gson 还是可能抛 → fallback
        val c = TaskClassifier.parseResponse("""{"type":"chat"""")
        assertEquals("agent", c.type)
    }

    // ── parseResponse: 部分字段缺失 ──

    @Test
    fun `parseResponse - 仅 type 字段, 其他为 null`() {
        val c = TaskClassifier.parseResponse("""{"type":"chat"}""")
        assertEquals("chat", c.type)
        assertNull(c.app)
        assertNull(c.skillId)
        assertNull(c.subGoal)
        assertNull(c.params)
    }

    @Test
    fun `parseResponse - 空 JSON 对象 使用 data class 默认值`() {
        val c = TaskClassifier.parseResponse("{}")
        // type 默认 "agent"
        assertEquals("agent", c.type)
        assertNull(c.app)
        assertNull(c.skillId)
        assertNull(c.subGoal)
        assertNull(c.params)
    }

    // ── Classification 数据类 ──

    @Test
    fun `Classification 默认值 - type=agent 其余 null`() {
        val c = TaskClassifier.Classification()
        assertEquals("agent", c.type)
        assertNull(c.app)
        assertNull(c.skillId)
        assertNull(c.subGoal)
        assertNull(c.params)
    }

    @Test
    fun `Classification - 包含 SerializedName 注解 (Gson 兼容)`() {
        // 反射检查 @SerializedName 存在
        val cls = TaskClassifier.Classification::class.java
        val typeField = cls.getDeclaredField("type")
        val annotation = typeField.getAnnotation(com.google.gson.annotations.SerializedName::class.java)
        assertNotNull("type 字段应有 @SerializedName 注解", annotation)
        assertEquals("type", annotation!!.value)
    }
}