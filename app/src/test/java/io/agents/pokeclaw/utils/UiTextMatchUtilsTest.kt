package io.agents.pokeclaw.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextMatchUtilsTest {

    @Test
    fun `matches exact text ignoring punctuation and case`() {
        assertTrue(UiTextMatchUtils.matchesExactOrNormalized("Allow!", "allow"))
        assertTrue(UiTextMatchUtils.matchesExactOrNormalized("Monica (Work)", "monica work"))
    }

    @Test
    fun `relaxed matching allows extra words around query`() {
        assertTrue(UiTextMatchUtils.matchesRelaxed("Allow to open", "allow"))
        assertTrue(UiTextMatchUtils.matchesRelaxed("Send message to Monica", "monica"))
    }

    @Test
    fun `relaxed matching handles digit formatting differences`() {
        assertTrue(UiTextMatchUtils.matchesRelaxed("+1 (604) 555-1234", "6045551234"))
    }

    @Test
    fun `exact matching does not overmatch partial short text`() {
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("Open", "op"))
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("Allow to open", "allow"))
    }

    @Test
    fun `relaxed matching still rejects unrelated text`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed("Telegram", "whatsapp"))
    }

    // --- normalizeText ---

    @Test
    fun `normalizeText returns empty for null`() {
        assertEquals("", UiTextMatchUtils.normalizeText(null))
    }

    @Test
    fun `normalizeText strips punctuation and lowercases`() {
        assertEquals("hello world", UiTextMatchUtils.normalizeText("Hello,   World!!"))
    }

    @Test
    fun `normalizeText returns empty for all punctuation`() {
        assertEquals("", UiTextMatchUtils.normalizeText("!!!---???"))
    }

    // --- digitsOnly ---

    @Test
    fun `digitsOnly returns empty for null`() {
        assertEquals("", UiTextMatchUtils.digitsOnly(null))
    }

    @Test
    fun `digitsOnly keeps only numeric characters`() {
        assertEquals("6045551234", UiTextMatchUtils.digitsOnly("604-555-1234"))
    }

    // --- matchesExactOrNormalized edge cases ---

    @Test
    fun `matchesExactOrNormalized returns false for null candidate`() {
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized(null as CharSequence?, "x"))
    }

    @Test
    fun `matchesExactOrNormalized returns false for null query`() {
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("Open", null))
    }

    @Test
    fun `matchesExactOrNormalized returns false for empty candidate or query`() {
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("", "x"))
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("x", ""))
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("   ", "x"))
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("x", "   "))
    }

    @Test
    fun `matchesExactOrNormalized matches full text case-insensitively`() {
        assertTrue(UiTextMatchUtils.matchesExactOrNormalized("ALLOW", "allow"))
        assertTrue(UiTextMatchUtils.matchesExactOrNormalized("Allow", "ALLOW"))
    }

    @Test
    fun `matchesExactOrNormalized does not substring-match short text`() {
        // exact mode does not allow contains() even for >=3 chars
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("Monica (Work)", "Monica"))
    }

    // --- matchesRelaxed edge cases ---

    @Test
    fun `matchesRelaxed returns false for null candidate`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed(null as CharSequence?, "x"))
    }

    @Test
    fun `matchesRelaxed returns false for null query`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed("Telegram", null))
    }

    @Test
    fun `matchesRelaxed returns false for empty input`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed("", "x"))
        assertFalse(UiTextMatchUtils.matchesRelaxed("x", ""))
    }

    @Test
    fun `matchesRelaxed substring match only works for query length at least 3`() {
        // 2-char normalized query should NOT substring-match
        assertFalse(UiTextMatchUtils.matchesRelaxed("Open App", "op"))
    }

    @Test
    fun `matchesRelaxed substring match works for query length at least 3`() {
        // 3+ char normalized query substring-matches inside candidate
        assertTrue(UiTextMatchUtils.matchesRelaxed("Open App", "app"))
    }

    @Test
    fun `matchesRelaxed digit query shorter than 4 digits and 3 chars is rejected`() {
        // 2-digit query: digit branch requires >=4, substring branch requires >=3 → both skip
        assertFalse(UiTextMatchUtils.matchesRelaxed("1234567", "12"))
    }

    @Test
    fun `matchesRelaxed 3-digit query matches via substring branch (not digit branch)`() {
        // 3-digit query: digit branch is skipped (3<4), but substring branch accepts (3>=3)
        assertTrue(UiTextMatchUtils.matchesRelaxed("1234567", "123"))
    }

    @Test
    fun `matchesRelaxed long digit query substring-matches inside longer digit candidate`() {
        // 4+ digit query inside longer candidate digit string
        assertTrue(UiTextMatchUtils.matchesRelaxed("555-1234-9999", "1234"))
    }

    @Test
    fun `matchesExactOrNormalized does not digit-substring-match even for 4+ digit queries`() {
        // exact mode disables the `contains` branch for digits too
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("555-1234-9999", "1234"))
    }

    @Test
    fun `matchesRelaxed matches full phone number across formatting`() {
        assertTrue(UiTextMatchUtils.matchesRelaxed("+1 (604) 555-1234", "6045551234"))
        assertTrue(UiTextMatchUtils.matchesRelaxed("+1 (604) 555-1234", "+16045551234"))
    }

    // --- punctuation-only candidate ---

    @Test
    fun `matchesExactOrNormalized rejects punctuation-only candidate`() {
        // After normalize, candidate becomes empty
        assertFalse(UiTextMatchUtils.matchesExactOrNormalized("!!!", "x"))
    }

    @Test
    fun `matchesRelaxed rejects punctuation-only candidate`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed("!!!", "x"))
    }

    // --- both empty after normalize ---

    @Test
    fun `matchesRelaxed rejects when both sides normalize to empty`() {
        assertFalse(UiTextMatchUtils.matchesRelaxed("!!!", "???"))
    }
}
