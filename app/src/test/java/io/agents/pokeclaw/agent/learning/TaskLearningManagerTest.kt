package io.agents.pokeclaw.agent.learning

import android.content.Context
import android.content.ContextWrapper
import io.agents.pokeclaw.cloud.ExperienceLocalCache
import io.agents.pokeclaw.cloud.ExperienceReader
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskLearningManagerTest {
    private val context: Context = object : ContextWrapper(null) {}
    private var now = 1_000L
    private lateinit var manager: TaskLearningManager

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        KVUtils.resetTestBacking()
        manager = TaskLearningManager(context) { now }
    }

    @After
    fun tearDown() {
        KVUtils.resetTestBacking()
    }

    @Test
    fun `recordSuccess persists a reusable success experience`() {
        manager.recordSuccess(
            taskId = "task-1",
            taskText = "open settings",
            summary = "Settings opened",
        )

        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, loaded[0].type)
        assertEquals("task-1", loaded[0].commercialTaskId)
        assertTrue(loaded[0].summary.contains("open settings"))
        assertTrue(loaded[0].summary.contains("Settings opened"))
        assertEquals(listOf("open", "settings"), loaded[0].strategyKeywords)
    }

    @Test
    fun `recordFailure persists recovery hints for future prompts`() {
        manager.recordFailure(
            taskId = "task-2",
            taskText = "tap confirm",
            errorCategory = "ACCESSIBILITY",
            errorCode = "SERVICE_OFF",
            recoveryHint = "Ask user to enable Accessibility before tapping.",
        )

        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals(ExperienceReader.Experience.Type.FAILURE, loaded[0].type)
        assertEquals("ACCESSIBILITY", loaded[0].errorCategory)
        assertEquals("SERVICE_OFF", loaded[0].errorCode)
        assertTrue(loaded[0].recoveryHint.contains("Accessibility"))
    }

    @Test
    fun `buildPrompt injects recent success and failure examples`() {
        now = 100L
        manager.recordSuccess("old", "open contacts", "Contacts opened")
        now = 200L
        manager.recordSuccess("new", "open settings", "Settings opened")
        now = 300L
        manager.recordFailure("fail", "tap confirm", "ACCESSIBILITY", "SERVICE_OFF", "Enable service")

        val prompt = manager.buildPrompt("open settings again")

        assertTrue(prompt.contains("## Past successful experiences"))
        assertTrue(prompt.contains("Settings opened"))
        assertTrue(prompt.contains("Contacts opened"))
        assertTrue(prompt.contains("## Past failure experiences"))
        assertTrue(prompt.contains("ACCESSIBILITY/SERVICE_OFF"))
        assertTrue(prompt.contains("## Current task"))
        assertTrue(prompt.endsWith("open settings again"))
    }

    @Test
    fun `buildPrompt returns original task when there is no experience`() {
        val prompt = manager.buildPrompt("read notifications")
        assertEquals("read notifications", prompt)
        assertNotNull(prompt)
    }

    // --- return value of recordSuccess / recordFailure ---

    @Test
    fun `recordSuccess returns the persisted experience with the provided taskId`() {
        val exp = manager.recordSuccess(
            taskId = "task-rt-1",
            taskText = "do something",
            summary = "did it",
        )
        assertEquals("task-rt-1", exp.commercialTaskId)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, exp.type)
        assertEquals(1_000L, exp.recordedAt)
        assertTrue(exp.summary.contains("do something"))
        assertTrue(exp.summary.contains("did it"))
        assertEquals(listOf("do", "something"), exp.strategyKeywords)
    }

    @Test
    fun `recordFailure returns the persisted experience with all fields`() {
        val exp = manager.recordFailure(
            taskId = "task-rt-2",
            taskText = "tap submit",
            errorCategory = "NETWORK",
            errorCode = "TIMEOUT",
            recoveryHint = "Retry with backoff",
        )
        assertEquals("task-rt-2", exp.commercialTaskId)
        assertEquals(ExperienceReader.Experience.Type.FAILURE, exp.type)
        assertEquals("NETWORK", exp.errorCategory)
        assertEquals("TIMEOUT", exp.errorCode)
        assertEquals("Retry with backoff", exp.recoveryHint)
        assertEquals(listOf("tap", "submit"), exp.strategyKeywords)
    }

    // --- blank input fallbacks ---

    @Test
    fun `recordSuccess with blank taskId falls back to local-hash prefix`() {
        manager.recordSuccess(taskId = "   ", taskText = "open x", summary = "ok")
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertTrue(
            "blank taskId should map to local- hash, got '${loaded[0].commercialTaskId}'",
            loaded[0].commercialTaskId.startsWith("local-"),
        )
    }

    @Test
    fun `recordFailure with blank errorCategory defaults to TASK_FAILED`() {
        manager.recordFailure(
            taskId = "task-bc",
            taskText = "x",
            errorCategory = "   ",
            errorCode = "E1",
            recoveryHint = "h",
        )
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals("TASK_FAILED", loaded[0].errorCategory)
    }

    @Test
    fun `recordFailure with blank errorCode defaults to UNKNOWN`() {
        manager.recordFailure(
            taskId = "task-bc-2",
            taskText = "x",
            errorCategory = "NET",
            errorCode = "",
            recoveryHint = "h",
        )
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals("UNKNOWN", loaded[0].errorCode)
    }

    @Test
    fun `recordFailure with blank recoveryHint defaults to a generic retry hint`() {
        manager.recordFailure(
            taskId = "task-bc-3",
            taskText = "x",
            errorCategory = "NET",
            errorCode = "E1",
            recoveryHint = "\n  \t",
        )
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals("Retry with a different strategy.", loaded[0].recoveryHint)
    }

    // --- dedup: same (taskId, type) replaces not appends ---

    @Test
    fun `recordSuccess with same taskId replaces previous success instead of appending`() {
        manager.recordSuccess("task-dedup", "open x", "first")
        manager.recordSuccess("task-dedup", "open x", "second")
        val loaded = ExperienceLocalCache.load(context)
        assertEquals("dedup should not duplicate, got ${loaded.size}", 1, loaded.size)
        assertTrue("latest summary should win", loaded[0].summary.contains("second"))
    }

    @Test
    fun `recordFailure with same taskId replaces previous failure instead of appending`() {
        manager.recordFailure("task-dedup-f", "tap y", "NET", "T1", "first hint")
        manager.recordFailure("task-dedup-f", "tap y", "NET", "T1", "second hint")
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(1, loaded.size)
        assertEquals("second hint", loaded[0].recoveryHint)
    }

    @Test
    fun `same taskId for success and failure is treated as two entries`() {
        manager.recordSuccess("task-both", "open z", "ok")
        manager.recordFailure("task-both", "open z", "NET", "T1", "hint")
        val loaded = ExperienceLocalCache.load(context)
        assertEquals(2, loaded.size)
        assertEquals(1, loaded.count { it.type == ExperienceReader.Experience.Type.SUCCESS })
        assertEquals(1, loaded.count { it.type == ExperienceReader.Experience.Type.FAILURE })
    }

    // --- buildPromptSection vs buildPrompt ---

    @Test
    fun `buildPromptSection returns null when cache is empty`() {
        assertNull(manager.buildPromptSection())
    }

    @Test
    fun `buildPromptSection returns a string when experiences exist`() {
        manager.recordSuccess("t", "open a", "ok")
        val section = manager.buildPromptSection()
        assertNotNull(section)
        assertTrue(section!!.contains("Past successful experiences"))
    }

    @Test
    fun `buildPrompt with only successes has no failure section`() {
        manager.recordSuccess("t", "open a", "ok")
        val prompt = manager.buildPrompt("next")
        assertTrue(prompt.contains("Past successful experiences"))
        assertTrue(!prompt.contains("Past failure experiences"))
        assertTrue(prompt.contains("next"))
    }

    @Test
    fun `buildPrompt with only failures has no success section`() {
        manager.recordFailure("t", "tap b", "NET", "T1", "h")
        val prompt = manager.buildPrompt("next")
        assertTrue(prompt.contains("Past failure experiences"))
        assertTrue(!prompt.contains("Past successful experiences"))
        assertTrue(prompt.contains("next"))
    }

    // --- keywords behavior: lowercasing, 2+ chars, distinct, capped at 8 ---

    @Test
    fun `keywords are lowercased deduplicated and capped at 8`() {
        manager.recordSuccess(
            taskId = "kw-1",
            taskText = "Open OPEN open Foo Bar Baz Qux Quux Corge Grault Garply",
            summary = "ok",
        )
        val loaded = ExperienceLocalCache.load(context)
        val kws = loaded[0].strategyKeywords
        assertTrue("all lowercased, got $kws", kws.none { it != it.lowercase() })
        assertEquals("distinct, got $kws", kws.size, kws.toSet().size)
        assertTrue("max 8, got ${kws.size}", kws.size <= 8)
    }

    @Test
    fun `keywords skip 1-char tokens`() {
        // "a" is a single char; "open" is multi-char. Only multi-char should pass.
        manager.recordSuccess(taskId = "kw-2", taskText = "a open z", summary = "ok")
        val loaded = ExperienceLocalCache.load(context)
        // "open" passes; "a" and "z" should not.
        assertEquals(listOf("open"), loaded[0].strategyKeywords)
    }

    // --- compact: trims + joins lines, truncates with ellipsis ---

    @Test
    fun `summary keeps line breaks as spaces when compact calls join them`() {
        manager.recordSuccess(
            taskId = "cmp-1",
            taskText = "step1",
            summary = "  \n  result  \n\n  more  ",
        )
        val loaded = ExperienceLocalCache.load(context)
        // "Task: step1" + "Result: " + trimmed "result more"
        assertTrue(loaded[0].summary.contains("result more"))
        assertTrue(!loaded[0].summary.contains("\n"))
    }
}
