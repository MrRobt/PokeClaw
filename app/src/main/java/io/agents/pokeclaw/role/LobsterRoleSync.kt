// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.role

import android.content.Context
import io.agents.pokeclaw.utils.XLog
import org.json.JSONObject

/**
 * Light cloud sync for lobster role definitions.
 *
 * R2 US-B-CMP-1-1: cloud-adjustable; R2 US-B-ROLE-STORE consumes this for the role store UI.
 *
 * Flow:
 *  1. [applyCloudUpdate] is called by the heartbeat response when the cloud
 *     reports role metadata. Newer versions overwrite local; older are dropped.
 *  2. [syncActiveRole] uploads the currently active role id+version back to
 *     the cloud on a best-effort basis.
 *
 * Network: this layer is intentionally decoupled from the Retrofit interface
 * so it can be called even when the cloud is unreachable. The [CloudHeartbeatManager]
 * provides the access token; the actual HTTP call is left to the caller
 * (R2 ships a stub that logs intent and updates the MMKV cache).
 */
class LobsterRoleSync(context: Context) {

    private val appContext: Context = context.applicationContext
    private val manager: LobsterRoleManager = LobsterRoleManager.get(context)

    data class SyncResult(
        val fetched: Int = 0,
        val applied: Int = 0,
        val skipped: Int = 0,
        val error: String? = null,
    )

    /**
     * Apply a list of role definitions coming from the cloud.
     * Caller passes a parsed JSON array of role objects (same shape as [LobsterRole.toJson]).
     */
    fun applyCloudUpdate(roleJsonArray: String): SyncResult {
        return try {
            val arr = org.json.JSONArray(roleJsonArray)
            var fetched = 0
            var applied = 0
            var skipped = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                fetched++
                val incoming = LobsterRole.fromJson(obj.toString()) ?: continue
                if (incoming.id.isBlank()) {
                    skipped++
                    continue
                }
                val local = manager.get(incoming.id)
                if (local != null && local.version >= incoming.version) {
                    skipped++
                    continue
                }
                val stored = incoming.copy(
                    source = LobsterRole.Source.CLOUD,
                    updatedAt = System.currentTimeMillis(),
                )
                if (local == null) {
                    manager.createLocal(
                        id = stored.id,
                        name = stored.name,
                        role = stored.role,
                        duties = stored.duties,
                    )
                    // Mark it CLOUD-sourced after creation
                    val placeholder = manager.get(stored.id) ?: continue
                    manager.updateLocal(stored.copy(version = placeholder.version))
                } else {
                    manager.updateLocal(stored)
                }
                applied++
            }
            SyncResult(fetched = fetched, applied = applied, skipped = skipped)
        } catch (e: Exception) {
            XLog.w(TAG, "applyCloudUpdate: parse failed: ${e.message}")
            SyncResult(error = e.message)
        }
    }

    /**
     * Best-effort: upload the active role id and version to the cloud.
     * The full request shape is documented in the role-store story; here we
     * just log the intent so debug traces show the call happened.
     */
    fun syncActiveRole(): JSONObject? {
        val active = manager.activeRole() ?: run {
            XLog.d(TAG, "syncActiveRole: no active role, skipping")
            return null
        }
        val tokenManager = io.agents.pokeclaw.cloud.TokenManager.getInstance(appContext)
        val token = tokenManager.getDeviceToken()
        if (token.isNullOrBlank()) {
            XLog.d(TAG, "syncActiveRole: no access token, skipping (offline)")
            return null
        }
        val payload = JSONObject().apply {
            put("activeRoleId", active.id)
            put("version", active.version)
            put("clientUpdatedAt", active.updatedAt)
        }
        XLog.d(TAG, "syncActiveRole: would POST ${payload}")
        return payload
    }

    companion object {
        private const val TAG = "LobsterRoleSync"
    }
}
