// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.channel.telegram

import io.agents.pokeclaw.utils.XLog
import org.json.JSONObject

/**
 * Parses a Telegram Bot API Update JSON payload into a flat record.
 *
 * Telegram update shapes we care about (US-D-017-TELEGRAM-HARDENING):
 *  - text message:   { "update_id": N, "message": { "chat": {…}, "text": "…" } }
 *  - command:        { "update_id": N, "message": { "entities":[{"offset":0,"type":"bot_command"}], "text": "/start args" } }
 *  - callback query: { "update_id": N, "callback_query": { "message": {…}, "data": "…" } }
 */
object TelegramUpdateParser {

    private const val TAG = "TelegramUpdateParser"

    enum class UpdateType { TEXT, COMMAND, CALLBACK_QUERY, UNKNOWN }

    data class ParsedUpdate(
        val updateId: Long,
        val type: UpdateType,
        val chatId: Long,
        val messageId: Long,
        val text: String,
        val command: String? = null,
        val commandArgs: String = "",
    )

    /**
     * Parse a single update JSON. Returns null when the payload is malformed
     * or contains no recognizable content.
     */
    fun parse(rawJson: String): ParsedUpdate? {
        val obj = runCatching { JSONObject(rawJson) }.getOrElse {
            XLog.w(TAG, "parse: not valid JSON")
            return null
        }
        val updateId = obj.optLong("update_id", -1L)
        if (updateId < 0) return null

        val message = obj.optJSONObject("message") ?: obj.optJSONObject("edited_message")
        if (message != null) {
            val chat = message.optJSONObject("chat") ?: return null
            val chatId = chat.optLong("id", -1L)
            val messageId = message.optLong("message_id", -1L)
            val text = message.optString("text", "").trim()
            if (chatId < 0 || text.isEmpty()) return null
            val entities = message.optJSONArray("entities")
            val command = entities?.let { ea ->
                (0 until ea.length()).map { ea.getJSONObject(it) }
                    .firstOrNull { it.optString("type") == "bot_command" }
            }
            return if (command != null && text.startsWith("/")) {
                val spaceIdx = text.indexOf(' ')
                val cmd = if (spaceIdx > 0) text.substring(0, spaceIdx) else text
                val args = if (spaceIdx > 0) text.substring(spaceIdx + 1) else ""
                ParsedUpdate(
                    updateId = updateId,
                    type = UpdateType.COMMAND,
                    chatId = chatId,
                    messageId = messageId,
                    text = text,
                    command = cmd,
                    commandArgs = args,
                )
            } else {
                ParsedUpdate(
                    updateId = updateId,
                    type = UpdateType.TEXT,
                    chatId = chatId,
                    messageId = messageId,
                    text = text,
                )
            }
        }

        val callback = obj.optJSONObject("callback_query")
        if (callback != null) {
            val cbMessage = callback.optJSONObject("message") ?: return null
            val chat = cbMessage.optJSONObject("chat") ?: return null
            val chatId = chat.optLong("id", -1L)
            val messageId = cbMessage.optLong("message_id", -1L)
            val data = callback.optString("data", "")
            return ParsedUpdate(
                updateId = updateId,
                type = UpdateType.CALLBACK_QUERY,
                chatId = chatId,
                messageId = messageId,
                text = data,
            )
        }

        return null
    }
}