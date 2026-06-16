package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPromptEnvelopeTest {

    @Test
    fun `build and parse preserve current request and chat history`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = listOf(
                "User: Please summarize the meeting notes.",
                "Assistant: Here is the summary of the meeting.",
            ),
            currentRequest = "Send that summary by email",
            backgroundState = "Background monitor active for: Mom on Telegram.",
        )

        val parsed = TaskPromptEnvelope.parse(prompt)

        assertTrue(parsed.hasChatHistory)
        assertTrue(parsed.hasBackgroundState)
        assertEquals("Send that summary by email", parsed.currentRequest)
        assertTrue(parsed.chatHistory!!.contains("Please summarize the meeting notes."))
        assertTrue(parsed.chatHistory!!.contains("Here is the summary of the meeting."))
        assertEquals("Background monitor active for: Mom on Telegram.", parsed.backgroundState)
    }

    @Test
    fun `plain prompt without envelope still parses as raw request`() {
        val parsed = TaskPromptEnvelope.parse("how much battery left")

        assertFalse(parsed.hasChatHistory)
        assertEquals("how much battery left", parsed.currentRequest)
    }

    // --- build branches ---

    @Test
    fun `build with empty history and no background returns just the request`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = emptyList(),
            currentRequest = "check battery",
        )
        assertEquals("check battery", prompt)
    }

    @Test
    fun `build with history but no background wraps request with history block`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = listOf("User: hi"),
            currentRequest = "do thing",
        )
        assertTrue(prompt.contains("<<<POKECLAW_CHAT_HISTORY>>>"))
        assertTrue(prompt.contains("User: hi"))
        assertTrue(prompt.contains("<<<END_POKECLAW_CHAT_HISTORY>>>"))
        assertFalse(prompt.contains("<<<POKECLAW_BACKGROUND_STATE>>>"))
        assertTrue(prompt.contains("<<<POKECLAW_CURRENT_REQUEST>>>"))
        assertTrue(prompt.contains("do thing"))
    }

    @Test
    fun `build with background only wraps request with background block`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = emptyList(),
            currentRequest = "check battery",
            backgroundState = "monitor active",
        )
        assertFalse(prompt.contains("<<<POKECLAW_CHAT_HISTORY>>>"))
        assertTrue(prompt.contains("<<<POKECLAW_BACKGROUND_STATE>>>"))
        assertTrue(prompt.contains("monitor active"))
    }

    @Test
    fun `build trims surrounding whitespace from request and background`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = emptyList(),
            currentRequest = "  check battery  \n",
            backgroundState = "  monitor active  ",
        )
        assertTrue(prompt.contains("check battery"))
        assertFalse(prompt.contains("  check battery  "))
        assertTrue(prompt.contains("monitor active"))
    }

    @Test
    fun `build with blank history and blank background returns trimmed request`() {
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = listOf("   "),
            currentRequest = "  go  ",
            backgroundState = " ",
        )
        assertEquals("go", prompt)
    }

    // --- parse branches ---

    @Test
    fun `parse with history block only recovers history and request`() {
        val prompt = "<<<POKECLAW_CHAT_HISTORY>>>\n" +
            "User: hi\n" +
            "Assistant: hello\n" +
            "<<<END_POKECLAW_CHAT_HISTORY>>>\n" +
            "<<<POKECLAW_CURRENT_REQUEST>>>\n" +
            "do thing\n" +
            "<<<END_POKECLAW_CURRENT_REQUEST>>>"
        val parsed = TaskPromptEnvelope.parse(prompt)
        assertTrue(parsed.hasChatHistory)
        assertFalse(parsed.hasBackgroundState)
        assertEquals("do thing", parsed.currentRequest)
        assertTrue(parsed.chatHistory!!.contains("User: hi"))
    }

    @Test
    fun `parse with background only recovers background and request`() {
        val prompt = "<<<POKECLAW_BACKGROUND_STATE>>>\n" +
            "monitor X\n" +
            "<<<END_POKECLAW_BACKGROUND_STATE>>>\n" +
            "<<<POKECLAW_CURRENT_REQUEST>>>\n" +
            "act\n" +
            "<<<END_POKECLAW_CURRENT_REQUEST>>>"
        val parsed = TaskPromptEnvelope.parse(prompt)
        assertFalse(parsed.hasChatHistory)
        assertTrue(parsed.hasBackgroundState)
        assertEquals("monitor X", parsed.backgroundState)
    }

    @Test
    fun `parse without request block falls back to raw trimmed prompt`() {
        val prompt = "<<<POKECLAW_CHAT_HISTORY>>>\nUser: hi\n<<<END_POKECLAW_CHAT_HISTORY>>>"
        val parsed = TaskPromptEnvelope.parse(prompt)
        assertEquals(prompt.trim(), parsed.currentRequest)
        assertNull(parsed.chatHistory)
    }

    @Test
    fun `parse with empty current request block falls back to raw prompt`() {
        val prompt = "<<<POKECLAW_CURRENT_REQUEST>>>\n   \n<<<END_POKECLAW_CURRENT_REQUEST>>>"
        val parsed = TaskPromptEnvelope.parse(prompt)
        assertEquals(prompt.trim(), parsed.currentRequest)
    }

    @Test
    fun `hasChatHistory and hasBackgroundState are false for null or blank`() {
        val blank = ParsedTaskPrompt(currentRequest = "x")
        assertFalse(blank.hasChatHistory)
        assertFalse(blank.hasBackgroundState)
        val withBlank = blank.copy(chatHistory = "   ", backgroundState = "")
        assertFalse(withBlank.hasChatHistory)
        assertFalse(withBlank.hasBackgroundState)
    }

    @Test
    fun `roundtrip build then parse preserves all three sections`() {
        val history = listOf("User: a", "Assistant: b", "User: c")
        val prompt = TaskPromptEnvelope.build(
            chatHistoryLines = history,
            currentRequest = "do something",
            backgroundState = "bg state",
        )
        val parsed = TaskPromptEnvelope.parse(prompt)
        assertTrue(parsed.hasChatHistory)
        assertTrue(parsed.hasBackgroundState)
        assertEquals("do something", parsed.currentRequest)
        assertEquals("bg state", parsed.backgroundState)
        history.forEach { line ->
            assertTrue(parsed.chatHistory!!.contains(line))
        }
    }
}
