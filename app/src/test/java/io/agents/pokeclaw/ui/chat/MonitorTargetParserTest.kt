package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTargetParserTest {

    @Test
    fun `parses telegram monitor target`() {
        val result = MonitorTargetParser.fromTaskText("monitor Mom on Telegram")

        requireNotNull(result)
        assertEquals("Mom", result.label)
        assertEquals("Telegram", result.app)
    }

    @Test
    fun `defaults to whatsapp when app missing`() {
        val result = MonitorTargetParser.fromTaskText("monitor girlfriend")

        requireNotNull(result)
        assertEquals("Girlfriend", result.label)
        assertEquals("WhatsApp", result.app)
    }

    @Test
    fun `does not mistake caroline for line app`() {
        val result = MonitorTargetParser.fromTaskText("monitor Caroline")

        requireNotNull(result)
        assertEquals("Caroline", result.label)
        assertEquals("WhatsApp", result.app)
    }

    @Test
    fun `parses messages aliases`() {
        val result = MonitorTargetParser.fromTaskText("watch Alex on sms")

        requireNotNull(result)
        assertEquals("Alex", result.label)
        assertEquals("Messages", result.app)
    }

    @Test
    fun `returns null when no target remains`() {
        assertNull(MonitorTargetParser.fromTaskText("monitor"))
    }

    // --- app detection ---

    @Test
    fun `parses LINE app monitor`() {
        val result = MonitorTargetParser.fromTaskText("monitor Kenji on LINE")
        requireNotNull(result)
        assertEquals("LINE", result.app)
        assertEquals("Kenji", result.label)
    }

    @Test
    fun `parses wechat app monitor with space keeps trailing phrase in label`() {
        // The remove list contains "on wechat" (no space), so "on we chat" (with space) is NOT stripped.
        // The WeChat app is still detected by the \bwe\s*chat\b regex.
        val result = MonitorTargetParser.fromTaskText("monitor Lily on We Chat")
        requireNotNull(result)
        assertEquals("WeChat", result.app)
        assertTrue(result.label.startsWith("Lily"))
    }

    @Test
    fun `parses wechat app monitor without space`() {
        val result = MonitorTargetParser.fromTaskText("monitor Lily on WeChat")
        requireNotNull(result)
        assertEquals("WeChat", result.app)
    }

    @Test
    fun `parses google messages as Messages`() {
        val result = MonitorTargetParser.fromTaskText("monitor Steve on google messages")
        requireNotNull(result)
        assertEquals("Messages", result.app)
    }

    // --- monitor verb variants ---

    @Test
    fun `monitoring verb is recognized and stripped`() {
        val result = MonitorTargetParser.fromTaskText("monitoring Dad on WhatsApp")
        requireNotNull(result)
        assertEquals("Dad", result.label)
    }

    @Test
    fun `watching verb is recognized and stripped`() {
        val result = MonitorTargetParser.fromTaskText("watching Sam on Telegram")
        requireNotNull(result)
        assertEquals("Sam", result.label)
    }

    @Test
    fun `auto reply keyword is stripped`() {
        val result = MonitorTargetParser.fromTaskText("auto-reply Mom on WhatsApp")
        requireNotNull(result)
        assertEquals("Mom", result.label)
    }

    @Test
    fun `please can you start prefix is stripped`() {
        val result = MonitorTargetParser.fromTaskText("please can you start monitoring Bob on Telegram")
        requireNotNull(result)
        assertEquals("Bob", result.label)
    }

    @Test
    fun `for and from prepositions are stripped`() {
        val result1 = MonitorTargetParser.fromTaskText("monitor for Alice on WhatsApp")
        val result2 = MonitorTargetParser.fromTaskText("monitor messages from Alice on WhatsApp")
        requireNotNull(result1)
        requireNotNull(result2)
        assertEquals("Alice", result1.label)
        assertEquals("Alice", result2.label)
    }

    // --- displayLabel and supportedApps ---

    @Test
    fun `displayLabel combines label and app with on`() {
        val spec = MonitorTargetSpec("Mom", "Telegram")
        assertEquals("Mom on Telegram", spec.displayLabel)
    }

    @Test
    fun `supportedApps includes the five known apps`() {
        val expected = listOf("WhatsApp", "Telegram", "Messages", "LINE", "WeChat")
        assertEquals(expected, MonitorTargetSpec.supportedApps)
    }

    // --- case insensitivity ---

    @Test
    fun `Telegram detection is case insensitive`() {
        val result = MonitorTargetParser.fromTaskText("monitor Sarah on TELEGRAM")
        requireNotNull(result)
        assertEquals("Telegram", result.app)
        assertEquals("Sarah", result.label)
    }

    // --- label capitalization ---

    @Test
    fun `label is capitalized`() {
        val result = MonitorTargetParser.fromTaskText("monitor alice on WhatsApp")
        requireNotNull(result)
        assertEquals("Alice", result.label)
    }

    // --- edge: completely stop-words returns null ---

    @Test
    fun `only stop words returns null`() {
        assertNull(MonitorTargetParser.fromTaskText("please can you start monitoring"))
    }

    // --- supportedApps is non-empty and contains WhatsApp as default ---

    @Test
    fun `supportedApps contains WhatsApp as default fallback`() {
        assertTrue(MonitorTargetSpec.supportedApps.contains("WhatsApp"))
    }

    // --- tone default ---

    @Test
    fun `default tone is Casual`() {
        val spec = MonitorTargetSpec("Mom", "WhatsApp")
        assertEquals("Casual", spec.tone)
    }

    @Test
    fun `custom tone is preserved`() {
        val spec = MonitorTargetSpec("Boss", "Telegram", tone = "Formal")
        assertEquals("Formal", spec.tone)
    }

    // --- sanity: not null when label is present ---

    @Test
    fun `result is non-null for valid input`() {
        val result = MonitorTargetParser.fromTaskText("monitor Foo on Telegram")
        assertNotNull(result)
    }
}
