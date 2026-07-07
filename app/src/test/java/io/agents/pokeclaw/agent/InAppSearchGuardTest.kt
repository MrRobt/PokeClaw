package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Positive cases use a common app ("youtube") so OpenAppTool.resolveAppNameStatic()
 * resolves via its static name→package map and never touches PackageManager/context,
 * keeping the test a pure JVM unit test. Negative cases use tasks that do not match
 * the search patterns, so resolveAppNameStatic() is never called.
 */
class InAppSearchGuardTest {

    // --- positive: "search <app> for <query>" ---

    @Test
    fun `search app for query activates guard and blocks until query typed`() {
        val guard = InAppSearchGuard.fromTask("search youtube for lofi beats")

        assertTrue(guard.shouldBlockTextOnlyCompletion())
        assertNotNull(guard.maybeBlockFinish())

        val section = guard.buildPromptSection()
        assertTrue(section.contains("Task Guard: In-App Search"))
        assertTrue(section.contains("youtube"))
        assertTrue(section.contains("lofi beats"))

        guard.recordSuccessfulTool("input_text", mapOf("text" to "lofi beats"))

        assertFalse(guard.shouldBlockTextOnlyCompletion())
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun `search for query on app alternate phrasing activates guard`() {
        val guard = InAppSearchGuard.fromTask("search for cats on youtube")

        assertTrue(guard.shouldBlockTextOnlyCompletion())
        val section = guard.buildPromptSection()
        assertTrue(section.contains("cats"))
        assertTrue(section.contains("youtube"))
    }

    @Test
    fun `typing unrelated text does not unblock but the query text does`() {
        val guard = InAppSearchGuard.fromTask("search youtube for lofi beats")

        guard.recordSuccessfulTool("input_text", mapOf("text" to "something else"))
        assertTrue(guard.shouldBlockTextOnlyCompletion())

        // typing a superset that contains the query counts as typed
        guard.recordSuccessfulTool("input_text", mapOf("text" to "lofi beats music"))
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `opening the target app records progress but still requires typing`() {
        val guard = InAppSearchGuard.fromTask("search youtube for lofi beats")

        guard.recordSuccessfulTool("open_app", mapOf("app_name" to "youtube"))

        // still blocked (query not typed), but the hint no longer nags to open the app
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        val hint = guard.maybeBlockFinish()
        assertNotNull(hint)
        assertFalse(hint!!.contains("Open youtube first"))
        assertTrue(hint.contains("input_text"))
    }

    // --- negative: non-search tasks (never call resolveAppNameStatic) ---

    @Test
    fun `generic chat request does not activate guard`() {
        val guard = InAppSearchGuard.fromTask("Tell me a joke")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
        assertEquals("", guard.buildPromptSection())
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun `plain open app is not an in-app search`() {
        val guard = InAppSearchGuard.fromTask("open youtube")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
        assertEquals("", guard.buildPromptSection())
    }

    @Test
    fun `empty task does not activate guard`() {
        val guard = InAppSearchGuard.fromTask("")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `not matched buildCompletionCorrection returns default guidance`() {
        val guard = InAppSearchGuard.fromTask("Tell me a joke")
        val text = guard.buildCompletionCorrection()
        assertTrue(text.contains("[System Guard]"))
        assertTrue(text.contains("Continue the task"))
    }
}
