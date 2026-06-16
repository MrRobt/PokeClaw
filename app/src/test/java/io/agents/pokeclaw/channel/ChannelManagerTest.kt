// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// ChannelManager 单测 — Channel 枚举稳定性、dispatchMessage listener 路由、
// sendMessage/sendMessageToUser 空内容跳过、内容 trim、handler 委托（通过反射注入 fake）。

package io.agents.pokeclaw.channel

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ChannelManagerTest {

    /**
     * 测试用 fake handler — 记录所有方法调用。
     */
    private class FakeHandler(override val channel: Channel, val connected: Boolean = true) : ChannelHandler {
        var initCalls = 0
            private set
        var disconnectCalls = 0
            private set
        var reinitCalls = 0
            private set
        var lastSendContent: String? = null
            private set
        var lastSendMessageId: String? = null
            private set
        var lastImageBytes: ByteArray? = null
            private set
        var lastFile: File? = null
            private set
        var flushCalls = 0
            private set
        var lastSenderIdValue: String? = null
        var lastUserId: String? = null
            private set
        var lastToUserContent: String? = null
            private set
        var lastRestoreUserId: String? = null
            private set

        var connectedState: Boolean = connected

        override fun isConnected(): Boolean = connectedState

        override fun init() { initCalls++ }

        override fun disconnect() {
            disconnectCalls++
            connectedState = false
        }

        override fun reinitFromStorage() {
            reinitCalls++
            connectedState = true
        }

        override fun sendMessage(content: String, messageID: String) {
            lastSendContent = content
            lastSendMessageId = messageID
        }

        override fun sendImage(imageBytes: ByteArray, messageID: String) {
            lastImageBytes = imageBytes
            lastSendMessageId = messageID
        }

        override fun sendFile(file: File, messageID: String) {
            lastFile = file
            lastSendMessageId = messageID
        }

        override fun flushMessages() { flushCalls++ }

        override fun getLastSenderId(): String? = lastSenderIdValue

        override fun sendMessageToUser(userId: String, content: String) {
            lastUserId = userId
            lastToUserContent = content
        }

        override fun restoreRoutingContext(targetUserId: String) {
            lastRestoreUserId = targetUserId
        }
    }

    /**
     * 测试用 listener — 记录收到的消息。
     */
    private class RecordingListener : ChannelManager.OnMessageReceivedListener {
        data class Call(val channel: Channel, val message: String, val messageID: String)
        var lastCall: Call? = null
            private set
        var callCount = 0
            private set

        override fun onMessageReceived(channel: Channel, message: String, messageID: String) {
            lastCall = Call(channel, message, messageID)
            callCount++
        }
    }

    @Before
    fun setUp() {
        // 清空 handlers map 避免测试间相互污染
        clearHandlersViaReflection()
        ChannelManager.setOnMessageReceivedListener(null)
    }

    @After
    fun tearDown() {
        clearHandlersViaReflection()
        ChannelManager.setOnMessageReceivedListener(null)
    }

    private fun clearHandlersViaReflection() {
        val field = ChannelManager::class.java.getDeclaredField("handlers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(ChannelManager) as MutableMap<Channel, ChannelHandler>
        map.clear()
    }

    private fun injectHandler(channel: Channel, handler: ChannelHandler) {
        val field = ChannelManager::class.java.getDeclaredField("handlers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(ChannelManager) as MutableMap<Channel, ChannelHandler>
        map[channel] = handler
    }

    // ── Channel 枚举 ──

    @Test
    fun `Channel 枚举 9 值稳定 UI 路由契约`() {
        val values = Channel.values().toSet()
        assertEquals(
            setOf(
                Channel.DISCORD, Channel.TELEGRAM, Channel.WECHAT,
                Channel.WHATSAPP, Channel.GMAIL, Channel.BROWSER,
                Channel.PHONE, Channel.CLOUD, Channel.LOCAL,
            ),
            values,
        )
    }

    @Test
    fun `Channel 各自 displayName 非空 (UI 契约)`() {
        for (c in Channel.values()) {
            assertTrue("Channel.${c.name} displayName 应非空", c.displayName.isNotBlank())
        }
    }

    // ── dispatchMessage 路由 ──

    @Test
    fun `dispatchMessage - 已设置 listener 时应触发回调`() {
        val listener = RecordingListener()
        ChannelManager.setOnMessageReceivedListener(listener)

        ChannelManager.dispatchMessage(Channel.TELEGRAM, "hello", "msg-1")

        assertEquals(1, listener.callCount)
        assertEquals(Channel.TELEGRAM, listener.lastCall!!.channel)
        assertEquals("hello", listener.lastCall!!.message)
        assertEquals("msg-1", listener.lastCall!!.messageID)
    }

    @Test
    fun `dispatchMessage - 未设置 listener 不应崩溃`() {
        // 不设 listener，dispatch 不应抛 NPE
        ChannelManager.dispatchMessage(Channel.TELEGRAM, "hello", "msg-1")
        // 没有异常就通过
    }

    @Test
    fun `setOnMessageReceivedListener - 设为 null 解除绑定`() {
        val listener = RecordingListener()
        ChannelManager.setOnMessageReceivedListener(listener)
        ChannelManager.dispatchMessage(Channel.DISCORD, "first", "1")
        assertEquals(1, listener.callCount)

        ChannelManager.setOnMessageReceivedListener(null)
        ChannelManager.dispatchMessage(Channel.DISCORD, "second", "2")
        assertEquals("解除绑定后 callCount 不再增加", 1, listener.callCount)
    }

    @Test
    fun `dispatchMessage - 多次分发都触发`() {
        val listener = RecordingListener()
        ChannelManager.setOnMessageReceivedListener(listener)

        ChannelManager.dispatchMessage(Channel.TELEGRAM, "a", "1")
        ChannelManager.dispatchMessage(Channel.DISCORD, "b", "2")
        ChannelManager.dispatchMessage(Channel.WECHAT, "c", "3")

        assertEquals(3, listener.callCount)
        assertEquals("c", listener.lastCall!!.message)
    }

    // ── sendMessage 空内容跳过 ──

    @Test
    fun `sendMessage - 空字符串不应调用 handler`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.sendMessage(Channel.TELEGRAM, "", "msg-id")

        assertNull("空内容不应设置 lastSendContent", handler.lastSendContent)
    }

    @Test
    fun `sendMessage - 仅空白字符串 不应调用 handler`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.sendMessage(Channel.TELEGRAM, "   \t  ", "msg-id")

        assertNull(handler.lastSendContent)
    }

    @Test
    fun `sendMessage - 仅换行符 不应调用 handler (trim 后为空)`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.sendMessage(Channel.TELEGRAM, "\n\n\r\n", "msg-id")

        assertNull(handler.lastSendContent)
    }

    @Test
    fun `sendMessage - 内容前后有换行符应被 trim 再发送`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.sendMessage(Channel.TELEGRAM, "\n\rhello\n\r", "msg-id")

        assertEquals("hello", handler.lastSendContent)
        assertEquals("msg-id", handler.lastSendMessageId)
    }

    @Test
    fun `sendMessage - 未注册 handler 不应崩溃`() {
        // handlers 为空，调用不应 NPE
        ChannelManager.sendMessage(Channel.DISCORD, "hello", "msg-id")
    }

    @Test
    fun `sendMessage - 中间空格内容应正常发送`() {
        val handler = FakeHandler(Channel.DISCORD)
        injectHandler(Channel.DISCORD, handler)

        ChannelManager.sendMessage(Channel.DISCORD, "hello world", "m1")

        assertEquals("hello world", handler.lastSendContent)
    }

    // ── sendMessageToUser 空内容跳过 ──

    @Test
    fun `sendMessageToUser - 空字符串不调用 handler`() {
        val handler = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.WECHAT, handler)

        ChannelManager.sendMessageToUser(Channel.WECHAT, "user-1", "")

        assertNull(handler.lastToUserContent)
        assertNull(handler.lastUserId)
    }

    @Test
    fun `sendMessageToUser - 仅空白不调用 handler`() {
        val handler = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.WECHAT, handler)

        ChannelManager.sendMessageToUser(Channel.WECHAT, "user-1", "  \t  ")

        assertNull(handler.lastToUserContent)
    }

    @Test
    fun `sendMessageToUser - 前后换行被 trim`() {
        val handler = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.WECHAT, handler)

        ChannelManager.sendMessageToUser(Channel.WECHAT, "user-1", "\nhi\r\n")

        assertEquals("hi", handler.lastToUserContent)
        assertEquals("user-1", handler.lastUserId)
    }

    // ── 委托方法 ──

    @Test
    fun `sendImage 委托给 handler`() {
        val handler = FakeHandler(Channel.DISCORD)
        injectHandler(Channel.DISCORD, handler)
        val bytes = byteArrayOf(1, 2, 3)

        ChannelManager.sendImage(Channel.DISCORD, bytes, "img-id")

        assertNotNull(handler.lastImageBytes)
        assertEquals(3, handler.lastImageBytes!!.size)
        assertEquals("img-id", handler.lastSendMessageId)
    }

    @Test
    fun `sendFile 委托给 handler`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)
        val file = File.createTempFile("test", ".txt").also { it.writeText("data") }
        try {
            ChannelManager.sendFile(Channel.TELEGRAM, file, "file-id")
            assertEquals(file, handler.lastFile)
            assertEquals("file-id", handler.lastSendMessageId)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `flushMessages 委托给 handler`() {
        val handler = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.WECHAT, handler)

        ChannelManager.flushMessages(Channel.WECHAT)

        assertEquals(1, handler.flushCalls)
    }

    @Test
    fun `restoreRoutingContext 委托给 handler`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.restoreRoutingContext(Channel.TELEGRAM, "user-42")

        assertEquals("user-42", handler.lastRestoreUserId)
    }

    @Test
    fun `getLastSenderId 委托给 handler`() {
        val handler = FakeHandler(Channel.TELEGRAM).apply { lastSenderIdValue = "sender-99" }
        injectHandler(Channel.TELEGRAM, handler)

        assertEquals("sender-99", ChannelManager.getLastSenderId(Channel.TELEGRAM))
    }

    @Test
    fun `getLastSenderId - 未注册 handler 返回 null 不崩`() {
        assertNull(ChannelManager.getLastSenderId(Channel.DISCORD))
    }

    @Test
    fun `未注册的 channel 调用 sendMessage 静默 no-op`() {
        // 注入 TELEGRAM 但调 DISCORD 应无副作用
        val tg = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, tg)
        val dc = FakeHandler(Channel.DISCORD)
        injectHandler(Channel.DISCORD, dc)

        ChannelManager.sendMessage(Channel.WHATSAPP, "hi", "id")

        assertNull(tg.lastSendContent)
        assertNull(dc.lastSendContent)
    }

    // ── disconnectAll 语义 ──

    @Test
    fun `disconnectAll - 仅断开已连接 handler`() {
        val connected = FakeHandler(Channel.DISCORD, connected = true)
        val notConnected = FakeHandler(Channel.TELEGRAM, connected = false)
        injectHandler(Channel.DISCORD, connected)
        injectHandler(Channel.TELEGRAM, notConnected)

        ChannelManager.disconnectAll()

        assertEquals("已连接 handler 应被断开", 1, connected.disconnectCalls)
        assertEquals("未连接 handler 不应被断开", 0, notConnected.disconnectCalls)
    }

    @Test
    fun `disconnectAll - handlers 为空不崩`() {
        ChannelManager.disconnectAll()
    }

    // ── reconnectIfNeeded 语义 ──

    @Test
    fun `reconnectIfNeeded - 仅重连未连接的 handler`() {
        val connected = FakeHandler(Channel.DISCORD, connected = true)
        val notConnected = FakeHandler(Channel.TELEGRAM, connected = false)
        injectHandler(Channel.DISCORD, connected)
        injectHandler(Channel.TELEGRAM, notConnected)

        ChannelManager.reconnectIfNeeded()

        assertEquals("已连接 handler 不应被重连", 0, connected.reinitCalls)
        assertEquals("未连接 handler 应被重连", 1, notConnected.reinitCalls)
    }

    @Test
    fun `reconnectIfNeeded - handlers 为空不崩`() {
        ChannelManager.reconnectIfNeeded()
    }

    // ── reinitXxx 单 channel 重连 ──

    @Test
    fun `reinitDiscordFromStorage - 仅重连 DISCORD`() {
        val dc = FakeHandler(Channel.DISCORD)
        val tg = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.DISCORD, dc)
        injectHandler(Channel.TELEGRAM, tg)

        ChannelManager.reinitDiscordFromStorage()

        assertEquals(1, dc.reinitCalls)
        assertEquals("TELEGRAM 不应被影响", 0, tg.reinitCalls)
    }

    @Test
    fun `reinitTelegramFromStorage - 仅重连 TELEGRAM`() {
        val dc = FakeHandler(Channel.DISCORD)
        val tg = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.DISCORD, dc)
        injectHandler(Channel.TELEGRAM, tg)

        ChannelManager.reinitTelegramFromStorage()

        assertEquals(0, dc.reinitCalls)
        assertEquals(1, tg.reinitCalls)
    }

    @Test
    fun `reinitWeChatFromStorage - 仅重连 WECHAT`() {
        val dc = FakeHandler(Channel.DISCORD)
        val wc = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.DISCORD, dc)
        injectHandler(Channel.WECHAT, wc)

        ChannelManager.reinitWeChatFromStorage()

        assertEquals(0, dc.reinitCalls)
        assertEquals(1, wc.reinitCalls)
    }

    @Test
    fun `reinitDiscordFromStorage - 未注册 handler 不崩`() {
        // DISCORD 不在 handlers map 里
        ChannelManager.reinitDiscordFromStorage()
    }

    // ── reinitFromStorage ──

    @Test
    fun `reinitFromStorage - 所有 handler 都被 reinit`() {
        val dc = FakeHandler(Channel.DISCORD)
        val tg = FakeHandler(Channel.TELEGRAM)
        val wc = FakeHandler(Channel.WECHAT)
        injectHandler(Channel.DISCORD, dc)
        injectHandler(Channel.TELEGRAM, tg)
        injectHandler(Channel.WECHAT, wc)

        ChannelManager.reinitFromStorage()

        assertEquals(1, dc.reinitCalls)
        assertEquals(1, tg.reinitCalls)
        assertEquals(1, wc.reinitCalls)
    }

    @Test
    fun `reinitFromStorage - handlers 为空不崩`() {
        ChannelManager.reinitFromStorage()
    }

    // ── 边界 / 防御 ──

    @Test
    fun `sendMessage - 所有 channel type 都能正确路由`() {
        for (channel in Channel.values()) {
            val handler = FakeHandler(channel)
            injectHandler(channel, handler)

            ChannelManager.sendMessage(channel, "test-${channel.name}", "id-${channel.name}")

            assertEquals("channel ${channel.name} 应收到内容", "test-${channel.name}", handler.lastSendContent)
            clearHandlersViaReflection()
        }
    }

    @Test
    fun `sendMessage 内容含 emoji 中文 特殊字符 应原样传递`() {
        val handler = FakeHandler(Channel.TELEGRAM)
        injectHandler(Channel.TELEGRAM, handler)

        ChannelManager.sendMessage(Channel.TELEGRAM, "你好 🐾 @user#1", "m")

        assertEquals("你好 🐾 @user#1", handler.lastSendContent)
    }
}