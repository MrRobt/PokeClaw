// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * Pure filter logic for [AigcTemplate]s by [TemplateChannel]
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER).
 *
 * Kept as a free function (not bound to any View) so the
 * filtering rules are unit-testable on the JVM and the UI layer
 * can re-use them with whatever adapter it likes.
 */
object TemplateChannelFilter {

    private const val TAG = "TemplateChannelFilter"

    /**
     * Filter [templates] by the selected [channel].
     *
     *  - [TemplateChannel.ALL] returns the input unchanged.
     *  - [TemplateChannel.UNKNOWN] returns only templates whose
     *    `channelCode` is null, blank, or not one of the known
     *    channels (i.e. parsed back to [TemplateChannel.UNKNOWN]).
     *  - Specific channels (PIXVERSE / COMFYUI / MUXI_CANVAS)
     *    return templates whose [TemplateChannel.fromString] is
     *    exactly that channel.
     */
    fun filter(templates: List<AigcTemplate>, channel: TemplateChannel): List<AigcTemplate> {
        if (channel == TemplateChannel.ALL) return templates
        return templates.filter { TemplateChannel.fromString(it.channelCode) == channel }
    }

    /**
     * Counting helper for the XLog line
     * `template-filter: channel=X showing=N`.
     */
    fun count(templates: List<AigcTemplate>, channel: TemplateChannel): Int =
        filter(templates, channel).size

    /** Internal tag used for XLog.d. */
    fun tag(): String = TAG
}
