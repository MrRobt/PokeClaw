// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.channel.telegram

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Telegram bot controller (US-D-017-TELEGRAM-HARDENING).
 *
 * Validates a bot token against `getMe`, then runs a long-polling loop that
 * fetches updates, parses them, and invokes [onUpdate]. Outbound `sendMessage`
 * is exposed via [sendMessage] so the chat layer can reply after a task
 * completes.
 *
 * Lifecycle:
 *  - [start] begins validation + polling on a private coroutine scope.
 *  - [stop] cancels the scope and flips status to STOPPED.
 *  - The polling loop survives transient HTTP failures with exponential backoff
 *    (1s / 5s / 30s), capped at 30s.
 */
object TelegramBotController {

    private const val TAG = "TelegramBotController"
    private const val POLL_TIMEOUT_SECONDS = 30L
    private const val BACKOFF_MS_1 = 1_000L
    private const val BACKOFF_MS_2 = 5_000L
    private const val BACKOFF_MS_3 = 30_000L

    enum class Status { IDLE, VALIDATING, CONNECTED, POLLING, RECONNECTING, FAILED, STOPPED }

    data class StatusSnapshot(
        val status: Status,
        val botUsername: String? = null,
        val lastError: String? = null,
        val lastUpdateAt: Long = 0L,
    )

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(POLL_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusRef = AtomicReference(StatusSnapshot(Status.IDLE))
    private var pollingJob: Job? = null
    @Volatile private var onUpdate: ((TelegramUpdateParser.ParsedUpdate) -> Unit)? = null

    /** Status observable via [getStatus]. */
    fun getStatus(): StatusSnapshot = statusRef.get()

    /** Register a callback that receives parsed updates. Call from chat/UI layer. */
    fun setOnUpdateListener(listener: ((TelegramUpdateParser.ParsedUpdate) -> Unit)?) {
        onUpdate = listener
    }

    /**
     * Validate the token via getMe and (on success) start the polling loop.
     * [token] must be in the canonical form returned by BotFather
     * (e.g. `123456789:AA...`). Returns immediately; observe status via [getStatus].
     */
    fun start(token: String) {
        if (token.isBlank()) {
            updateStatus(Status.FAILED, null, "EMPTY_TOKEN")
            return
        }
        // Tear down any previous loop.
        stop()
        KVUtils.setTelegramBotToken(token)
        updateStatus(Status.VALIDATING, null, null)

        pollingJob = scope.launch {
            val meResult = validateToken(token)
            if (meResult == null) {
                updateStatus(Status.FAILED, null, "INVALID_TOKEN")
                KVUtils.setTelegramConnected(false)
                return@launch
            }
            KVUtils.setTelegramBotUsername(meResult.username)
            KVUtils.setTelegramConnected(true)
            updateStatus(Status.CONNECTED, meResult.username, null)
            XLog.i(TAG, "telegram: connected username=@${meResult.username}")

            var backoffMs = BACKOFF_MS_1
            while (isActive) {
                updateStatus(Status.POLLING, meResult.username, null)
                val offset = KVUtils.getTelegramLastUpdateId() + 1
                val updates = fetchUpdates(token, offset)
                if (updates == null) {
                    // Network error: backoff.
                    updateStatus(Status.RECONNECTING, meResult.username, "fetch_failed")
                    XLog.w(TAG, "telegram: fetch failed, backing off ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = nextBackoff(backoffMs)
                    continue
                }
                backoffMs = BACKOFF_MS_1
                for (raw in updates) {
                    val parsed = TelegramUpdateParser.parse(raw) ?: continue
                    KVUtils.setTelegramLastUpdateId(parsed.updateId)
                    statusRef.set(statusRef.get().copy(lastUpdateAt = System.currentTimeMillis()))
                    XLog.d(TAG, "telegram: chatId=${parsed.chatId} text=${parsed.text.take(40)} type=${parsed.type.name}")
                    runCatching { onUpdate?.invoke(parsed) }
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        updateStatus(Status.STOPPED, statusRef.get().botUsername, null)
        KVUtils.setTelegramConnected(false)
        XLog.i(TAG, "telegram: stopped")
    }

    /**
     * Send a plain text message to a chat via sendMessage. Returns true on HTTP 200.
     */
    suspend fun sendMessage(token: String, chatId: Long, text: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot$token/sendMessage".toHttpUrl().newBuilder()
                .addQueryParameter("chat_id", chatId.toString())
                .addQueryParameter("text", text.take(4096))
                .build()
            val req = Request.Builder().url(url).get().build()
            runCatching {
                httpClient.newCall(req).execute().use { it.isSuccessful }
            }.getOrElse { e ->
                XLog.w(TAG, "sendMessage: chatId=$chatId err=${e.message}")
                false
            }
        }

    private fun validateToken(token: String): MeResponse? {
        val url = "https://api.telegram.org/bot$token/getMe".toHttpUrl()
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val root = org.json.JSONObject(body)
                if (!root.optBoolean("ok", false)) return null
                val result = root.optJSONObject("result") ?: return null
                MeResponse(
                    id = result.optLong("id"),
                    username = result.optString("username"),
                    isBot = result.optBoolean("is_bot", false),
                ).takeIf { it.isBot && it.username.isNotBlank() }
            }
        }.getOrElse { e ->
            XLog.w(TAG, "validateToken: err=${e.message}")
            null
        }
    }

    private fun fetchUpdates(token: String, offset: Long): List<String>? {
        val url = "https://api.telegram.org/bot$token/getUpdates".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("timeout", POLL_TIMEOUT_SECONDS.toString())
            .addQueryParameter("allowed_updates", "[\"message\",\"callback_query\"]")
            .build()
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val root = org.json.JSONObject(body)
                if (!root.optBoolean("ok", false)) return null
                val arr = root.optJSONArray("result") ?: return emptyList()
                (0 until arr.length()).map { arr.getJSONObject(it).toString() }
            }
        }.getOrElse { e ->
            XLog.w(TAG, "fetchUpdates: err=${e.message}")
            null
        }
    }

    private fun nextBackoff(current: Long): Long = when (current) {
        BACKOFF_MS_1 -> BACKOFF_MS_2
        BACKOFF_MS_2 -> BACKOFF_MS_3
        else -> BACKOFF_MS_3
    }

    private fun updateStatus(status: Status, username: String?, error: String?) {
        val prev = statusRef.get()
        statusRef.set(
            StatusSnapshot(
                status = status,
                botUsername = username ?: prev.botUsername,
                lastError = error,
                lastUpdateAt = prev.lastUpdateAt,
            )
        )
    }

    private data class MeResponse(val id: Long, val username: String, val isBot: Boolean)
}