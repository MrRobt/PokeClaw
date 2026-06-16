package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailComposeGuardTest {

    // --- fromTask factory: match detection ---

    @Test
    fun explicitEmailComposeTask_isGuarded() {
        val guard = EmailComposeGuard.fromTask("Write an email saying I will be late today")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun composeEmail_withComposeVerb_isMatched() {
        val guard = EmailComposeGuard.fromTask("compose an email to my manager")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun draftEmail_withDraftVerb_isMatched() {
        val guard = EmailComposeGuard.fromTask("draft an email to support@example.com")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun sendEmail_withSendVerb_isMatched() {
        val guard = EmailComposeGuard.fromTask("send an email to the team")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun emailLeadingTaskWithoutComposeVerb_isNotMatched() {
        // The guard requires a compose verb (write/compose/draft/send) AND an email mention.
        // "email John..." starts with the noun "email" not a verb, so it is NOT guarded.
        val guard = EmailComposeGuard.fromTask("email John about the meeting")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun uppercaseEmailComposeTask_isMatchedCaseInsensitively() {
        val guard = EmailComposeGuard.fromTask("WRITE AN EMAIL SAYING HI")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun genericWritingTask_isNotGuarded() {
        val guard = EmailComposeGuard.fromTask("Write a short apology note")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun writingWithoutEmailMention_isNotGuarded() {
        val guard = EmailComposeGuard.fromTask("write a thank-you letter")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun emailMentionWithoutComposeVerb_isNotGuarded() {
        val guard = EmailComposeGuard.fromTask("Tell me about email marketing")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun emptyTask_isNotGuarded() {
        val guard = EmailComposeGuard.fromTask("")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun whitespaceOnlyTask_isNotGuarded() {
        val guard = EmailComposeGuard.fromTask("   \t\n")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    // --- buildPromptSection ---

    @Test
    fun buildPromptSection_whenMatched_includesTaskTextAndGuardHeader() {
        val guard = EmailComposeGuard.fromTask("write an email to the team")
        val section = guard.buildPromptSection()
        assertTrue(section.contains("Task Guard: Compose Email Draft"))
        assertTrue(section.contains("write an email to the team"))
    }

    @Test
    fun buildPromptSection_whenNotMatched_returnsEmptyString() {
        val guard = EmailComposeGuard.fromTask("just chatting")
        assertEquals("", guard.buildPromptSection())
    }

    // --- shouldBlockTextOnlyCompletion after tool attempts ---

    @Test
    fun shouldBlock_unblocksAfterUIComposeToolAttempt() {
        val guard = EmailComposeGuard.fromTask("write an email to Bob")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("open_app")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun shouldBlock_unblocksAfterInputTextSuccessfulTool() {
        val guard = EmailComposeGuard.fromTask("write an email to Carol")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordSuccessfulTool("input_text")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun shouldBlock_unblocksAfterTypeTextSuccessfulTool() {
        val guard = EmailComposeGuard.fromTask("write an email to Dan")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordSuccessfulTool("type_text")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun shouldBlock_unchangedByNonComposeTool() {
        val guard = EmailComposeGuard.fromTask("write an email to Eve")
        guard.recordToolAttempt("screenshot")
        guard.recordToolAttempt("wait")
        guard.recordSuccessfulTool("tap")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    // --- maybeBlockFinish ---

    @Test
    fun maybeBlockFinish_whenNotMatched_returnsNull() {
        val guard = EmailComposeGuard.fromTask("just chatting about email")
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun maybeBlockFinish_whenMatchedButNotAttempted_returnsBlockMessage() {
        val guard = EmailComposeGuard.fromTask("write an email to the team")
        val block = guard.maybeBlockFinish()
        assertNotNull(block)
        assertTrue(block!!.contains("email-compose task"))
        assertTrue(block.contains("Open an email app"))
    }

    @Test
    fun maybeBlockFinish_afterAttempt_returnsNull() {
        val guard = EmailComposeGuard.fromTask("write an email to the team")
        guard.recordToolAttempt("tap_node")
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun maybeBlockFinish_withScreenInfo_containsComposeNodeHint() {
        val guard = EmailComposeGuard.fromTask("write an email to Frank")
        val screenInfo = """
            Some other line
            Compose new email [n42] button
            Subject field [n17]
        """.trimIndent()
        val block = guard.maybeBlockFinish(screenInfo)
        assertNotNull(block)
        assertTrue(block!!.contains("n42"))
    }

    @Test
    fun maybeBlockFinish_withBlankScreenInfo_returnsBlockWithoutNodeHint() {
        val guard = EmailComposeGuard.fromTask("write an email to Grace")
        val block = guard.maybeBlockFinish("")
        assertNotNull(block)
        assertFalse(block!!.contains("node_id="))
    }

    @Test
    fun maybeBlockFinish_withScreenInfo_fallsBackToEditNodeHint() {
        // The fallback regex matches a leading-space " edit" token (case-sensitive),
        // so the test screen line must use lowercase "edit" with a leading space.
        val guard = EmailComposeGuard.fromTask("write an email to Henry")
        val screenInfo = """
            Random title bar
            Has an edit field for the body [n9]
        """.trimIndent()
        val block = guard.maybeBlockFinish(screenInfo)
        assertNotNull(block)
        assertTrue(block!!.contains("n9"))
    }

    @Test
    fun maybeBlockFinish_withScreenInfoWithNoMatchingNode_returnsBlockWithoutHint() {
        val guard = EmailComposeGuard.fromTask("write an email to Irene")
        val screenInfo = """
            Random unrelated text
            More text
        """.trimIndent()
        val block = guard.maybeBlockFinish(screenInfo)
        assertNotNull(block)
        assertFalse(block!!.contains("node_id="))
    }

    // --- buildCompletionCorrection ---

    @Test
    fun buildCompletionCorrection_mentionsGuardAndRequiredSteps() {
        val guard = EmailComposeGuard.fromTask("write an email to Judy")
        val text = guard.buildCompletionCorrection()
        assertTrue(text.contains("[System Guard]"))
        assertTrue(text.contains("email-compose task"))
        assertTrue(text.contains("Open an email app"))
    }
}
