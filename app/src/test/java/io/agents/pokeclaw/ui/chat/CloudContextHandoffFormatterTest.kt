package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudContextHandoffFormatterTest {

    @Test
    fun `conversation handoff keeps user and assistant only`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, "Auto-reply active for Mom on Telegram."),
                ChatMessage(ChatMessage.Role.USER, "The codeword is zulu731."),
                ChatMessage(ChatMessage.Role.ASSISTANT, "ok", modelName = "gpt-4.1"),
                ChatMessage(ChatMessage.Role.TOOL_GROUP, "", toolSteps = listOf(ToolStep("search", "done"))),
                ChatMessage(ChatMessage.Role.SYSTEM, "Accessibility service connecting, please wait..."),
            )
        )

        assertEquals(
            listOf(
                "User: The codeword is zulu731.",
                "Assistant: ok",
            ),
            lines
        )
    }

    // --- empty/whitespace input filtering ---

    @Test
    fun `empty content is dropped`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.USER, ""),
                ChatMessage(ChatMessage.Role.ASSISTANT, "  "),
            )
        )
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `content equal to triple-dot is dropped`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.USER, "..."),
                ChatMessage(ChatMessage.Role.ASSISTANT, "  ...  "),
            )
        )
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `content with leading and trailing whitespace is trimmed`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.USER, "  hi  \n"),
                ChatMessage(ChatMessage.Role.ASSISTANT, "\n  ok  \n"),
            )
        )
        assertEquals(listOf("User: hi", "Assistant: ok"), lines)
    }

    // --- role branches ---

    @Test
    fun `SYSTEM role is always filtered out`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, "background state"),
            )
        )
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `TOOL_GROUP role is always filtered out`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.TOOL_GROUP, "search result", toolSteps = listOf(ToolStep("search", "result"))),
            )
        )
        assertTrue(lines.isEmpty())
    }

    // --- multi-message ordering ---

    @Test
    fun `multiple user and assistant messages preserve order`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, "skip me"),
                ChatMessage(ChatMessage.Role.USER, "first"),
                ChatMessage(ChatMessage.Role.ASSISTANT, "one"),
                ChatMessage(ChatMessage.Role.TOOL_GROUP, "", toolSteps = listOf(ToolStep("tool", "x"))),
                ChatMessage(ChatMessage.Role.USER, "second"),
                ChatMessage(ChatMessage.Role.ASSISTANT, "two"),
            )
        )
        assertEquals(
            listOf(
                "User: first",
                "Assistant: one",
                "User: second",
                "Assistant: two",
            ),
            lines
        )
    }

    // --- empty input ---

    @Test
    fun `empty input list returns empty list`() {
        assertTrue(CloudContextHandoffFormatter.conversationLines(emptyList()).isEmpty())
    }

    // --- ellipsis-like vs real content ---

    @Test
    fun `four-dot content is kept (not the exact 'dot dot dot' sentinel)`() {
        val lines = CloudContextHandoffFormatter.conversationLines(
            listOf(ChatMessage(ChatMessage.Role.USER, "...."))
        )
        assertEquals(listOf("User: ...."), lines)
    }
}
