// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Read-back layer for experience records (symmetric to [ExperienceUploader]).
 *
 * R2 US-B-EXPERIENCE-READ:
 *  - Contract: GET /api/claw-experience/list?deviceId=X&since=Y
 *  - Use case: when a similar task starts, inject top-3 success + top-1 failure
 *    experiences as few-shot examples
 *  - Offline → fall back to [ExperienceLocalCache] (KV-persisted copy)
 *  - Only reads what the device itself wrote (deviceId filter)
 */
class ExperienceReader(
    private val context: Context,
    private val baseUrl: String,
    private val deviceId: String,
    private val getToken: () -> String? = { null },
) {

    companion object {
        private const val TAG = "ExperienceReader"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_LIMIT = 50
    }

    data class Experience(
        val commercialTaskId: String,
        val type: Type,
        val summary: String = "",
        val errorCategory: String = "",
        val errorCode: String = "",
        val recoveryHint: String = "",
        val strategyKeywords: List<String> = emptyList(),
        val recordedAt: Long = 0L,
    ) {
        enum class Type { SUCCESS, FAILURE }
    }

    data class FewShotBundle(
        val topSuccess: List<Experience>,
        val topFailure: List<Experience>,
    ) {
        fun asPromptSection(): String? {
            if (topSuccess.isEmpty() && topFailure.isEmpty()) return null
            val sb = StringBuilder()
            if (topSuccess.isNotEmpty()) {
                sb.appendLine("## Past successful experiences (few-shot)")
                topSuccess.forEachIndexed { i, e ->
                    sb.appendLine("${i + 1}. ${e.summary}")
                }
            }
            if (topFailure.isNotEmpty()) {
                sb.appendLine("## Past failure experiences (avoid these)")
                topFailure.forEachIndexed { i, e ->
                    sb.appendLine("${i + 1}. ${e.errorCategory}/${e.errorCode} → ${e.recoveryHint}")
                }
            }
            return sb.toString().trim()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Sync from cloud. On failure, fall back to the local cache. Returns the
     * experiences regardless of source (caller is told via [SyncResult.fromCache]).
     */
    fun sync(sinceMillis: Long = 0L, limit: Int = DEFAULT_LIMIT): SyncResult {
        val url = "$baseUrl/api/claw-experience/list?deviceId=${deviceId}&since=${sinceMillis}&limit=${limit}"
        val requestBuilder = Request.Builder().url(url).get()
        getToken()?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val experiences = parseList(body)
                    ExperienceLocalCache.save(context, experiences)
                    XLog.d(TAG, "experience-read: fetched=${experiences.size} from cloud")
                    SyncResult(experiences = experiences, fromCache = false)
                } else {
                    XLog.w(TAG, "experience-read: HTTP ${response.code}, falling back to cache")
                    SyncResult(experiences = ExperienceLocalCache.load(context), fromCache = true, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "experience-read: network failure, falling back to cache: ${e.message}")
            SyncResult(experiences = ExperienceLocalCache.load(context), fromCache = true, error = e.message)
        }
    }

    /**
     * Pick top-3 success + top-1 failure (most recent first) and assemble
     * a few-shot prompt section.
     */
    fun fewShotFor(experiences: List<Experience>): FewShotBundle {
        val successSorted = experiences
            .filter { it.type == Experience.Type.SUCCESS }
            .sortedByDescending { it.recordedAt }
            .take(3)
        val failureSorted = experiences
            .filter { it.type == Experience.Type.FAILURE }
            .sortedByDescending { it.recordedAt }
            .take(1)
        return FewShotBundle(successSorted, failureSorted)
    }

    private fun parseList(body: String): List<Experience> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<Experience>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val typeStr = o.optString("type", "success").uppercase()
                val type = runCatching { Experience.Type.valueOf(typeStr) }.getOrDefault(Experience.Type.SUCCESS)
                val kw = mutableListOf<String>()
                o.optJSONArray("strategyKeywords")?.let { ja ->
                    for (k in 0 until ja.length()) kw.add(ja.optString(k, ""))
                }
                list.add(
                    Experience(
                        commercialTaskId = o.optString("commercialTaskId"),
                        type = type,
                        summary = o.optString("summary"),
                        errorCategory = o.optString("errorCategory"),
                        errorCode = o.optString("errorCode"),
                        recoveryHint = o.optString("recoveryHint"),
                        strategyKeywords = kw.filter { it.isNotEmpty() },
                        recordedAt = o.optLong("recordedAt", 0L),
                    )
                )
            }
            list
        } catch (e: Exception) {
            XLog.w(TAG, "parseList: ${e.message}")
            emptyList()
        }
    }

    data class SyncResult(
        val experiences: List<Experience>,
        val fromCache: Boolean,
        val error: String? = null,
    )
}
