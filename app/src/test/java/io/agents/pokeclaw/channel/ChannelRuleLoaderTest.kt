// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the static mapping portion of ChannelRuleLoader.
 *
 * Asset-loading paths are exercised by the QA checklist (`channel-rules QA
 * section in QA_CHECKLIST.md`) rather than this unit test, because the
 * production code path depends on Android's AssetManager and is best run
 * against a real APK with the assets present.
 */
class ChannelRuleLoaderTest {

    @Test
    fun fileNameFor_mapsKnownChannelsToTheirAssetFiles() {
        assertEquals("channel_rules_whatsapp.md", ChannelRuleLoader.fileNameFor(Channel.WHATSAPP))
        assertEquals("channel_rules_telegram.md", ChannelRuleLoader.fileNameFor(Channel.TELEGRAM))
        assertEquals("channel_rules_gmail.md", ChannelRuleLoader.fileNameFor(Channel.GMAIL))
        assertEquals("channel_rules_browser.md", ChannelRuleLoader.fileNameFor(Channel.BROWSER))
        assertEquals("channel_rules_phone.md", ChannelRuleLoader.fileNameFor(Channel.PHONE))
        assertEquals("channel_rules_cloud.md", ChannelRuleLoader.fileNameFor(Channel.CLOUD))
    }

    @Test
    fun fileNameFor_returnsNullForChannelsWithoutDedicatedRules() {
        // LOCAL has no dedicated file — global rules cover in-app chat.
        assertNull(ChannelRuleLoader.fileNameFor(Channel.LOCAL))
    }

    @Test
    fun fileNameFor_distinguishesEveryChannel() {
        val names = listOf(
            Channel.WHATSAPP, Channel.TELEGRAM, Channel.GMAIL,
            Channel.BROWSER, Channel.PHONE, Channel.CLOUD
        ).mapNotNull { ChannelRuleLoader.fileNameFor(it) }
        // All 6 channel filenames must be unique — no accidental collisions.
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun fileNameFor_handlesAllEnumValuesWithoutCrash() {
        // Smoke: every enum value must produce either a known filename or null.
        for (c in Channel.values()) {
            val name = ChannelRuleLoader.fileNameFor(c)
            // Either null (local / unknown / future) or a string starting with channel_rules_
            if (name != null) {
                assertNotEquals("", name)
            }
        }
    }

    // --- Channel enum structure ---

    @Test
    fun `Channel enum 数量为 9`() {
        // DISCORD, TELEGRAM, WECHAT, WHATSAPP, GMAIL, BROWSER, PHONE, CLOUD, LOCAL = 9
        assertEquals(9, Channel.values().size)
    }

    @Test
    fun `Channel displayName 与 name 不一定相同`() {
        // displayName 是给 UI 用的 title-case，name 是 enum 名
        assertEquals("Discord", Channel.DISCORD.displayName)
        assertEquals("Telegram", Channel.TELEGRAM.displayName)
        assertEquals("WeChat", Channel.WECHAT.displayName)
        assertEquals("WhatsApp", Channel.WHATSAPP.displayName)
        assertEquals("Gmail", Channel.GMAIL.displayName)
        assertEquals("Browser", Channel.BROWSER.displayName)
        assertEquals("Phone", Channel.PHONE.displayName)
        assertEquals("Cloud", Channel.CLOUD.displayName)
        assertEquals("Local", Channel.LOCAL.displayName)
    }

    @Test
    fun `Channel displayName 全部唯一`() {
        val displayNames = Channel.values().map { it.displayName }
        assertEquals("displayName 出现重复", displayNames.size, displayNames.toSet().size)
    }

    @Test
    fun `Channel enum name 全部唯一`() {
        val names = Channel.values().map { it.name }
        assertEquals("enum name 出现重复", names.size, names.toSet().size)
    }

    // --- fileNameFor 边界 ---

    @Test
    fun `fileNameFor 对 DISCORD 和 WECHAT 返回 null`() {
        // DISCORD / WECHAT 当前没有专属规则文件（云端渠道）
        assertNull(ChannelRuleLoader.fileNameFor(Channel.DISCORD))
        assertNull(ChannelRuleLoader.fileNameFor(Channel.WECHAT))
    }

    @Test
    fun `fileNameFor 返回的字符串都遵循 channel_rules_{name lowercase} md 格式`() {
        for (c in Channel.values()) {
            val name = ChannelRuleLoader.fileNameFor(c) ?: continue
            assertTrue(
                "fileName 格式不合法: $name",
                name.startsWith("channel_rules_") && name.endsWith(".md"),
            )
            // 中间段必须等于 enum name 的小写形式
            val middle = name.removePrefix("channel_rules_").removeSuffix(".md")
            assertEquals("中间段必须等于 enum name 小写", c.name.lowercase(), middle)
        }
    }

    @Test
    fun `fileNameFor 同一 channel 多次调用结果一致`() {
        // 纯函数语义：相同输入必须返回相同输出
        val first = ChannelRuleLoader.fileNameFor(Channel.WHATSAPP)
        val second = ChannelRuleLoader.fileNameFor(Channel.WHATSAPP)
        val third = ChannelRuleLoader.fileNameFor(Channel.WHATSAPP)
        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `fileNameFor 命名空间为 lowercase md 资产文件后缀`() {
        // 验证资产文件后缀是 .md 而非 .txt
        assertTrue(
            ChannelRuleLoader.fileNameFor(Channel.WHATSAPP)!!.endsWith(".md"),
        )
        assertTrue(
            ChannelRuleLoader.fileNameFor(Channel.TELEGRAM)!!.endsWith(".md"),
        )
    }

    @Test
    fun `fileNameFor 只接受 md 后缀 不混入 txt 或 json`() {
        for (c in Channel.values()) {
            val name = ChannelRuleLoader.fileNameFor(c) ?: continue
            assertTrue("不能是 .txt: $name", !name.endsWith(".txt"))
            assertTrue("不能是 .json: $name", !name.endsWith(".json"))
        }
    }

    // --- Channel enum 反查 ---

    @Test
    fun `Channel valueOf 与 values 反查一致`() {
        // enum.valueOf("WHATSAPP") 与 Channel.values() 反查结果应一致
        assertEquals(Channel.WHATSAPP, Channel.valueOf("WHATSAPP"))
        assertEquals(Channel.LOCAL, Channel.valueOf("LOCAL"))
        assertEquals(Channel.CLOUD, Channel.valueOf("CLOUD"))
    }

    @Test
    fun `Channel valueOf 对未知名称抛 IllegalArgumentException`() {
        try {
            Channel.valueOf("UNKNOWN_CHANNEL")
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // --- ChannelRuleLoader 单例语义 ---

    @Test
    fun `ChannelRuleLoader 是 object 单例`() {
        // Kotlin object 的语义：fileNameFor 调用等同于 ::fileNameFor.invoke
        val ref1 = ChannelRuleLoader::fileNameFor
        val ref2 = ChannelRuleLoader::fileNameFor
        // 两个 method reference 必须指向同一个 receiver
        assertEquals(ref1, ref2)
    }
}