// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CsAiCreditToastTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    @Test
    fun format_nullCredits_returnsFallback() {
        assertEquals(CsAiCreditToast.FALLBACK_MESSAGE, CsAiCreditToast.format(null))
    }

    @Test
    fun format_zeroCredits_returnsFallback() {
        assertEquals(CsAiCreditToast.FALLBACK_MESSAGE, CsAiCreditToast.format(0L))
    }

    @Test
    fun format_negativeCredits_returnsFallback() {
        assertEquals(CsAiCreditToast.FALLBACK_MESSAGE, CsAiCreditToast.format(-5L))
    }

    @Test
    fun format_positiveCredits_returnsTemplate() {
        assertEquals("本次回复消耗约 3 积分", CsAiCreditToast.format(3L))
    }

    @Test
    fun format_largeCredits_returnsTemplate() {
        assertEquals("本次回复消耗约 999 积分", CsAiCreditToast.format(999L))
    }

    @Test
    fun creditsFromTokens_zeroOrNegativeTokens_returnsNull() {
        assertNull(CsAiCreditToast.creditsFromTokens(0, 1.0))
        assertNull(CsAiCreditToast.creditsFromTokens(-1, 1.0))
    }

    @Test
    fun creditsFromTokens_zeroOrNegativeRate_returnsNull() {
        assertNull(CsAiCreditToast.creditsFromTokens(500, 0.0))
        assertNull(CsAiCreditToast.creditsFromTokens(500, -0.5))
    }

    @Test
    fun creditsFromTokens_oneToken_alwaysRoundsUpToOne() {
        // Even at a tiny rate, 1 token should not be free.
        assertEquals(1L, CsAiCreditToast.creditsFromTokens(1, 0.01))
    }

    @Test
    fun creditsFromTokens_thousandTokens_atOnePer1k_equalsOne() {
        assertEquals(1L, CsAiCreditToast.creditsFromTokens(1000, 1.0))
    }

    @Test
    fun creditsFromTokens_thousandTokens_atTwoPer1k_equalsTwo() {
        assertEquals(2L, CsAiCreditToast.creditsFromTokens(1000, 2.0))
    }

    @Test
    fun creditsFromTokens_500Tokens_atOnePer1k_roundsUpToOne() {
        // 0.5 credits rounds up to 1, never 0.
        assertEquals(1L, CsAiCreditToast.creditsFromTokens(500, 1.0))
    }

    @Test
    fun creditsFromTokens_1500Tokens_atOnePer1k_equalsTwo() {
        // 1.5 → 2
        assertEquals(2L, CsAiCreditToast.creditsFromTokens(1500, 1.0))
    }

    @Test
    fun creditsFromTokens_8000Tokens_atOnePoint5Per1k_equalsTwelve() {
        // 12.0
        assertEquals(12L, CsAiCreditToast.creditsFromTokens(8000, 1.5))
    }

    @Test
    fun formatFromTokens_disabledRate_returnsFallback() {
        assertEquals(
            CsAiCreditToast.FALLBACK_MESSAGE,
            CsAiCreditToast.formatFromTokens(2000, 0.0)
        )
    }

    @Test
    fun formatFromTokens_activeRate_returnsTemplate() {
        assertEquals(
            "本次回复消耗约 5 积分",
            CsAiCreditToast.formatFromTokens(5000, 1.0)
        )
    }

    @Test
    fun formatFromTokens_zeroTokens_returnsFallback() {
        assertEquals(
            CsAiCreditToast.FALLBACK_MESSAGE,
            CsAiCreditToast.formatFromTokens(0, 1.0)
        )
    }

    @Test
    fun fallbackMessage_isNonEmpty() {
        assert(CsAiCreditToast.FALLBACK_MESSAGE.isNotBlank())
    }
}
