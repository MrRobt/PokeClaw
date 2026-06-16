// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.channel

import android.content.Context
import io.agents.pokeclaw.utils.XLog

/**
 * Loads per-channel rules from assets/channel_rules_{channel}.md and
 * appends the global rules (channel_rules_global.md) before them.
 *
 * Per R3 story US-D-019-SCOPED-CHANNEL-RULES:
 *  - 7 channel-specific files: whatsapp / telegram / gmail / browser / phone / cloud / local
 *  - 1 global file applies to all channels
 *  - Each channel-specific file is capped at 80 lines (soft limit; loader warns above)
 *  - Missing channel-specific file → fallback to global only
 */
object ChannelRuleLoader {

    private const val TAG = "ChannelRuleLoader"
    private const val GLOBAL_FILE = "channel_rules_global.md"
    private const val CHANNEL_PREFIX = "channel_rules_"
    private const val CHANNEL_SUFFIX = ".md"
    private const val SOFT_LINE_LIMIT = 80

    /**
     * Map [Channel] to its asset filename. Returns null for channels that
     * don't have a dedicated rule file (they fall back to global only).
     */
    fun fileNameFor(channel: Channel): String? = when (channel) {
        Channel.WHATSAPP -> "channel_rules_whatsapp.md"
        Channel.TELEGRAM -> "channel_rules_telegram.md"
        Channel.GMAIL -> "channel_rules_gmail.md"
        Channel.BROWSER -> "channel_rules_browser.md"
        Channel.PHONE -> "channel_rules_phone.md"
        Channel.CLOUD -> "channel_rules_cloud.md"
        Channel.LOCAL -> null  // local chat uses global + role/instructions
        else -> null
    }

    /**
     * Load global rules + channel-specific rules (if present).
     * Returns "" if both files are missing — caller should treat empty as "no rules".
     */
    fun loadFor(context: Context, channel: Channel): String {
        val globalPart = runCatching {
            context.assets.open(GLOBAL_FILE).bufferedReader().use { it.readText() }
        }.getOrElse { e ->
            XLog.w(TAG, "loadFor: missing global file (err=${e.javaClass.simpleName})")
            ""
        }

        val channelFile = fileNameFor(channel)
        val channelPart = if (channelFile != null) {
            runCatching {
                context.assets.open(channelFile).bufferedReader().use { it.readText() }
            }.getOrElse { e ->
                XLog.w(TAG, "loadFor: missing channel=$channel file=$channelFile, fallback to global (err=${e.javaClass.simpleName})")
                ""
            }
        } else {
            ""
        }

        val lines = (globalPart.lineCount() + channelPart.lineCount())
        XLog.d(TAG, "loadFor: channel=$channel lines=$lines")

        if (channelPart.isNotEmpty()) {
            val channelLines = channelPart.lineCount()
            if (channelLines > SOFT_LINE_LIMIT) {
                XLog.w(TAG, "loadFor: channel=$channel lines=$channelLines exceeds soft limit=$SOFT_LINE_LIMIT")
            }
        }

        return buildString {
            append(globalPart)
            if (channelPart.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(channelPart)
            }
        }
    }

    private fun String.lineCount(): Int = if (isEmpty()) 0 else count { it == '\n' } + 1
}