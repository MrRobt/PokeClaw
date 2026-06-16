// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MemorySuggester. Verifies the MIN_OCCURRENCES threshold,
 * the secret-phrase filter, and frequency-based ordering.
 */
class MemorySuggesterTest {

    @Test
    fun suggest_returnsEmpty_whenBelowMinOccurrences() {
        // 2 occurrences of the same phrase — below the 3-occurrence threshold.
        val msgs = listOf("I prefer dark mode", "yes i prefer dark mode")
        assertEquals(emptyList<String>(), MemorySuggester.suggest(msgs))
    }

    @Test
    fun suggest_detectsRepeatedPhrase() {
        val msgs = listOf(
            "I prefer dark mode always",
            "again I prefer dark mode today",
            "still I prefer dark mode here",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("expected at least one suggestion, got $out", out.isNotEmpty())
        assertTrue("expected 'i prefer dark' in $out", out.any { it == "i prefer dark" })
    }

    @Test
    fun suggest_ordersByFrequencyDescending() {
        // Implementation uses 3-token windows as candidate phrases.
        // We need alpha's 3-gram to strictly outnumber beta's 3-gram (>= MIN_OCCURRENCES=3).
        val msgs = listOf(
            "alpha alpha alpha gamma",     // alpha: 1
            "alpha alpha alpha delta",     // alpha: 2
            "alpha alpha alpha epsilon",   // alpha: 3
            "alpha alpha alpha zeta",      // alpha: 4
            "beta beta beta eta",          // beta: 1
            "beta beta beta theta",        // beta: 2
            "beta beta beta iota",         // beta: 3
        )
        val out = MemorySuggester.suggest(msgs)
        assertEquals(2, out.size)
        // The two 3-grams that appear most should be "alpha alpha alpha" (count 4) then "beta beta beta" (count 3).
        val alphaIdx = out.indexOf("alpha alpha alpha")
        val betaIdx = out.indexOf("beta beta beta")
        assertTrue("alpha should rank higher than beta, got $out", alphaIdx < betaIdx)
    }

    @Test
    fun suggest_filtersSecretPhrases() {
        // Even if "my secret token" repeats 3 times, it should never be returned.
        val msgs = listOf(
            "my secret token is here",
            "yes my secret token is here",
            "again my secret token is here",
        )
        assertEquals(emptyList<String>(), MemorySuggester.suggest(msgs))
    }

    @Test
    fun suggest_returnsEmpty_forShortMessages() {
        // Messages shorter than 6 chars or with < 3 tokens produce no candidate.
        val msgs = listOf("hi", "ok", "yes")
        assertEquals(emptyList<String>(), MemorySuggester.suggest(msgs))
    }

    @Test
    fun suggest_returnsEmpty_forEmptyInput() {
        assertEquals(emptyList<String>(), MemorySuggester.suggest(emptyList()))
    }

    // --- MIN_OCCURRENCES boundary ---

    @Test
    fun `MIN_OCCURRENCES constant is 3`() {
        assertEquals(3, MemorySuggester.MIN_OCCURRENCES)
    }

    @Test
    fun `exactly 3 occurrences of the same phrase is included`() {
        val msgs = listOf(
            "I prefer dark mode please",
            "I prefer dark mode today",
            "I prefer dark mode here",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("3 occurrences should be included, got $out", out.contains("i prefer dark"))
    }

    @Test
    fun `2 occurrences of the same phrase is excluded`() {
        val msgs = listOf(
            "I prefer dark mode please",
            "I prefer dark mode today",
        )
        val out = MemorySuggester.suggest(msgs)
        assertEquals("only 2 occurrences must not appear, got $out", emptyList<String>(), out)
    }

    // --- length / token-count pre-filters ---

    @Test
    fun `messages with fewer than 6 chars are skipped`() {
        // "hi ok yes" is 3 msgs of 2 chars each — even with 3 occurrences, length filter
        // drops them.
        val msgs = listOf("hi", "hi", "hi")
        val out = MemorySuggester.suggest(msgs)
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `messages with fewer than 3 tokens are skipped`() {
        // 6+ chars but <3 tokens → sliding window produces no candidate.
        val msgs = listOf("hello there", "hello there", "hello there")
        val out = MemorySuggester.suggest(msgs)
        assertEquals("two-token messages must produce no candidates, got $out", emptyList<String>(), out)
    }

    // --- secret filter variants ---

    @Test
    fun `secret phrase with key=value form is also filtered`() {
        // "api_key=xxx" should be filtered even when repeated >= 3 times.
        val msgs = listOf(
            "my api_key=secret123 here",
            "your api_key=secret123 here",
            "his api_key=secret123 here",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("secret phrases must be filtered, got $out", out.none { it.contains("api_key") })
    }

    @Test
    fun `secret phrase does not poison non-secret 3-grams in the same message`() {
        // Mixing: one message has both "my favorite coffee" and a secret. The non-secret
        // 3-gram should still count across messages.
        val msgs = listOf(
            "my favorite coffee here",
            "my api_key=secret123 here",
            "my favorite coffee there",
            "my favorite coffee everywhere",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue(
            "non-secret 3-gram 'my favorite coffee' should be detected across 3 messages, got $out",
            out.any { it == "my favorite coffee" },
        )
    }

    // --- normalization ---

    @Test
    fun `trims leading and trailing whitespace before tokenizing`() {
        val msgs = listOf(
            "  I prefer dark mode  ",
            "\tI prefer dark mode\n",
            "I prefer dark mode",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("normalized 3-gram should match, got $out", out.any { it == "i prefer dark" })
    }

    @Test
    fun `is case-insensitive (uppercase occurrences match lowercase counter`() {
        val msgs = listOf(
            "I PREFER DARK MODE",
            "i prefer dark mode",
            "I Prefer Dark Mode",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("mixed-case matches lowercase, got $out", out.any { it == "i prefer dark" })
    }

    // --- multi-gram sliding window behavior ---

    @Test
    fun `4-token message produces 2 overlapping 3-grams`() {
        // "alpha beta gamma delta" → "alpha beta gamma" + "beta gamma delta"
        val msgs = listOf(
            "alpha beta gamma delta",
            "alpha beta gamma delta",
            "alpha beta gamma delta",
        )
        val out = MemorySuggester.suggest(msgs)
        assertTrue("first overlapping 3-gram, got $out", out.contains("alpha beta gamma"))
        assertTrue("second overlapping 3-gram, got $out", out.contains("beta gamma delta"))
    }

    @Test
    fun `all qualifying 3-grams are returned sorted by frequency`() {
        val msgs = listOf(
            "alpha alpha alpha beta",
            "alpha alpha alpha beta",
            "alpha alpha alpha gamma",
            "alpha alpha alpha gamma",
            "alpha alpha alpha gamma",
        )
        val out = MemorySuggester.suggest(msgs)
        // "alpha alpha alpha" appears 5 times; "alpha alpha beta" 2x (below threshold); "alpha alpha gamma" 3x.
        assertTrue("'alpha alpha alpha' should win, got $out", out.first() == "alpha alpha alpha")
        assertTrue("'alpha alpha gamma' should be second, got $out", out.contains("alpha alpha gamma"))
        assertTrue("'alpha alpha beta' must not pass (only 2), got $out", !out.contains("alpha alpha beta"))
    }
}
