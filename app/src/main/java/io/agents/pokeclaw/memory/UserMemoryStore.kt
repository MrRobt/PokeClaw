// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.memory

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-user memory store (US-D-018-USER-MEMORY).
 *
 * Stores short user-authored facts ("I prefer dark mode", "my wife's name is Sarah")
 * that the agent should keep in mind across sessions. Entries are KV-persisted as a
 * JSON array under [KVUtils.KEY_USER_MEMORY].
 *
 * Hard rules:
 *  - Each entry ≤ 1000 characters (enforced in [add]).
 *  - Total entries ≤ 200; oldest by [MemoryEntry.createdAt] are dropped first.
 *  - Secrets (tokens, passwords, recovery codes, …) are rejected at write time by
 *    [SECRET_PATTERN] — see [isSecret].
 *  - The store never persists secrets, bot tokens, API keys, recovery codes, or
 *    2FA / OTP / mnemonic / seed phrases.
 */
object UserMemoryStore {

    private const val TAG = "UserMemoryStore"
    const val MAX_ENTRY_LEN = 1000
    const val MAX_ENTRIES = 200
    const val REJECTED_SECRET = "REJECTED_SECRET"
    const val REJECTED_TOO_LONG = "REJECTED_TOO_LONG"

    /**
     * Patterns that should never end up in memory.
     * Each pattern is anchored to either a key/value pair (e.g. `token=...`) or
     * a stand-alone word like "mnemonic". The word-boundary regexes match across
     * common separators.
     */
    val SECRET_PATTERN: Regex = Regex(
        "(?i)(" +
            "token|password|passwd|secret|api[-_]?key|bearer|jwt|" +
            "otp|2fa|two[-_]?factor|recovery|seed|mnemonic|private[-_]?key" +
            ")\\b" +
            // If preceded by `=` or `:` or `: `, also require a non-empty value on the right.
            "(\\s*[:=]\\s*\\S+)?"
    )

    data class MemoryEntry(
        val id: String,
        val text: String,
        val tags: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val lastUsedAt: Long = createdAt,
        val useCount: Int = 0,
        val source: Source = Source.USER,
    ) {
        enum class Source { USER, SUGGESTED }
    }

    sealed class AddResult {
        data class Accepted(val entry: MemoryEntry) : AddResult()
        data class Rejected(val reason: String, val preview: String) : AddResult()
    }

    /**
     * Add a memory entry. Returns [AddResult.Rejected] with [REJECTED_SECRET] if
     * the text matches [SECRET_PATTERN], or [REJECTED_TOO_LONG] if it exceeds
     * [MAX_ENTRY_LEN] characters.
     */
    fun add(
        text: String,
        tags: List<String> = emptyList(),
        source: MemoryEntry.Source = MemoryEntry.Source.USER,
    ): AddResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AddResult.Rejected("EMPTY", trimmed.take(40))
        if (trimmed.length > MAX_ENTRY_LEN) return AddResult.Rejected(REJECTED_TOO_LONG, trimmed.take(40))
        if (isSecret(trimmed)) {
            XLog.w(TAG, "memory: rejected secret pattern: ${trimmed.take(40)}...")
            return AddResult.Rejected(REJECTED_SECRET, trimmed.take(40))
        }
        val entry = MemoryEntry(
            id = "mem-" + java.util.UUID.randomUUID().toString().take(8),
            text = trimmed,
            tags = tags,
            source = source,
        )
        val list = listAll().toMutableList()
        list += entry
        // FIFO cap; keep newest by createdAt.
        val trimmedList = if (list.size > MAX_ENTRIES) {
            list.sortedByDescending { it.createdAt }.take(MAX_ENTRIES)
        } else list
        persist(trimmedList)
        XLog.d(TAG, "memory: added id=${entry.id} len=${entry.text.length} source=${source.name}")
        return AddResult.Accepted(entry)
    }

    /** All memory entries, newest first by createdAt. */
    fun listAll(): List<MemoryEntry> {
        val raw = KVUtils.getUserMemory()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MemoryEntry(
                    id = o.optString("id"),
                    text = o.optString("text"),
                    tags = o.optJSONArray("tags")?.let { ja ->
                        (0 until ja.length()).map { ja.getString(it) }
                    } ?: emptyList(),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    lastUsedAt = o.optLong("lastUsedAt", o.optLong("createdAt", System.currentTimeMillis())),
                    useCount = o.optInt("useCount", 0),
                    source = runCatching { MemoryEntry.Source.valueOf(o.optString("source", "USER")) }
                        .getOrDefault(MemoryEntry.Source.USER),
                )
            }.sortedByDescending { it.createdAt }
        }.getOrElse { e ->
            XLog.w(TAG, "memory: parse failed, clearing: ${e.message}")
            KVUtils.clearUserMemory()
            emptyList()
        }
    }

    fun get(id: String): MemoryEntry? = listAll().firstOrNull { it.id == id }

    fun delete(id: String): Boolean {
        val list = listAll().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) persist(list)
        return removed
    }

    fun touchLastUsed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val list = listAll().map { e ->
            if (e.id in ids) e.copy(lastUsedAt = now, useCount = e.useCount + 1) else e
        }
        persist(list)
    }

    /** Most recently used first, capped to [limit]. */
    fun mostRecent(limit: Int = 5): List<MemoryEntry> =
        listAll().sortedByDescending { it.lastUsedAt }.take(limit.coerceAtLeast(0))

    fun exportJson(): String {
        val arr = JSONArray()
        for (e in listAll()) arr.put(e.toJson())
        return arr.toString(2)
    }

    fun clearAll() = KVUtils.clearUserMemory()

    fun isSecret(text: String): Boolean = SECRET_PATTERN.containsMatchIn(text)

    private fun persist(list: List<MemoryEntry>) {
        val arr = JSONArray()
        for (e in list) arr.put(e.toJson())
        KVUtils.setUserMemory(arr.toString())
    }

    private fun MemoryEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("tags", JSONArray(tags))
        put("createdAt", createdAt)
        put("lastUsedAt", lastUsedAt)
        put("useCount", useCount)
        put("source", source.name)
    }
}