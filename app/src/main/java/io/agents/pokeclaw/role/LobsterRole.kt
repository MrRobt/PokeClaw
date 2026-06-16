// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.role

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lobster role (小虾角色) — end-side persistent, cloud-adjustable personality definition.
 *
 * R2 US-B-CMP-1-1: A role controls the agent's behavior for a given context
 * (e.g. 通用助手, 工作模式, 家庭模式, 谨慎模式). Roles are persisted in MMKV
 * (lobster_roles_v1) and the active role id is stored in
 * [io.agents.pokeclaw.utils.KVUtils.LOBSTER_ACTIVE_ROLE_ID].
 *
 * The [systemPrompt] is injected as a separate section in the agent's system
 * prompt, AFTER the safety rules and tool rules, so it can never override them.
 */
data class LobsterRole(
    val id: String,
    val name: String,
    val role: String,
    val status: Status = Status.ENABLED,
    val duties: List<String> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val source: Source = Source.USER,
) {
    enum class Status { ENABLED, PAUSED, DISABLED }
    enum class Source { USER, SYSTEM, CLOUD }

    data class HistoryEntry(
        val changedAt: Long,
        val fromStatus: Status?,
        val toStatus: Status,
        val note: String = "",
    )

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("role", role)
        obj.put("status", status.name)
        obj.put("duties", JSONArray(duties))
        obj.put("version", version)
        obj.put("updatedAt", updatedAt)
        obj.put("source", source.name)
        val historyArr = JSONArray()
        history.forEach { entry ->
            val e = JSONObject()
            e.put("changedAt", entry.changedAt)
            entry.fromStatus?.let { e.put("fromStatus", it.name) }
            e.put("toStatus", entry.toStatus.name)
            e.put("note", entry.note)
            historyArr.put(e)
        }
        obj.put("history", historyArr)
        return obj.toString()
    }

    companion object {
        fun fromJson(text: String): LobsterRole? {
            return runCatching {
                val obj = JSONObject(text)
                val dutiesArr = obj.optJSONArray("duties")
                val duties = mutableListOf<String>()
                if (dutiesArr != null) {
                    for (i in 0 until dutiesArr.length()) duties.add(dutiesArr.optString(i, ""))
                }
                val historyArr = obj.optJSONArray("history")
                val history = mutableListOf<HistoryEntry>()
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        val e = historyArr.optJSONObject(i) ?: continue
                        history.add(
                            HistoryEntry(
                                changedAt = e.optLong("changedAt", 0L),
                                fromStatus = e.optString("fromStatus", "").takeIf { it.isNotEmpty() }?.let { runCatching { Status.valueOf(it) }.getOrNull() },
                                toStatus = runCatching { Status.valueOf(e.optString("toStatus", "ENABLED")) }.getOrDefault(Status.ENABLED),
                                note = e.optString("note", ""),
                            )
                        )
                    }
                }
                LobsterRole(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    role = obj.optString("role"),
                    status = runCatching { Status.valueOf(obj.optString("status", "ENABLED")) }.getOrDefault(Status.ENABLED),
                    duties = duties.filter { it.isNotEmpty() },
                    history = history,
                    version = obj.optInt("version", 1),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    source = runCatching { Source.valueOf(obj.optString("source", "USER")) }.getOrDefault(Source.USER),
                )
            }.getOrNull()
        }

        /** Built-in default roles that ship with PokeClaw. */
        val BUILTIN_DEFAULTS: List<LobsterRole> = listOf(
            LobsterRole(
                id = "default-assistant",
                name = "通用助手",
                role = "You are a helpful, balanced AI assistant. Be friendly, clear, and concise. Switch tone based on user need.",
                duties = listOf("answer questions", "draft text", "explain concepts", "general help"),
                source = Source.SYSTEM,
            ),
            LobsterRole(
                id = "work-mode",
                name = "工作模式",
                role = "You are a professional work assistant. Be precise, structured, and result-oriented. Prefer bullet points, action items, and explicit references. Avoid small talk.",
                duties = listOf("draft emails", "summarize meetings", "organize tasks", "format documents"),
                source = Source.SYSTEM,
            ),
            LobsterRole(
                id = "home-mode",
                name = "家庭模式",
                role = "You are a warm, patient home companion. Use plain language, be encouraging, and avoid technical jargon. Prioritize family-friendly and safe responses.",
                duties = listOf("explain to kids", "plan family activities", "household reminders"),
                source = Source.SYSTEM,
            ),
            LobsterRole(
                id = "cautious-mode",
                name = "谨慎模式",
                role = "You are an extra-cautious assistant. Confirm before taking irreversible actions. Surface risks and ask for explicit confirmation on purchases, deletions, account changes, and any phone automation that affects privacy or data.",
                duties = listOf("warn on risky actions", "require approval for sensitive ops", "explain consequences"),
                source = Source.SYSTEM,
            ),
        )
    }
}
