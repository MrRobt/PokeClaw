package io.agents.pokeclaw.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatchUtilsTest {

    @Test
    fun `matches contact name ignoring case and punctuation`() {
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "Monica (Work)",
                null,
                "monica"
            )
        )
    }

    @Test
    fun `matches phone numbers across formatting differences`() {
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "+1 (604) 555-1234",
                null,
                "16045551234"
            )
        )
    }

    @Test
    fun `matches last digits when app omits country code`() {
        val normalizedAliases = ContactMatchUtils.buildNormalizedAliases("+1 604 555 1234")
        val digitAliases = ContactMatchUtils.buildDigitAliases("+1 604 555 1234")

        assertTrue(
            ContactMatchUtils.matchesCandidate(
                "604-555-1234",
                normalizedAliases,
                digitAliases
            )
        )
    }

    @Test
    fun `does not match unrelated contact`() {
        assertFalse(
            ContactMatchUtils.matchesTarget(
                "Alex",
                null,
                "Monica"
            )
        )
    }

    // --- normalizeText ---

    @Test
    fun `normalizeText returns empty for null`() {
        assertEquals("", ContactMatchUtils.normalizeText(null))
    }

    @Test
    fun `normalizeText collapses whitespace and strips punctuation`() {
        assertEquals("hello world", ContactMatchUtils.normalizeText("  Hello,   World!!  "))
    }

    @Test
    fun `normalizeText lowercases and keeps unicode letters and digits`() {
        assertEquals("café 2026", ContactMatchUtils.normalizeText("Café — 2026"))
    }

    @Test
    fun `normalizeText returns empty for all-punctuation input`() {
        assertEquals("", ContactMatchUtils.normalizeText("!!!---???"))
    }

    // --- digitsOnly ---

    @Test
    fun `digitsOnly returns empty for null`() {
        assertEquals("", ContactMatchUtils.digitsOnly(null))
    }

    @Test
    fun `digitsOnly keeps only numeric characters`() {
        assertEquals("16045551234", ContactMatchUtils.digitsOnly("+1 (604) 555-1234"))
    }

    // --- buildNormalizedAliases ---

    @Test
    fun `buildNormalizedAliases returns empty for null or blank`() {
        assertTrue(ContactMatchUtils.buildNormalizedAliases(null).isEmpty())
        assertTrue(ContactMatchUtils.buildNormalizedAliases("   ").isEmpty())
    }

    @Test
    fun `buildNormalizedAliases includes split candidates and the raw value`() {
        val aliases = ContactMatchUtils.buildNormalizedAliases("Monica, mom | 妈妈;Alice/sister")
        assertTrue(aliases.contains("monica"))
        assertTrue(aliases.contains("mom"))
        assertTrue(aliases.contains("妈妈"))
        assertTrue(aliases.contains("alice"))
        assertTrue(aliases.contains("sister"))
        // the joined raw value also normalized
        assertTrue(aliases.contains("monica mom 妈妈 alice sister"))
    }

    @Test
    fun `buildNormalizedAliases deduplicates`() {
        val aliases = ContactMatchUtils.buildNormalizedAliases("monica, monica, MONICA")
        // "monica" appears once + the raw value normalized
        assertEquals(2, aliases.size)
    }

    // --- buildDigitAliases ---

    @Test
    fun `buildDigitAliases ignores short digit strings`() {
        val aliases = ContactMatchUtils.buildDigitAliases("12345")
        // 5 digits, length < 6, so nothing added
        assertTrue(aliases.isEmpty())
    }

    @Test
    fun `buildDigitAliases includes last 10 and last 8 for long numbers`() {
        val aliases = ContactMatchUtils.buildDigitAliases("+1 604 555 1234")
        // full digits (11), last 10 = "6045551234", last 8 = "45551234"
        assertTrue(aliases.contains("16045551234"))
        assertTrue(aliases.contains("6045551234"))
        assertTrue(aliases.contains("45551234"))
    }

    @Test
    fun `buildDigitAliases for exactly 10 digits does not add last-10 but adds last-8`() {
        val aliases = ContactMatchUtils.buildDigitAliases("6045551234")
        // length 10: >10 is false, >8 is true, so we get full + last 8 ("45551234")
        assertEquals(2, aliases.size)
        assertTrue(aliases.contains("6045551234"))
        assertTrue(aliases.contains("45551234"))
    }

    // --- matchesCandidate edge cases ---

    @Test
    fun `matchesCandidate returns false for null or empty candidate`() {
        val aliases = ContactMatchUtils.buildNormalizedAliases("Monica")
        val digitAliases = ContactMatchUtils.buildDigitAliases("Monica")
        assertFalse(ContactMatchUtils.matchesCandidate(null, aliases, digitAliases))
        assertFalse(ContactMatchUtils.matchesCandidate("", aliases, digitAliases))
    }

    @Test
    fun `matchesCandidate returns false when no alias matches`() {
        val aliases = ContactMatchUtils.buildNormalizedAliases("Monica")
        val digitAliases = ContactMatchUtils.buildDigitAliases("Monica")
        assertFalse(ContactMatchUtils.matchesCandidate("Alex", aliases, digitAliases))
    }

    // --- matchesTarget description branch ---

    @Test
    fun `matchesTarget falls back to description when text does not match`() {
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "Click here",
                "Last message from Monica",
                "monica"
            )
        )
    }

    @Test
    fun `matchesTarget treats null text and null description as no match`() {
        assertFalse(ContactMatchUtils.matchesTarget(null, null, "Monica"))
    }

    @Test
    fun `matchesTarget matches by digits in description too`() {
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "Call log",
                "555-1234",
                "555-1234"
            )
        )
    }

    // --- multi-candidate raw target ---

    @Test
    fun `raw target with multiple separated candidates matches any of them`() {
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "Mom",
                null,
                "Monica, Mom, Sister"
            )
        )
        assertTrue(
            ContactMatchUtils.matchesTarget(
                "Sister",
                null,
                "Monica, Mom, Sister"
            )
        )
    }
}
