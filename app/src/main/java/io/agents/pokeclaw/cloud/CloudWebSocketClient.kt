// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.BuildConfig
import io.agents.pokeclaw.utils.XLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * WebSocket client scaffold (default OFF — controlled by BuildConfig.CLOUD_WS_ENABLED).
 *
 * This is a client skeleton — it does NOT replace the existing cloud task polling path.
 * It only opens a WS connection when explicitly enabled, and reuses the existing
 * /api/claw/poll or the same /ws endpoint that the cloud server exposes.
 *
 * Lifecycle:
 *   connect() → OPEN
 *     ↳ onMessage → dispatch
 *     ↳ ping every 30s; close if no pong within 90s
 *   onFailure/onClosed → reconnect with backoff 1s / 5s / 30s
 */
class CloudWebSocketClient(
    private val host: String,
    private val deviceId: String,
    private val token: String,
    private val onMessage: (String) -> Unit = {},
) {

    companion object {
        private const val TAG = "CloudWebSocket"
        const val PING_INTERVAL_SEC = 30L
        const val PONG_TIMEOUT_SEC = 90L
        val RECONNECT_BACKOFF_MS = longArrayOf(1_000L, 5_000L, 30_000L)
    }

    enum class State { IDLE, CONNECTING, OPEN, CLOSED, RECONNECTING }

    private val enabled: Boolean = BuildConfig.CLOUD_WS_ENABLED
    private val state = AtomicReference(State.IDLE)
    private val manualClose = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
        .readTimeout(PONG_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    fun state(): State = state.get()

    fun connect() {
        if (!enabled) {
            XLog.d(TAG, "ws: connect skipped (BuildConfig.CLOUD_WS_ENABLED=false)")
            return
        }
        if (state.get() == State.OPEN || state.get() == State.CONNECTING) {
            XLog.d(TAG, "ws: connect already in progress")
            return
        }
        manualClose.set(false)
        state.set(State.CONNECTING)
        val url = "ws://$host/ws/claw/device?deviceId=$deviceId&token=$token"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
        XLog.d(TAG, "ws: connect url=$url")
    }

    fun close() {
        manualClose.set(true)
        webSocket?.close(1000, "client_close")
        webSocket = null
        state.set(State.CLOSED)
        XLog.d(TAG, "ws: disconnect (manual)")
    }

    /** Send a text frame to the cloud. */
    fun send(text: String): Boolean {
        val ws = webSocket ?: return false
        val ok = ws.send(text)
        XLog.d(TAG, "ws: send text len=${text.length} ok=$ok")
        return ok
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            state.set(State.OPEN)
            XLog.d(TAG, "ws: open")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            XLog.d(TAG, "ws: recv len=${text.length} preview=${text.take(60)}")
            onMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            XLog.d(TAG, "ws: closing code=$code reason=$reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            state.set(State.CLOSED)
            XLog.d(TAG, "ws: closed code=$code reason=$reason")
            if (!manualClose.get()) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            XLog.w(TAG, "ws: failure ${t.javaClass.simpleName}: ${t.message}")
            state.set(State.CLOSED)
            if (!manualClose.get()) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        state.set(State.RECONNECTING)
        // 顺序回退：1s / 5s / 30s 循环
        Thread({
            for (delay in RECONNECT_BACKOFF_MS) {
                if (manualClose.get()) return@Thread
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
                XLog.d(TAG, "ws: reconnect attempt after ${delay}ms")
                connect()
                if (state.get() == State.OPEN) return@Thread
            }
        }, "ws-reconnect").start()
    }
}
