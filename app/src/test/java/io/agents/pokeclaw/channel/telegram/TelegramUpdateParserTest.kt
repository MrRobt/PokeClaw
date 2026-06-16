// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.channel.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TelegramUpdateParser covering the three update shapes the
 * controller cares about: text message, command, and callback query.
 */
class TelegramUpdateParserTest {

    @Test
    fun parse_textMessage_returnsTextType() {
        val raw = """
            {
              "update_id": 1234,
              "message": {
                "message_id": 50,
                "chat": { "id": 999, "type": "private" },
                "text": "hello world"
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(1234L, parsed!!.updateId)
        assertEquals(999L, parsed.chatId)
        assertEquals(50L, parsed.messageId)
        assertEquals("hello world", parsed.text)
        assertEquals(TelegramUpdateParser.UpdateType.TEXT, parsed.type)
    }

    @Test
    fun parse_commandWithArgs_returnsCommandType() {
        val raw = """
            {
              "update_id": 1235,
              "message": {
                "message_id": 51,
                "chat": { "id": 999, "type": "private" },
                "text": "/remember my name is Sarah",
                "entities": [ { "offset": 0, "length": 9, "type": "bot_command" } ]
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.COMMAND, parsed!!.type)
        assertEquals("/remember", parsed.command)
        assertEquals("my name is Sarah", parsed.commandArgs)
    }

    @Test
    fun parse_commandWithoutArgs_returnsEmptyArgs() {
        val raw = """
            {
              "update_id": 1236,
              "message": {
                "message_id": 52,
                "chat": { "id": 999, "type": "private" },
                "text": "/start",
                "entities": [ { "offset": 0, "length": 6, "type": "bot_command" } ]
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.COMMAND, parsed!!.type)
        assertEquals("/start", parsed.command)
        assertEquals("", parsed.commandArgs)
    }

    @Test
    fun parse_callbackQuery_returnsCallbackQueryType() {
        val raw = """
            {
              "update_id": 1237,
              "callback_query": {
                "id": "cb-1",
                "data": "approve",
                "message": {
                  "message_id": 53,
                  "chat": { "id": 999, "type": "private" }
                }
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.CALLBACK_QUERY, parsed!!.type)
        assertEquals("approve", parsed.text)
        assertEquals(999L, parsed.chatId)
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        assertNull(TelegramUpdateParser.parse("not json"))
        assertNull(TelegramUpdateParser.parse(""))
        assertNull(TelegramUpdateParser.parse("{}"))
    }

    @Test
    fun parse_messageWithoutText_returnsNull() {
        val raw = """
            {
              "update_id": 1238,
              "message": {
                "message_id": 54,
                "chat": { "id": 999 }
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_editedMessage_returnsTextType() {
        // The parser intentionally treats edited_message like message.
        val raw = """
            {
              "update_id": 1239,
              "edited_message": {
                "message_id": 55,
                "chat": { "id": 999 },
                "text": "edited"
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.TEXT, parsed!!.type)
        assertEquals("edited", parsed.text)
    }

    @Test
    fun parse_unknownUpdate_returnsNull() {
        val raw = """
            {
              "update_id": 1240,
              "inline_query": { "id": "inline", "query": "x" }
            }
        """.trimIndent()
        // We don't currently handle inline_query — return null is acceptable.
        val parsed = TelegramUpdateParser.parse(raw)
        assertTrue(parsed == null)
    }

    // --- 边界 / 错误路径 ---

    @Test
    fun parse_missingUpdateId_returnsNull() {
        val raw = """
            {
              "message": {
                "message_id": 60,
                "chat": { "id": 999 },
                "text": "hi"
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_negativeUpdateId_returnsNull() {
        // update_id 必须是 >= 0（实际 Telegram 总是 > 0）
        val raw = """{"update_id":-1,"message":{"message_id":60,"chat":{"id":999},"text":"hi"}}"""
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_messageWithoutChat_returnsNull() {
        val raw = """
            {
              "update_id": 1300,
              "message": {
                "message_id": 60,
                "text": "no chat"
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_messageWithNegativeChatId_returnsNull() {
        val raw = """
            {
              "update_id": 1301,
              "message": {
                "message_id": 60,
                "chat": { "id": -1 },
                "text": "hi"
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_messageWithBlankText_returnsNull() {
        val raw = """
            {
              "update_id": 1302,
              "message": {
                "message_id": 60,
                "chat": { "id": 999 },
                "text": "   "
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_callbackQueryWithoutMessage_returnsNull() {
        val raw = """
            {
              "update_id": 1303,
              "callback_query": {
                "id": "cb-1",
                "data": "approve"
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_callbackQueryWithoutChat_returnsNull() {
        val raw = """
            {
              "update_id": 1304,
              "callback_query": {
                "id": "cb-1",
                "data": "approve",
                "message": { "message_id": 60 }
              }
            }
        """.trimIndent()
        assertNull(TelegramUpdateParser.parse(raw))
    }

    @Test
    fun parse_commandWithBotnameSuffix_recognizedAsCommand() {
        // /start@mybot 形式也以 "/" 开头，应被识别为 COMMAND
        val raw = """
            {
              "update_id": 1305,
              "message": {
                "message_id": 60,
                "chat": { "id": 999 },
                "text": "/start@mybot arg1 arg2",
                "entities": [ { "offset": 0, "length": 14, "type": "bot_command" } ]
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.COMMAND, parsed!!.type)
        assertEquals("/start@mybot", parsed.command)
        assertEquals("arg1 arg2", parsed.commandArgs)
    }

    @Test
    fun parse_textWithUnicodeEmoji_preservedAsText() {
        val raw = """
            {
              "update_id": 1306,
              "message": {
                "message_id": 60,
                "chat": { "id": 999 },
                "text": "你好 🦞 world"
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.TEXT, parsed!!.type)
        assertEquals("你好 🦞 world", parsed.text)
    }

    @Test
    fun parse_messageWithoutMessageId_keepsMinusOne() {
        val raw = """
            {
              "update_id": 1307,
              "message": {
                "chat": { "id": 999 },
                "text": "hi"
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(-1L, parsed!!.messageId)
    }

    @Test
    fun parse_commandEntityExistsButTextDoesNotStartWithSlash_treatedAsText() {
        // entity 是 bot_command 但 text 不以 / 开头 → 走 TEXT 分支
        val raw = """
            {
              "update_id": 1308,
              "message": {
                "message_id": 60,
                "chat": { "id": 999 },
                "text": "not a command",
                "entities": [ { "offset": 0, "length": 5, "type": "bot_command" } ]
              }
            }
        """.trimIndent()
        val parsed = TelegramUpdateParser.parse(raw)
        assertNotNull(parsed)
        assertEquals(TelegramUpdateParser.UpdateType.TEXT, parsed!!.type)
        assertEquals("not a command", parsed.text)
    }

    @Test
    fun `UpdateType enum 数量为 4`() {
        assertEquals(4, TelegramUpdateParser.UpdateType.values().size)
    }
}