// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * Fetcher abstraction for [AigcTemplate]s
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER). Cloud sync is owned by
 * the dyq backend; until that wire is up the picker falls back to
 * [CatalogAigcTemplateFetcher] which returns a hardcoded seed of
 * templates covering every [TemplateChannel].
 */
fun interface AigcTemplateFetcher {
    /** Return templates to show, or null to fall back to the seed. */
    fun fetch(): List<AigcTemplate>?
}

/**
 * Hardcoded seed of AIGC templates covering all known
 * [TemplateChannel] values. Used as a fallback when the cloud
 * fetcher returns null or throws.
 */
object AigcTemplateCatalog {

    private val SEED: List<AigcTemplate> = listOf(
        AigcTemplate(
            id = "pv-extend-001",
            name = "PixVerse 视频延长",
            channelCode = "pixverse",
        ),
        AigcTemplate(
            id = "pv-text-002",
            name = "PixVerse 文生图",
            channelCode = "pixverse",
        ),
        AigcTemplate(
            id = "cu-portrait-001",
            name = "ComfyUI 写真增强",
            channelCode = "comfyui",
        ),
        AigcTemplate(
            id = "cu-cartoon-002",
            name = "ComfyUI 动漫化",
            channelCode = "comfyui",
        ),
        AigcTemplate(
            id = "mx-poster-001",
            name = "Muxi 海报模板",
            channelCode = "muxi-canvas",
        ),
        AigcTemplate(
            id = "mx-banner-002",
            name = "Muxi 横幅模板",
            channelCode = "muxi-canvas",
        ),
        AigcTemplate(
            id = "orphan-001",
            name = "其他渠道模板",
            channelCode = null,
        ),
    )

    fun all(): List<AigcTemplate> = SEED

    fun count(): Int = SEED.size
}

/**
 * Fetcher wrapper that tries [remote] first and falls back to
 * [AigcTemplateCatalog] on null / throw / empty.
 */
class CatalogAigcTemplateFetcher(
    private val remote: AigcTemplateFetcher? = null,
) {
    fun fetch(): List<AigcTemplate> {
        val fromRemote = try {
            remote?.fetch()
        } catch (t: Throwable) {
            io.agents.pokeclaw.utils.XLog.w(
                "AigcTemplateFetcher", "remote fetch failed: ${t.message}",
            )
            null
        }
        if (!fromRemote.isNullOrEmpty()) return fromRemote
        return AigcTemplateCatalog.all()
    }
}
