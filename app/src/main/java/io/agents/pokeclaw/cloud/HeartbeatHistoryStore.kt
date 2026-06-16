// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Persistent heartbeat history (KV-backed; last 1000 entries).
 *
 * Per the PRD (US-D-009):
 *  - timestamp, latencyMs, success, errorCategory
 *  - Used by HeartbeatHealthActivity to render timeline + success-rate + failure list.
 */
object HeartbeatHistoryStore {

    private const val TAG = "HeartbeatStore"
    private const val KV_KEY = "heartbeat_history_v1"
    private const val MAX_ENTRIES = 1000

    data class Entry(
        val timestamp: Long,
        val latencyMs: Long,
        val success: Boolean,
        val errorCategory: String? = null,
    )

    fun record(entry: Entry) {
        val list = load().toMutableList()
        list.add(entry)
        // Trim to last MAX_ENTRIES
        while (list.size > MAX_ENTRIES) {
            list.removeAt(0)
        }
        save(list)
        XLog.d(TAG, "record: success=${entry.success} latency=${entry.latencyMs}ms total=${list.size}")
    }

    fun load(): List<Entry> {
        val raw = KVUtils.getString(KV_KEY) ?: return emptyList()
        val result = mutableListOf<Entry>()
        raw.split("|").forEach { line ->
            val parts = line.split(",", limit = 4)
            if (parts.size >= 3) {
                result.add(Entry(
                    timestamp = parts[0].toLongOrNull() ?: return@forEach,
                    latencyMs = parts[1].toLongOrNull() ?: 0L,
                    success = parts[2] == "1",
                    errorCategory = parts.getOrNull(3)?.takeIf { it.isNotEmpty() && it != "-" },
                ))
            }
        }
        return result
    }

    private fun save(list: List<Entry>) {
        val encoded = list.joinToString("|") { entry ->
            val safeCat = (entry.errorCategory ?: "-").replace("|", "/").replace(",", "/")
            "${entry.timestamp},${entry.latencyMs},${if (entry.success) "1" else "0"},$safeCat"
        }
        KVUtils.putString(KV_KEY, encoded)
    }

    fun clear() {
        KVUtils.putString(KV_KEY, "")
    }

    /** Compute aggregate stats for a time window. */
    fun summary(windowMs: Long = 24L * 3600_000L): Map<String, Any> {
        val cutoff = System.currentTimeMillis() - windowMs
        val windowed = load().filter { it.timestamp >= cutoff }
        val total = windowed.size
        val success = windowed.count { it.success }
        val failure = total - success
        val byCategory = windowed.filter { !it.success }
            .groupingBy { it.errorCategory ?: "unknown" }
            .eachCount()
        val avgLatency = if (total > 0) windowed.sumOf { it.latencyMs } / total else 0L
        val successRate = if (total == 0) 0.0 else success.toDouble() / total
        return mapOf(
            "total" to total,
            "success" to success,
            "failure" to failure,
            "successRate" to successRate,
            "avgLatencyMs" to avgLatency,
            "failureByCategory" to byCategory,
        )
    }
}
