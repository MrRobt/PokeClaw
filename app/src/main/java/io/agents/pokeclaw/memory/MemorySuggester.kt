// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.memory

import io.agents.pokeclaw.utils.XLog

/**
 * Detects candidate memory entries from a conversation tail. Per US-D-018:
 *  - Auto-suggest only after the same key phrase recurs ≥ [MIN_OCCURRENCES] times.
 *  - Returns text the user should be asked to confirm; nothing is written
 *    until the user approves (memory suggestion flow lives in the chat layer).
 *  - Secrets are filtered out before the suggestion is offered.
 */
object MemorySuggester {

    private const val TAG = "MemorySuggester"
    const val MIN_OCCURRENCES = 3

    /**
     * Scan [recentMessages] for repeated phrases (length ≥ 6) that occur at least
     * [MIN_OCCURRENCES] times. Case-insensitive matching. Returns suggestions in
     * order of frequency.
     */
    fun suggest(recentMessages: List<String>): List<String> {
        if (recentMessages.size < MIN_OCCURRENCES) return emptyList()
        val counts = HashMap<String, Int>()
        for (msg in recentMessages) {
            val cleaned = msg.lowercase().trim()
            if (cleaned.length < 6) continue
            // Use sliding 6-word windows as candidate phrases.
            val tokens = cleaned.split(Regex("\\s+"))
            if (tokens.size < 3) continue
            for (i in 0..tokens.size - 3) {
                val phrase = tokens.subList(i, i + 3).joinToString(" ")
                if (UserMemoryStore.isSecret(phrase)) continue
                counts[phrase] = (counts[phrase] ?: 0) + 1
            }
        }
        val candidates = counts.filterValues { it >= MIN_OCCURRENCES }
            .entries.sortedByDescending { it.value }
            .map { it.key }
        if (candidates.isNotEmpty()) {
            XLog.d(TAG, "memory: suggest count=${candidates.size} top=${candidates.first()}")
        }
        return candidates
    }
}