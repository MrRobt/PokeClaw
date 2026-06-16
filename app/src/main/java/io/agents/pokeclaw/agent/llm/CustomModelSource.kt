// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

/**
 * A user-defined litertlm model source (US-D-020-CUSTOM-MODEL-SOURCE).
 *
 * Sources are validated against an HTTPS-only allowlist before being
 * persisted. Optional SHA-256 checksum is verified after download.
 */
data class CustomModelSource(
    val id: String,
    val name: String,
    val url: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val minRamGb: Int? = null,
    val enabled: Boolean = true,
) {
    /**
     * Convert this source into a [LocalModelManager.ModelInfo] suitable for
     * the existing download / catalog path. The [LocalModelManager.ModelInfo.id]
     * is prefixed `custom_` so it's distinguishable from built-in entries.
     */
    fun toModelInfo(): LocalModelManager.ModelInfo {
        // Derive a stable filename from the URL's last path segment.
        // For URLs without a path (e.g. "https://example.com" or "https://huggingface.co/"),
        // fall back to "$id.litertlm" so the catalog always has a unique,
        // .litertlm-terminated filename to write to disk.
        val fileName = deriveFileName()
        return LocalModelManager.ModelInfo(
            id = "custom_$id",
            displayName = "$name (自定义)",
            url = url,
            fileName = fileName,
            sizeBytes = sizeBytes ?: 0L,
            minRamGb = minRamGb ?: 8,
        )
    }

    private fun deriveFileName(): String {
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        // Strip trailing slashes so ".../file.litertlm/" yields "file.litertlm"
        // (without trailing /) and ".../" yields an empty path that falls
        // through to the "$id.litertlm" fallback.
        val rawPath = parsed?.rawPath?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        val lastSegment = rawPath?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        return if (lastSegment != null && lastSegment.contains('.')) {
            lastSegment
        } else {
            "$id.litertlm"
        }
    }
}