// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Pure helpers for the CS-AI per-reply credit toast
 * (US-D-027-CS-AI-CREDIT-AWARE-TOAST).
 *
 * The CS-AI client channel is not yet wired, but the formatter is
 * already pinned so the eventual wiring just has to call [format]
 * with a real credit count. When the API is silent (cloud not yet
 * emitting the credit field), callers should pass null and we fall
 * back to [FALLBACK_MESSAGE] ("积分计量未启用").
 */
object CsAiCreditToast {

    /** Display template; the placeholder is replaced with the integer credit count. */
    const val MESSAGE_TEMPLATE = "本次回复消耗约 %d 积分"

    /** Shown when the cloud hasn't filled in the credit field for this reply. */
    const val FALLBACK_MESSAGE = "积分计量未启用"

    /**
     * Build a toast text from a known credit count.
     *   - null         → [FALLBACK_MESSAGE]
     *   - 0 or below   → [FALLBACK_MESSAGE] (metering disabled or refunded)
     *   - positive n   → "本次回复消耗约 n 积分"
     */
    fun format(credits: Long?): String {
        if (credits == null || credits <= 0L) return FALLBACK_MESSAGE
        return MESSAGE_TEMPLATE.format(credits)
    }

    /**
     * Compute the credit count from a token total and the per-1k rate.
     * Returns null when metering is disabled (rate <= 0) or the input is
     * negative; in both cases the UI should call [format] with null and
     * surface [FALLBACK_MESSAGE].
     */
    fun creditsFromTokens(tokenCount: Int, creditsPer1k: Double): Long? {
        if (tokenCount <= 0) return null
        if (creditsPer1k <= 0.0) return null
        // tokens * (creditsPer1k / 1000), rounded UP to the nearest integer
        // so a 1-token reply is never free.
        val exact = tokenCount.toDouble() * creditsPer1k / 1000.0
        return ceil(exact).roundToIntCoerceAtLeastOne()
    }

    /** Convenience: format a toast from a token count + per-1k rate. */
    fun formatFromTokens(tokenCount: Int, creditsPer1k: Double): String =
        format(creditsFromTokens(tokenCount, creditsPer1k))

    private fun Double.roundToIntCoerceAtLeastOne(): Long {
        val rounded = this.roundToLong()
        return if (rounded < 1L) 1L else rounded
    }
}
