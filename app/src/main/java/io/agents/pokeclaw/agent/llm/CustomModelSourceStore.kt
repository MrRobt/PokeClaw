// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for user-defined model sources (US-D-020).
 *
 * Storage: JSON array at KV key [KEY] (managed via [io.agents.pokeclaw.utils.KVUtils]).
 * Validation: HTTPS-only; host must be in [ALLOWED_HOSTS]. SHA-256 is optional
 * but verified at download time when present.
 */
object CustomModelSourceStore {

    private const val TAG = "CustomModelSourceStore"
    const val MAX_SOURCES = 10
    private const val KEY = "custom_model_sources_v1"

    val ALLOWED_HOSTS: Set<String> = setOf(
        "huggingface.co",
        "github.com",
        "gitlab.com",
        "raw.githubusercontent.com",
    )

    sealed class AddResult {
        data class Accepted(val source: CustomModelSource) : AddResult()
        data class Rejected(val reason: String, val preview: String) : AddResult()
    }

    /**
     * Validate a URL against the [ALLOWED_HOSTS] allowlist and the HTTPS rule.
     * Returns null when valid, or a short error code when invalid.
     */
    fun validateUrl(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (!url.startsWith("https://")) return "URL_NOT_HTTPS"
        val host = url.removePrefix("https://").substringBefore('/').lowercase()
        val matches = ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
        return if (matches) null else "URL_NOT_ALLOWED"
    }

    fun validateSha256(sha256: String?): String? {
        if (sha256.isNullOrBlank()) return null
        val ok = sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        return if (ok) null else "INVALID_SHA256_FORMAT"
    }

    /**
     * Add a source. Returns [AddResult.Rejected] on validation failure or when
     * [MAX_SOURCES] is exceeded. The new source is auto-enabled.
     */
    fun add(
        name: String,
        url: String,
        sha256: String? = null,
        sizeBytes: Long? = null,
        minRamGb: Int? = null,
    ): AddResult {
        if (name.isBlank()) return AddResult.Rejected("EMPTY_NAME", name.take(40))
        val urlErr = validateUrl(url)
        if (urlErr != null) return AddResult.Rejected(urlErr, url.take(60))
        val shaErr = validateSha256(sha256)
        if (shaErr != null) return AddResult.Rejected(shaErr, sha256?.take(20) ?: "")
        if (listAll().size >= MAX_SOURCES) {
            return AddResult.Rejected("MAX_SOURCES_EXCEEDED", name.take(40))
        }
        val source = CustomModelSource(
            id = "cms-" + java.util.UUID.randomUUID().toString().take(8),
            name = name.trim(),
            url = url.trim(),
            sha256 = sha256?.trim()?.lowercase(),
            sizeBytes = sizeBytes,
            minRamGb = minRamGb,
            enabled = true,
        )
        val list = listAll().toMutableList()
        list += source
        persist(list)
        XLog.d(TAG, "custom-model: added id=${source.id} url=${source.url} sha256=${source.sha256?.take(12)}")
        return AddResult.Accepted(source)
    }

    fun listAll(): List<CustomModelSource> {
        val raw = io.agents.pokeclaw.utils.KVUtils.getCustomModelSources()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomModelSource(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    sha256 = o.optString("sha256").takeIf { it.isNotEmpty() },
                    sizeBytes = o.optLong("sizeBytes", 0L).takeIf { it > 0L },
                    minRamGb = o.optInt("minRamGb", 0).takeIf { it > 0 },
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }.getOrElse { e ->
            XLog.w(TAG, "custom-model: parse failed: ${e.message}")
            emptyList()
        }
    }

    fun listEnabled(): List<CustomModelSource> = listAll().filter { it.enabled }

    fun delete(id: String): Boolean {
        val list = listAll().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) persist(list)
        return removed
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val list = listAll().map { if (it.id == id) it.copy(enabled = enabled) else it }
        persist(list)
    }

    fun clearAll() {
        io.agents.pokeclaw.utils.KVUtils.setCustomModelSources("")
    }

    private fun persist(list: List<CustomModelSource>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("url", s.url)
            o.put("sha256", s.sha256 ?: "")
            o.put("sizeBytes", s.sizeBytes ?: 0L)
            o.put("minRamGb", s.minRamGb ?: 0)
            o.put("enabled", s.enabled)
            arr.put(o)
        }
        io.agents.pokeclaw.utils.KVUtils.setCustomModelSources(arr.toString())
    }
}