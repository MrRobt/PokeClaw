package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDeviceDataGuardTest {

    // --- fromTask: parse branches ---

    @Test
    fun `clipboard request blocks completion until clipboard tool is tried`() {
        val guard = DirectDeviceDataGuard.fromTask("Read my clipboard and explain what it says")

        assertTrue(guard.shouldBlockTextOnlyCompletion())
        assertNotNull(guard.maybeBlockFinish())

        guard.recordToolAttempt("clipboard")

        assertFalse(guard.shouldBlockTextOnlyCompletion())
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun `notification slang still activates direct data guard`() {
        val guard = DirectDeviceDataGuard.fromTask("yo whats on my notifs")

        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_notifications")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `formal notification phrase activates guard with notifications tool`() {
        val guard = DirectDeviceDataGuard.fromTask("Please show my recent notifications")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_notifications")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `battery request activates guard with device info tool`() {
        val guard = DirectDeviceDataGuard.fromTask("check my battery status")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `wifi request activates guard with device info tool`() {
        val guard = DirectDeviceDataGuard.fromTask("what's my wifi status")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `bluetooth request activates guard with device info tool`() {
        val guard = DirectDeviceDataGuard.fromTask("is bluetooth on")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `storage request activates guard with device info tool`() {
        val guard = DirectDeviceDataGuard.fromTask("how much storage is free")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `android version request activates guard with device info tool`() {
        val guard = DirectDeviceDataGuard.fromTask("what android version do i have")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `installed apps request activates guard`() {
        val guard = DirectDeviceDataGuard.fromTask("what apps do i have installed")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_installed_apps")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `screen reading request activates guard with screen tool`() {
        val guard = DirectDeviceDataGuard.fromTask("read screen for me")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_screen_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `whats on my screen activates guard`() {
        val guard = DirectDeviceDataGuard.fromTask("what's on my screen")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
        guard.recordToolAttempt("get_screen_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    // --- fromTask: negative branches ---

    @Test
    fun `conceptual clipboard question stays out of device data guard`() {
        val guard = DirectDeviceDataGuard.fromTask("What is an Android clipboard?")

        assertFalse(guard.shouldBlockTextOnlyCompletion())
        assertNull(guard.maybeBlockFinish())
    }

    @Test
    fun `generic chat request does not activate guard`() {
        val guard = DirectDeviceDataGuard.fromTask("Tell me a joke")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `empty task does not activate guard`() {
        val guard = DirectDeviceDataGuard.fromTask("")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    // --- buildPromptSection ---

    @Test
    fun `empty clipboard remains a valid answer path`() {
        val guard = DirectDeviceDataGuard.fromTask("Read my clipboard and explain what it says")

        assertTrue(guard.buildPromptSection().contains("valid result", ignoreCase = true))
        assertTrue(guard.buildCompletionCorrection().contains("valid answer", ignoreCase = true))
        assertTrue(guard.maybeBlockFinish()?.contains("valid result", ignoreCase = true) == true)
    }

    @Test
    fun `buildPromptSection when matched includes the task text and allowed tools`() {
        val guard = DirectDeviceDataGuard.fromTask("check my battery status")
        val section = guard.buildPromptSection()
        assertTrue(section.contains("Task Guard: Direct Device Data"))
        assertTrue(section.contains("check my battery status"))
        assertTrue(section.contains("get_device_info"))
    }

    @Test
    fun `buildPromptSection when not matched returns empty string`() {
        val guard = DirectDeviceDataGuard.fromTask("Tell me a joke")
        assertEquals("", guard.buildPromptSection())
    }

    // --- buildCompletionCorrection ---

    @Test
    fun `buildCompletionCorrection when matched names the task label`() {
        val guard = DirectDeviceDataGuard.fromTask("check my battery status")
        val text = guard.buildCompletionCorrection()
        assertTrue(text.contains("device info"))
        assertTrue(text.contains("get_device_info"))
    }

    @Test
    fun `buildCompletionCorrection when not matched returns default message`() {
        val guard = DirectDeviceDataGuard.fromTask("Tell me a joke")
        val text = guard.buildCompletionCorrection()
        assertTrue(text.contains("[System Guard]"))
        assertTrue(text.contains("Continue the task"))
    }

    // --- matchesNonInteractiveDeviceDataTask ---

    @Test
    fun `matchesNonInteractiveDeviceDataTask excludes screen reading`() {
        assertFalse(DirectDeviceDataGuard.matchesNonInteractiveDeviceDataTask("what's on my screen"))
    }

    @Test
    fun `matchesNonInteractiveDeviceDataTask includes clipboard`() {
        assertTrue(DirectDeviceDataGuard.matchesNonInteractiveDeviceDataTask("read my clipboard"))
    }

    @Test
    fun `matchesNonInteractiveDeviceDataTask includes battery`() {
        assertTrue(DirectDeviceDataGuard.matchesNonInteractiveDeviceDataTask("check battery"))
    }

    @Test
    fun `matchesNonInteractiveDeviceDataTask excludes generic chat`() {
        assertFalse(DirectDeviceDataGuard.matchesNonInteractiveDeviceDataTask("hello there"))
    }

    // --- deterministicToolCall ---

    @Test
    fun `deterministicToolCall for clipboard returns clipboard get`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("Read my clipboard")
        assertNotNull(call)
        assertEquals("clipboard", call!!.toolName)
        assertEquals("get", call.params["action"])
    }

    @Test
    fun `deterministicToolCall for what i copied returns clipboard get`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("what i copied")
        assertNotNull(call)
        assertEquals("clipboard", call!!.toolName)
    }

    @Test
    fun `deterministicToolCall for notification returns get_notifications`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("show me my notifications")
        assertNotNull(call)
        assertEquals("get_notifications", call!!.toolName)
        assertTrue(call.params.isEmpty())
    }

    @Test
    fun `deterministicToolCall for battery returns get_device_info battery`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("how much battery left")
        assertNotNull(call)
        assertEquals("get_device_info", call!!.toolName)
        assertEquals("battery", call.params["category"])
    }

    @Test
    fun `deterministicToolCall for wifi returns get_device_info wifi`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("wifi status")
        assertNotNull(call)
        assertEquals("wifi", call!!.params["category"])
    }

    @Test
    fun `deterministicToolCall for bluetooth returns get_device_info bluetooth`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("bluetooth status")
        assertNotNull(call)
        assertEquals("bluetooth", call!!.params["category"])
    }

    @Test
    fun `deterministicToolCall for storage returns get_device_info storage`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("free storage")
        assertNotNull(call)
        assertEquals("storage", call!!.params["category"])
    }

    @Test
    fun `deterministicToolCall for android version returns get_device_info device`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("android version")
        assertNotNull(call)
        assertEquals("device", call!!.params["category"])
    }

    @Test
    fun `deterministicToolCall for temperature returns get_device_info battery`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("phone temp")
        assertNotNull(call)
        assertEquals("battery", call!!.params["category"])
    }

    @Test
    fun `deterministicToolCall for installed apps returns get_installed_apps`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("what apps do i have")
        assertNotNull(call)
        assertEquals("get_installed_apps", call!!.toolName)
    }

    @Test
    fun `deterministicToolCall for screen reading returns null`() {
        // screen reading is interactive, not deterministic
        val call = DirectDeviceDataGuard.deterministicToolCall("what's on my screen")
        assertNull(call)
    }

    @Test
    fun `deterministicToolCall for unrelated task returns null`() {
        val call = DirectDeviceDataGuard.deterministicToolCall("Tell me a joke")
        assertNull(call)
    }

    // --- recordToolAttempt: tool not in allowedTools is ignored ---

    @Test
    fun `recordToolAttempt with non-allowed tool does not unblock`() {
        val guard = DirectDeviceDataGuard.fromTask("check my battery status")
        guard.recordToolAttempt("screenshot")
        guard.recordToolAttempt("clipboard")
        guard.recordToolAttempt("get_notifications")
        assertTrue(guard.shouldBlockTextOnlyCompletion())
    }

    @Test
    fun `recordToolAttempt on non-matched guard is a no-op`() {
        val guard = DirectDeviceDataGuard.fromTask("Tell me a joke")
        guard.recordToolAttempt("get_device_info")
        assertFalse(guard.shouldBlockTextOnlyCompletion())
    }

    // --- maybeBlockFinish ---

    @Test
    fun `maybeBlockFinish on not-matched returns null`() {
        val guard = DirectDeviceDataGuard.fromTask("Tell me a joke")
        assertNull(guard.maybeBlockFinish())
    }
}
