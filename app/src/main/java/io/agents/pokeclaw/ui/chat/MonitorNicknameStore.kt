// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.json.JSONObject

/**
 * Persistent nickname override table for monitor targets
 * (US-D-023-MONITOR-NICKNAMES).
 *
 * Stored as a single JSON object under [KVUtils.KEY_MONITOR_NICKNAMES]:
 *   { "<stableId>": "<nickname>", ... }
 *
 * All operations are best-effort: corrupt JSON returns an empty table
 * rather than throwing, so a fresh install can never crash on legacy data.
 */
object MonitorNicknameStore {

    private const val TAG = "MonitorNicknameStore"
    const val MAX_NICKNAME_LEN = 60

    /** Set a nickname for [stableId]. Empty/whitespace clears it. */
    fun setNickname(stableId: String, nickname: String) {
        val key = stableId.trim()
        if (key.isEmpty()) return
        val cleaned = nickname.trim()
        val table = readTable().toMutableMap()
        if (cleaned.isEmpty()) {
            table.remove(key)
        } else {
            if (cleaned.length > MAX_NICKNAME_LEN) {
                XLog.w(TAG, "nickname: truncated id=$key len=${cleaned.length}")
            }
            table[key] = cleaned.take(MAX_NICKNAME_LEN)
        }
        persistTable(table)
        XLog.i(TAG, "monitor-nickname: set id=$key present=${table.containsKey(key)}")
    }

    /** Get the nickname for [stableId], or null when unset. */
    fun getNickname(stableId: String): String? {
        if (stableId.isBlank()) return null
        return readTable()[stableId.trim()]
    }

    /** All nicknames keyed by stableId. */
    fun listAll(): Map<String, String> = readTable()

    /** Remove the nickname for [stableId]. */
    fun clearAll(stableId: String) {
        if (stableId.isBlank()) return
        val table = readTable().toMutableMap()
        if (table.remove(stableId.trim()) != null) {
            persistTable(table)
            XLog.i(TAG, "monitor-nickname: clear id=$stableId")
        }
    }

    /** Wipe the entire nickname table. */
    fun clearEverything() {
        KVUtils.setMonitorNicknames("")
        XLog.i(TAG, "monitor-nickname: cleared all")
    }

    private fun readTable(): Map<String, String> {
        val raw = runCatching { KVUtils.getMonitorNicknames() }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val out = HashMap<String, String>(obj.length())
            // Collect keys into a list first — JSON object iteration depends on the runtime
            // version of org.json (Android stub has no `keySet()`); the iterator API is the
            // most portable.
            val keys = ArrayList<String>(obj.length())
            val it = obj.keys()
            while (it.hasNext()) keys.add(it.next())
            for (k in keys) {
                val v = obj.opt(k)
                if (v is String && v.isNotEmpty()) out[k] = v
            }
            out
        }.getOrElse { e ->
            XLog.w(TAG, "nickname: parse failed: ${e.message}")
            emptyMap()
        }
    }

    private fun persistTable(table: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in table) obj.put(k, v)
        KVUtils.setMonitorNicknames(obj.toString())
    }
}
