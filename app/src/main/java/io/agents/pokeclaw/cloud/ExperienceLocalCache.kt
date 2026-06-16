// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * End-side cache of fetched experiences.
 *
 * R2 US-B-EXPERIENCE-READ: offline fallback for [ExperienceReader].
 * Capped at [MAX_ENTRIES] most-recent entries; older are dropped.
 */
object ExperienceLocalCache {

    private const val TAG = "ExperienceLocalCache"
    private const val KEY_CACHE = "experience_local_cache_v1"
    private const val MAX_ENTRIES = 200

    fun save(context: Context, experiences: List<ExperienceReader.Experience>) {
        try {
            val arr = JSONArray()
            experiences.take(MAX_ENTRIES).forEach { e ->
                val o = JSONObject()
                o.put("commercialTaskId", e.commercialTaskId)
                o.put("type", e.type.name)
                o.put("summary", e.summary)
                o.put("errorCategory", e.errorCategory)
                o.put("errorCode", e.errorCode)
                o.put("recoveryHint", e.recoveryHint)
                o.put("recordedAt", e.recordedAt)
                o.put("strategyKeywords", JSONArray(e.strategyKeywords))
                arr.put(o)
            }
            KVUtils.putString(KEY_CACHE, arr.toString())
            XLog.d(TAG, "save: ${arr.length()} experiences cached")
        } catch (ex: Exception) {
            XLog.w(TAG, "save: ${ex.message}")
        }
    }

    fun load(context: Context): List<ExperienceReader.Experience> {
        val json = KVUtils.getString(KEY_CACHE, "")
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<ExperienceReader.Experience>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = runCatching {
                    ExperienceReader.Experience.Type.valueOf(o.optString("type", "SUCCESS"))
                }.getOrDefault(ExperienceReader.Experience.Type.SUCCESS)
                val kw = mutableListOf<String>()
                o.optJSONArray("strategyKeywords")?.let { ja ->
                    for (k in 0 until ja.length()) kw.add(ja.optString(k, ""))
                }
                out.add(
                    ExperienceReader.Experience(
                        commercialTaskId = o.optString("commercialTaskId"),
                        type = type,
                        summary = o.optString("summary"),
                        errorCategory = o.optString("errorCategory"),
                        errorCode = o.optString("errorCode"),
                        recoveryHint = o.optString("recoveryHint"),
                        recordedAt = o.optLong("recordedAt", 0L),
                        strategyKeywords = kw.filter { it.isNotEmpty() },
                    )
                )
            }
            out
        } catch (e: Exception) {
            XLog.w(TAG, "load: ${e.message}")
            emptyList()
        }
    }
}
