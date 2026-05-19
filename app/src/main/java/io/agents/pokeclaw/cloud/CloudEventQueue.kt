// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端事件离线队列 — 网络不可用时有限缓存，恢复后按幂等编号补报。

package io.agents.pokeclaw.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.utils.XLog
import java.util.UUID

/**
 * 任务结果离线队列。
 *
 * 设计边界：只缓存云端回传事件，不缓存原始通知正文、联系人列表、提示词或密钥。
 */
class CloudEventQueue(context: Context, private val maxSize: Int = DEFAULT_MAX_SIZE) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class PendingCloudEvent(
        val requestId: String,
        val taskUuid: String,
        val payload: TaskResultRequest,
        val createdAtMillis: Long,
        val retryCount: Int = 0,
        val nextAttemptAtMillis: Long = createdAtMillis,
    )

    fun enqueue(taskUuid: String, payload: TaskResultRequest, nowMillis: Long = System.currentTimeMillis()): PendingCloudEvent {
        require(taskUuid.isNotBlank()) { "taskUuid 不能为空" }
        val sanitized = payload.sanitized()
        val event = PendingCloudEvent(
            requestId = UUID.randomUUID().toString(),
            taskUuid = taskUuid,
            payload = sanitized,
            createdAtMillis = nowMillis,
        )
        val updated = (loadAll() + event).takeLast(maxSize.coerceAtLeast(1))
        saveAll(updated)
        XLog.w(TAG, "enqueue: 云端结果进入离线队列，taskUuid=$taskUuid, requestId=${event.requestId}, size=${updated.size}")
        return event
    }

    fun peekDue(nowMillis: Long = System.currentTimeMillis(), limit: Int = DEFAULT_FLUSH_LIMIT): List<PendingCloudEvent> {
        return loadAll()
            .filter { it.nextAttemptAtMillis <= nowMillis }
            .sortedBy { it.createdAtMillis }
            .take(limit.coerceAtLeast(1))
    }

    fun markSucceeded(requestId: String) {
        val updated = loadAll().filterNot { it.requestId == requestId }
        saveAll(updated)
        XLog.i(TAG, "markSucceeded: 离线事件补报成功，requestId=$requestId, remaining=${updated.size}")
    }

    fun markFailed(requestId: String, nowMillis: Long = System.currentTimeMillis()) {
        val updated = loadAll().map { event ->
            if (event.requestId == requestId) {
                val nextRetryCount = event.retryCount + 1
                val delayMillis = retryDelayMillis(nextRetryCount)
                event.copy(
                    retryCount = nextRetryCount,
                    nextAttemptAtMillis = nowMillis + delayMillis,
                )
            } else {
                event
            }
        }
        saveAll(updated)
        XLog.w(TAG, "markFailed: 离线事件补报失败，requestId=$requestId")
    }

    fun size(): Int = loadAll().size

    fun clear() {
        prefs.edit().remove(KEY_EVENTS).apply()
        XLog.i(TAG, "clear: 离线事件队列已清空")
    }

    fun loadAll(): List<PendingCloudEvent> {
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<PendingCloudEvent>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            XLog.e(TAG, "loadAll: 离线队列解析失败，已丢弃损坏数据", e)
            emptyList()
        }
    }

    private fun saveAll(events: List<PendingCloudEvent>) {
        prefs.edit().putString(KEY_EVENTS, gson.toJson(events)).apply()
    }

    private fun TaskResultRequest.sanitized(): TaskResultRequest {
        return copy(
            result = result?.take(MAX_TEXT_LENGTH),
            errorMessage = errorMessage?.take(MAX_TEXT_LENGTH),
            toolCalls = toolCalls?.take(MAX_TEXT_LENGTH),
            evidenceUrls = evidenceUrls?.take(MAX_TEXT_LENGTH),
            errorCategory = errorCategory?.take(MAX_FIELD_LENGTH),
            errorCode = errorCode?.take(MAX_FIELD_LENGTH),
            errorDetail = errorDetail?.take(MAX_TEXT_LENGTH),
            suggestedAction = suggestedAction?.take(MAX_TEXT_LENGTH),
            screenshotBase64 = screenshotBase64?.take(MAX_BASE64_LENGTH),
            logSnippet = logSnippet?.take(MAX_TEXT_LENGTH),
        )
    }

    companion object {
        private const val TAG = "PokeClaw/CloudEventQueue"
        private const val PREF_NAME = "pokeclaw_cloud_event_queue"
        private const val KEY_EVENTS = "events"
        private const val DEFAULT_MAX_SIZE = 100
        private const val DEFAULT_FLUSH_LIMIT = 10
        private const val MAX_TEXT_LENGTH = 2048
        private const val MAX_FIELD_LENGTH = 128
        private const val MAX_BASE64_LENGTH = 500000

        fun retryDelayMillis(retryCount: Int): Long {
            val safeRetry = retryCount.coerceIn(1, 6)
            return 1000L * (1 shl (safeRetry - 1))
        }
    }
}
