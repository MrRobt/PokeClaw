// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 离线结果队列抽象 — 让 RetrofitDeviceCloudClient 解耦具体持久化实现。
// Android 运行时由 [CloudEventQueue] 实现（SharedPreferences + Gson 持久化），
// 单测可注入 InMemoryOfflineQueue 等 fake。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.TaskResultRequest

/**
 * 离线结果队列接口。
 *
 * 行为契约：
 * - enqueue：把无法上报的结果入队；返回 true=成功，false=队列已满或异常
 * - peekDue：返回到达重试时间的事件列表（按 createdAt 升序）
 * - markSucceeded：从队列移除指定事件
 * - markFailed：增加重试计数 + 推后 nextAttemptAt
 * - size：当前队列长度
 */
interface OfflineResultQueue {
    fun enqueue(taskUuid: String, payload: TaskResultRequest, nowMillis: Long): Boolean
    fun peekDue(nowMillis: Long, limit: Int): List<QueuedResult>
    fun markSucceeded(requestId: String)
    fun markFailed(requestId: String, nowMillis: Long)
    fun size(): Int
}

/** 队列中的事件快照（与具体实现解耦）。 */
data class QueuedResult(
    val requestId: String,
    val taskUuid: String,
    val payload: TaskResultRequest,
    val createdAtMillis: Long,
    val nextAttemptAtMillis: Long,
    val retryCount: Int,
)

/**
 * Android 运行时适配：把 SharedPreferences 版的 CloudEventQueue 包成 OfflineResultQueue。
 */
class CloudEventQueueAdapter(
    private val queue: CloudEventQueue,
) : OfflineResultQueue {

    override fun enqueue(taskUuid: String, payload: TaskResultRequest, nowMillis: Long): Boolean {
        return try {
            queue.enqueue(taskUuid, payload, nowMillis)
            true
        } catch (e: Exception) {
            io.agents.pokeclaw.utils.XLog.e("OfflineResultQueue", "enqueue 失败", e)
            false
        }
    }

    override fun peekDue(nowMillis: Long, limit: Int): List<QueuedResult> {
        return queue.peekDue(nowMillis, limit).map { event ->
            QueuedResult(
                requestId = event.requestId,
                taskUuid = event.taskUuid,
                payload = event.payload,
                createdAtMillis = event.createdAtMillis,
                nextAttemptAtMillis = event.nextAttemptAtMillis,
                retryCount = event.retryCount,
            )
        }
    }

    override fun markSucceeded(requestId: String) {
        queue.markSucceeded(requestId)
    }

    override fun markFailed(requestId: String, nowMillis: Long) {
        queue.markFailed(requestId, nowMillis)
    }

    override fun size(): Int = queue.size()
}
