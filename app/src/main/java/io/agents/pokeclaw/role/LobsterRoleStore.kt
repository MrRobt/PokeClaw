// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.role

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lobster role store (light cloud + end-side cache).
 *
 * R2 US-B-ROLE-STORE:
 *  - list / get / activate / sync
 *  - cache hit → return; first/expired → sync() to fetch from cloud
 *  - end-side read-only + activate; writes happen in the cloud admin
 *  - offline → show local cache + offline badge
 *
 * The actual HTTP call (GET /api/claw/roles/list) is left to the caller —
 * R2 ships a stub that returns an empty list when no cloud payload is
 * available. The contract shape is documented inline.
 */
class LobsterRoleStore(context: Context) {

    data class StoreEntry(
        val id: String,
        val name: String,
        val role: String,
        val version: Int,
        val cached: Boolean,
    )

    data class SyncResult(
        val fetched: Int = 0,
        val applied: Int = 0,
        val skipped: Int = 0,
        val error: String? = null,
    )

    private val appContext: Context = context.applicationContext
    private val manager: LobsterRoleManager = LobsterRoleManager.get(context)

    fun list(): List<StoreEntry> = readCache().map { StoreEntry(it.id, it.name, it.role, it.version, cached = true) }

    fun get(id: String): StoreEntry? = readCache().firstOrNull { it.id == id }?.let {
        StoreEntry(it.id, it.name, it.role, it.version, cached = true)
    }

    /** Activate a store role locally. */
    fun activate(id: String): Boolean {
        val cached = readCache().firstOrNull { it.id == id } ?: return false
        // Persist as USER-sourced so the local manager owns it
        val existing = manager.get(cached.id)
        return if (existing == null) {
            manager.createLocal(id = cached.id, name = cached.name, role = cached.role, duties = cached.duties)
            manager.activate(cached.id)
        } else {
            manager.activate(cached.id)
        }
    }

    /**
     * Sync from cloud. In R2 the cloud endpoint may not be live; we accept
     * an explicit JSON payload via [applyCloudPayload] or fall back to the
     * existing cache so the UI still renders something.
     */
    fun sync(): SyncResult {
        return try {
            val current = readCache()
            // The contract (documented for the role-store story):
            //   GET /api/claw/roles/list
            //   response: { roles: [ {id, name, role, version, updatedAt} ] }
            // R2 leaves the network call to the caller; we just check
            // staleness and return the existing cache.
            val cutoff = 24 * 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            val fresh = current.filter { now - it.updatedAt < cutoff }
            if (fresh.isNotEmpty()) {
                SyncResult(fetched = fresh.size, applied = 0, skipped = 0)
            } else {
                SyncResult(fetched = 0, applied = 0, skipped = 0, error = "no cloud payload available")
            }
        } catch (e: Exception) {
            XLog.w(TAG, "sync: $e")
            SyncResult(error = e.message)
        }
    }

    /** Apply a cloud payload (called by the heart-beat or explicit push). */
    fun applyCloudPayload(json: String?): SyncResult {
        if (json.isNullOrBlank()) return SyncResult(error = "empty payload")
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("roles") ?: JSONArray()
            val arrStr = arr.toString()
            val existing = readCache().associateBy { it.id }.toMutableMap()
            var fetched = 0
            var applied = 0
            var skipped = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                fetched++
                val id = o.optString("id")
                if (id.isBlank()) {
                    skipped++
                    continue
                }
                val incoming = LobsterRole(
                    id = id,
                    name = o.optString("name", id),
                    role = o.optString("role", ""),
                    version = o.optInt("version", 1),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    source = LobsterRole.Source.CLOUD,
                )
                val local = existing[id]
                if (local != null && local.version >= incoming.version) {
                    skipped++
                    continue
                }
                existing[id] = incoming
                applied++
            }
            writeCache(existing.values.toList())
            SyncResult(fetched = fetched, applied = applied, skipped = skipped)
        } catch (e: Exception) {
            XLog.w(TAG, "applyCloudPayload: $e")
            SyncResult(error = e.message)
        }
    }

    private fun readCache(): List<LobsterRole> {
        val json = KVUtils.getString(KEY_CACHE, "")
        if (json.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<LobsterRole>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    LobsterRole(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        role = o.optString("role"),
                        version = o.optInt("version", 1),
                        updatedAt = o.optLong("updatedAt", 0L),
                        source = runCatching { LobsterRole.Source.valueOf(o.optString("source", "CLOUD")) }.getOrDefault(LobsterRole.Source.CLOUD),
                    )
                )
            }
            list
        } catch (e: Exception) {
            XLog.w(TAG, "readCache: $e")
            emptyList()
        }
    }

    private fun writeCache(roles: List<LobsterRole>) {
        val arr = JSONArray()
        roles.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("role", r.role)
            o.put("version", r.version)
            o.put("updatedAt", r.updatedAt)
            o.put("source", r.source.name)
            arr.put(o)
        }
        KVUtils.putString(KEY_CACHE, arr.toString())
    }

    companion object {
        private const val TAG = "LobsterRoleStore"
        const val KEY_CACHE = "lobster_role_store_cache_v1"

        @Volatile private var instance: LobsterRoleStore? = null
        fun get(context: Context): LobsterRoleStore =
            instance ?: synchronized(this) {
                instance ?: LobsterRoleStore(context).also { instance = it }
            }
    }
}
