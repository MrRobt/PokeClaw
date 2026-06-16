package io.agents.pokeclaw.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ExternalAutomationContractTest {

    @Test
    fun `parse task action reads plain task payload`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK to " summarize notifications ",
                ExternalAutomationContract.EXTRA_REQUEST_ID to " req-1 ",
                ExternalAutomationContract.EXTRA_RETURN_ACTION to " io.example.RESULT ",
                ExternalAutomationContract.EXTRA_RETURN_PACKAGE to " net.dinglisch.android.taskerm ",
            )[key]
        }

        assertEquals(ExternalAutomationContract.Mode.TASK, request!!.mode)
        assertEquals("summarize notifications", request.text)
        assertEquals("req-1", request.requestId)
        assertEquals("io.example.RESULT", request.returnAction)
        assertEquals("net.dinglisch.android.taskerm", request.returnPackage)
    }

    @Test
    fun `parse prefers base64 payload over plain payload`() {
        val encoded = Base64.getEncoder().encodeToString("battery status".toByteArray())
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK_B64 to encoded,
                ExternalAutomationContract.EXTRA_TASK to "wrong fallback",
            )[key]
        }

        assertEquals("battery status", request!!.text)
    }

    @Test
    fun `parse chat action can fallback to task extra`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_CHAT
        ) { key ->
            mapOf(ExternalAutomationContract.EXTRA_TASK to "hello")[key]
        }

        assertEquals(ExternalAutomationContract.Mode.CHAT, request!!.mode)
        assertEquals("hello", request.text)
    }

    @Test
    fun `parse rejects unknown action or empty payload`() {
        assertNull(ExternalAutomationContract.parse("other.action") { null })
        assertNull(ExternalAutomationContract.parse(ExternalAutomationContract.ACTION_RUN_TASK) { null })
    }

    // --- base64 handling ---

    @Test
    fun `parse falls back to plain when base64 is blank or empty`() {
        val request1 = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK_B64 to "   ",
                ExternalAutomationContract.EXTRA_TASK to "plain text",
            )[key]
        }
        assertEquals("plain text", request1!!.text)

        val request2 = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK_B64 to "",
                ExternalAutomationContract.EXTRA_TASK to "plain text",
            )[key]
        }
        assertEquals("plain text", request2!!.text)
    }

    @Test
    fun `parse falls back to plain when base64 is invalid`() {
        // Not a valid base64 string — decodeBase64 catches and returns null, plain wins.
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK_B64 to "!!!not base64!!!",
                ExternalAutomationContract.EXTRA_TASK to "fallback text",
            )[key]
        }
        assertEquals("fallback text", request!!.text)
    }

    @Test
    fun `parse chat action prefers chat_b64 over chat extra`() {
        val encoded = Base64.getEncoder().encodeToString("decoded chat".toByteArray())
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_CHAT
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_CHAT_B64 to encoded,
                ExternalAutomationContract.EXTRA_CHAT to "raw chat",
            )[key]
        }
        assertEquals("decoded chat", request!!.text)
        assertEquals(ExternalAutomationContract.Mode.CHAT, request.mode)
    }

    @Test
    fun `parse task action falls back to chat extra when task missing`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(ExternalAutomationContract.EXTRA_CHAT to "hello from chat extra")[key]
        }
        assertNotNull(request)
        assertEquals(ExternalAutomationContract.Mode.TASK, request!!.mode)
        assertEquals("hello from chat extra", request.text)
    }

    @Test
    fun `parse chat action falls back to task extra when chat missing`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_CHAT
        ) { key ->
            mapOf(ExternalAutomationContract.EXTRA_TASK to "hello from task extra")[key]
        }
        assertNotNull(request)
        assertEquals(ExternalAutomationContract.Mode.CHAT, request!!.mode)
        assertEquals("hello from task extra", request.text)
    }

    // --- requestId / returnAction / returnPackage trimming ---

    @Test
    fun `parse trims request id and keeps only non-empty values`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK to "x",
                ExternalAutomationContract.EXTRA_REQUEST_ID to "   ",
                ExternalAutomationContract.EXTRA_RETURN_ACTION to "\t\n",
                ExternalAutomationContract.EXTRA_RETURN_PACKAGE to " pkg ",
            )[key]
        }
        assertNotNull(request)
        // Blank request id / return action become null; trimmed return package kept
        assertNull(request!!.requestId)
        assertNull(request.returnAction)
        assertEquals("pkg", request.returnPackage)
    }

    @Test
    fun `parse with null action returns null`() {
        assertNull(ExternalAutomationContract.parse(null) { null })
    }

    @Test
    fun `parse with both task and chat missing returns null`() {
        assertNull(ExternalAutomationContract.parse(ExternalAutomationContract.ACTION_RUN_TASK) { null })
        assertNull(ExternalAutomationContract.parse(ExternalAutomationContract.ACTION_RUN_CHAT) { null })
    }

    // --- payload from base64 with whitespace ---

    @Test
    fun `parse trims base64 encoded value before decoding`() {
        val encoded = Base64.getEncoder().encodeToString("hello world".toByteArray())
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(
                ExternalAutomationContract.EXTRA_TASK_B64 to "  $encoded  ",
            )[key]
        }
        assertEquals("hello world", request!!.text)
    }

    // --- payload value with leading/trailing whitespace gets trimmed ---

    @Test
    fun `parse trims surrounding whitespace from plain text payload`() {
        val request = ExternalAutomationContract.parse(
            ExternalAutomationContract.ACTION_RUN_TASK
        ) { key ->
            mapOf(ExternalAutomationContract.EXTRA_TASK to "  hello  \n")[key]
        }
        assertEquals("hello", request!!.text)
    }
}
