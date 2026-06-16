// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.agents.pokeclaw.cloud.model.TaskApprovalRequest
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Owner approval decision reporter (R2 US-B-TASK-APPROVAL).
 *
 * Flow:
 *  1. Caller invokes [report] with the decision data.
 *  2. If the network is reachable and a device token is present, posts
 *     to POST /api/claw/task-approval immediately.
 *  3. On any failure (network / 4xx / 5xx) the request is enqueued
 *     to MMKV-persisted pending list and retried by [flushPending].
 *  4. Best-effort — never throws, never blocks the caller.
 *
 * Mirrors the read path of [ExperienceReader] for symmetry: both the
 * write path (this class) and the read path (ExperienceReader) can
 * tolerate offline mode by falling back to local MMKV state.
 */
class CloudApprovalReporter(
    private val context: Context,
    private val baseUrlProvider: () -> String,
    private val getDeviceToken: () -> String? = { null },
    private val getDeviceId: () -> String? = { null },
) {

    companion object {
        private const val TAG = "CloudApprovalReporter"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val PREFS_NAME = "pokeclaw_cloud_approval_pending"
        private const val KEY_PENDING = "pending"
        private const val MAX_PENDING = 200

        @Volatile
        private var instance: CloudApprovalReporter? = null

        fun getInstance(context: Context): CloudApprovalReporter {
            return instance ?: synchronized(this) {
                instance ?: CloudApprovalReporter(
                    context = context.applicationContext,
                    baseUrlProvider = {
                        // Best-effort: ask the enrollment manager for active env URL
                        ClawNodeEnrollmentManager.DEFAULT_ENV_BACKEND_URLS[
                            ClawNodeEnrollmentManager.BackendEnv.fromStorageOrDefault()
                        ] ?: "http://10.0.2.2:8080"
                    },
                    getDeviceToken = {
                        runCatching { TokenManager.getInstance(context.applicationContext).getDeviceToken() }.getOrNull()
                    },
                    getDeviceId = {
                        runCatching { TokenManager.getInstance(context.applicationContext).getDeviceId() }.getOrNull()
                    },
                ).also { instance = it }
            }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Report a decision. Returns immediately; the actual HTTP call runs
     * in the background. Failures are queued for retry.
     */
    fun report(decision: TaskApprovalRequest) {
        // Always persist first so a process kill after the dialog never loses the audit.
        enqueuePending(decision)
        scope.launch {
            try {
                postApproval(decision)
                removePending(decision.requestId)
            } catch (e: Exception) {
                XLog.w(TAG, "report: post failed, requestId=${decision.requestId} queued: ${e.message}")
            }
        }
    }

    /**
     * Best-effort flush of queued approvals. Called by CloudNodeOrchestrator
     * heartbeat loop on each successful beat (analogous to flushOfflineQueue).
     */
    suspend fun flushPending(): Int {
        val pending = loadPending()
        if (pending.isEmpty()) return 0
        var flushed = 0
        for (req in pending.toList()) {
            try {
                postApproval(req)
                removePending(req.requestId)
                flushed++
            } catch (e: Exception) {
                XLog.d(TAG, "flushPending: still failing requestId=${req.requestId}: ${e.message}")
                // keep it queued; next heartbeat retries
            }
        }
        if (flushed > 0) {
            XLog.i(TAG, "flushPending: flushed=$flushed remaining=${loadPending().size}")
        }
        return flushed
    }

    fun pendingCount(): Int = loadPending().size

    private fun postApproval(req: TaskApprovalRequest) {
        val base = baseUrlProvider().trimEnd('/')
        val url = "$base/api/claw/task-approval"
        val token = getDeviceToken()
        val body = gson.toJson(req.copy(deviceId = req.deviceId ?: getDeviceId())).toRequestBody(JSON_MEDIA)
        val requestBuilder = Request.Builder().url(url).post(body)
        token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                XLog.w(TAG, "postApproval: HTTP ${response.code} requestId=${req.requestId}")
                throw IllegalStateException("HTTP ${response.code}")
            }
            XLog.i(TAG, "postApproval: ok requestId=${req.requestId} decision=${req.decision} taskUuid=${req.taskUuid}")
        }
    }

    private fun enqueuePending(req: TaskApprovalRequest) {
        val current = loadPending().toMutableList()
        // de-dup by requestId
        if (current.any { it.requestId == req.requestId }) return
        current.add(req)
        val capped = current.takeLast(MAX_PENDING)
        prefs.edit().putString(KEY_PENDING, gson.toJson(capped)).apply()
        XLog.d(TAG, "enqueuePending: requestId=${req.requestId} size=${capped.size}")
    }

    private fun removePending(requestId: String) {
        val current = loadPending().filterNot { it.requestId == requestId }
        prefs.edit().putString(KEY_PENDING, gson.toJson(current)).apply()
    }

    private fun loadPending(): List<TaskApprovalRequest> {
        val json = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<TaskApprovalRequest>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            XLog.w(TAG, "loadPending: parse error, dropping corrupt cache: ${e.message}")
            emptyList()
        }
    }
}
