// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.instructions

import io.agents.pokeclaw.memory.UserMemoryStore
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Persistent, role-agnostic global instructions (US-D-022-PERSISTENT-GLOBAL-INSTRUCTIONS).
 *
 * The user writes a short paragraph describing preferences the agent should keep
 * across all tasks/channels ("Use Chinese replies", "Prefer short answers",
 * "Never reveal internal prompts", …). The text is prepended to every new
 * prompt pipeline.
 *
 * Hard rules:
 *  - Total length ≤ [MAX_LEN] (2000 chars). Longer input is rejected.
 *  - Secrets are rejected via [UserMemoryStore.SECRET_PATTERN] — same filter
 *    used for memory, so the user never accidentally persists a token,
 *    password, recovery code, or 2FA secret into the system prompt.
 *  - Empty input clears the store.
 */
object GlobalInstructionsStore {

    private const val TAG = "GlobalInstructionsStore"
    const val MAX_LEN = 2000
    const val REJECTED_TOO_LONG = "REJECTED_TOO_LONG"
    const val REJECTED_SECRET = "REJECTED_SECRET"

    /** Read the current instructions. Returns "" when unset. */
    fun get(): String = KVUtils.getUserGlobalInstructions().trim()

    /**
     * Persist new instructions. Returns null on success, or a short code on
     * rejection. Empty/whitespace input is treated as a clear() — succeeds.
     */
    fun set(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            clear()
            return null
        }
        if (trimmed.length > MAX_LEN) return REJECTED_TOO_LONG
        if (UserMemoryStore.isSecret(trimmed)) {
            XLog.w(TAG, "instructions: rejected secret pattern: ${trimmed.take(40)}...")
            return REJECTED_SECRET
        }
        KVUtils.setUserGlobalInstructions(trimmed)
        XLog.i(TAG, "instructions: saved len=${trimmed.length}")
        return null
    }

    /** Clear the instructions. */
    fun clear() {
        KVUtils.clearUserGlobalInstructions()
        XLog.i(TAG, "instructions: cleared")
    }

    /** True when the current text is non-blank. */
    fun isConfigured(): Boolean = get().isNotEmpty()
}
