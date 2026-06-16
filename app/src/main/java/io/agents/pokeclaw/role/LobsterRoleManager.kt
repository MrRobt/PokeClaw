// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.role

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lobster role store — list / get / activate / pause / disable / create / update.
 *
 * Persistence: MMKV key `lobster_roles_v1` holds a JSON array of roles.
 * Active role id is stored at [KVUtils.LOBSTER_ACTIVE_ROLE_ID].
 *
 * Bootstrap: if no roles are persisted, [LobsterRole.BUILTIN_DEFAULTS] are seeded.
 *
 * Concurrency: all public methods are synchronized on `lock` to prevent
 * cross-thread races between UI edits and TaskOrchestrator reads.
 */
class LobsterRoleManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val lock = Any()

    fun list(): List<LobsterRole> {
        synchronized(lock) {
            return loadAll().sortedBy { it.source.ordinal * 1000 + it.name.hashCode() }
        }
    }

    fun get(id: String): LobsterRole? = synchronized(lock) {
        loadAll().firstOrNull { it.id == id }
    }

    fun activate(id: String): Boolean = synchronized(lock) {
        val roles = loadAll().toMutableList()
        val idx = roles.indexOfFirst { it.id == id }
        if (idx < 0) {
            XLog.w(TAG, "activate: role not found id=$id")
            return@synchronized false
        }
        val target = roles[idx]
        if (target.status == LobsterRole.Status.DISABLED) {
            XLog.w(TAG, "activate: role is disabled, refusing id=$id")
            return@synchronized false
        }
        val updated = target.copy(
            status = LobsterRole.Status.ENABLED,
            updatedAt = System.currentTimeMillis(),
            history = target.history + LobsterRole.HistoryEntry(
                changedAt = System.currentTimeMillis(),
                fromStatus = target.status,
                toStatus = LobsterRole.Status.ENABLED,
                note = "activated",
            ),
        )
        roles[idx] = updated
        saveAll(roles)
        KVUtils.setLobsterActiveRoleId(id)
        XLog.d(TAG, "lobster-role activate: id=$id name=${updated.name}")
        true
    }

    fun pause(id: String): Boolean = synchronized(lock) {
        return@synchronized updateStatus(id, LobsterRole.Status.PAUSED, "paused")
    }

    fun disable(id: String): Boolean = synchronized(lock) {
        return@synchronized updateStatus(id, LobsterRole.Status.DISABLED, "disabled")
    }

    private fun updateStatus(id: String, newStatus: LobsterRole.Status, note: String): Boolean {
        val roles = loadAll().toMutableList()
        val idx = roles.indexOfFirst { it.id == id }
        if (idx < 0) {
            XLog.w(TAG, "updateStatus: role not found id=$id")
            return false
        }
        val target = roles[idx]
        if (target.status == newStatus) return true
        val updated = target.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis(),
            history = target.history + LobsterRole.HistoryEntry(
                changedAt = System.currentTimeMillis(),
                fromStatus = target.status,
                toStatus = newStatus,
                note = note,
            ),
        )
        roles[idx] = updated
        saveAll(roles)
        // If we just disabled the active role, clear the pointer so agent falls back to default
        if (newStatus == LobsterRole.Status.DISABLED && KVUtils.getLobsterActiveRoleId() == id) {
            KVUtils.setLobsterActiveRoleId(DEFAULT_ROLE_ID)
        }
        XLog.d(TAG, "lobster-role updateStatus: id=$id $newStatus note=$note")
        return true
    }

    fun createLocal(
        id: String? = null,
        name: String,
        role: String,
        duties: List<String> = emptyList(),
    ): LobsterRole = synchronized(lock) {
        val roles = loadAll().toMutableList()
        val newId = id?.takeIf { it.isNotBlank() } ?: ("user-" + System.currentTimeMillis().toString(36))
        val now = System.currentTimeMillis()
        val newRole = LobsterRole(
            id = newId,
            name = name,
            role = role,
            status = LobsterRole.Status.ENABLED,
            duties = duties,
            version = 1,
            updatedAt = now,
            source = LobsterRole.Source.USER,
        )
        roles.add(newRole)
        saveAll(roles)
        XLog.d(TAG, "lobster-role create: id=$newId name=$name")
        newRole
    }

    fun updateLocal(updated: LobsterRole): Boolean = synchronized(lock) {
        val roles = loadAll().toMutableList()
        val idx = roles.indexOfFirst { it.id == updated.id }
        if (idx < 0) return@synchronized false
        val newVersion = roles[idx].version + 1
        roles[idx] = updated.copy(version = newVersion, updatedAt = System.currentTimeMillis())
        saveAll(roles)
        XLog.d(TAG, "lobster-role update: id=${updated.id} v=$newVersion")
        true
    }

    fun deleteLocal(id: String): Boolean = synchronized(lock) {
        val roles = loadAll().toMutableList()
        val target = roles.firstOrNull { it.id == id } ?: return@synchronized false
        if (target.source == LobsterRole.Source.SYSTEM) {
            XLog.w(TAG, "deleteLocal: cannot delete SYSTEM-sourced role id=$id")
            return@synchronized false
        }
        roles.removeAll { it.id == id }
        saveAll(roles)
        if (KVUtils.getLobsterActiveRoleId() == id) {
            KVUtils.setLobsterActiveRoleId(DEFAULT_ROLE_ID)
        }
        XLog.d(TAG, "lobster-role delete: id=$id")
        true
    }

    fun activeRole(): LobsterRole? {
        val id = KVUtils.getLobsterActiveRoleId().ifEmpty { DEFAULT_ROLE_ID }
        return get(id) ?: get(DEFAULT_ROLE_ID)
    }

    fun activeRoleSystemPromptSection(): String? {
        val role = activeRole() ?: return null
        if (role.status != LobsterRole.Status.ENABLED) return null
        return buildString {
            appendLine("## Active Role: ${role.name}")
            appendLine(role.role)
            if (role.duties.isNotEmpty()) {
                appendLine()
                appendLine("Primary duties:")
                role.duties.forEach { appendLine("- $it") }
            }
        }.trim()
    }

    // ==================== Persistence ====================

    private fun loadAll(): List<LobsterRole> {
        val json = KVUtils.getString(KEY_ROLES, "")
        if (json.isEmpty()) {
            // First boot — seed defaults
            val seeded = LobsterRole.BUILTIN_DEFAULTS
            saveAll(seeded)
            KVUtils.setLobsterActiveRoleId(DEFAULT_ROLE_ID)
            XLog.d(TAG, "loadAll: seeded ${seeded.size} default roles")
            return seeded
        }
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<LobsterRole>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val role = LobsterRole.fromJson(obj.toString())
                if (role != null) list.add(role)
            }
            list
        } catch (e: Exception) {
            XLog.e(TAG, "loadAll: parse failed, resetting to defaults", e)
            saveAll(LobsterRole.BUILTIN_DEFAULTS)
            KVUtils.setLobsterActiveRoleId(DEFAULT_ROLE_ID)
            LobsterRole.BUILTIN_DEFAULTS
        }
    }

    private fun saveAll(roles: List<LobsterRole>) {
        val arr = JSONArray()
        roles.forEach { role ->
            val obj = JSONObject()
            obj.put("id", role.id)
            obj.put("name", role.name)
            obj.put("role", role.role)
            obj.put("status", role.status.name)
            obj.put("duties", JSONArray(role.duties))
            obj.put("version", role.version)
            obj.put("updatedAt", role.updatedAt)
            obj.put("source", role.source.name)
            val historyArr = JSONArray()
            role.history.forEach { entry ->
                val e = JSONObject()
                e.put("changedAt", entry.changedAt)
                entry.fromStatus?.let { e.put("fromStatus", it.name) }
                e.put("toStatus", entry.toStatus.name)
                e.put("note", entry.note)
                historyArr.put(e)
            }
            obj.put("history", historyArr)
            arr.put(obj)
        }
        KVUtils.putString(KEY_ROLES, arr.toString())
    }

    companion object {
        private const val TAG = "LobsterRole"
        const val DEFAULT_ROLE_ID = "default-assistant"
        const val KEY_ROLES = "lobster_roles_v1"

        @Volatile private var instance: LobsterRoleManager? = null
        fun get(context: Context): LobsterRoleManager =
            instance ?: synchronized(this) {
                instance ?: LobsterRoleManager(context).also { instance = it }
            }
    }
}
