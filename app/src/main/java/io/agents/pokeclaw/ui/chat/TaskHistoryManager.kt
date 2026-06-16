// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 任务历史数据模型与内存存储。
 *
 * 设计：内存中维护最近 500 条任务，KVUtils 持久化最近 50 条。
 * 每次任务完成时由 TaskOrchestrator 调用 [record]。
 *
 * 后续可扩展为 Room 持久化（结构已抽象为 listOf<TaskRecord>）。
 */
object TaskHistoryManager {

    private const val TAG = "PokeClaw/TaskHistory"
    private const val KEY_RECORDS = "task_history_records"
    private const val MAX_INMEM = 500
    private const val MAX_PERSIST = 50

    /** 任务状态。 */
    enum class Status {
        SUCCESS,
        FAILED,
        RUNNING,
        CANCELLED,
    }

    /** 任务类型。 */
    enum class TaskType {
        CHAT,       // 闲聊
        TASK,       // 端侧任务
        MONITOR,    // 后台监控
        CLOUD_TASK, // 云端任务
        UNKNOWN,
    }

    /** 单条任务记录。 */
    data class TaskRecord(
        val displayTaskId: String,        // CMP-TASK-20260613-0001
        val commercialTaskId: String,     // uuid v7
        val taskText: String,              // 任务原文
        val status: Status,
        val type: TaskType,
        val createdAtMillis: Long,
        val finishedAtMillis: Long,
        val channel: String? = null,
        val modelName: String? = null,
        val totalTokens: Int = 0,
        val totalCost: Double = 0.0,
        val toolCalls: Int = 0,
    )

    /** 过滤条件。 */
    data class Filter(
        val query: String? = null,
        val status: Status? = null,
        val timeRange: TimeRange = TimeRange.ALL,
        val type: TaskType? = null,
        val page: Int = 0,
        val pageSize: Int = 20,
    )

    /** 时间范围（按 createdAtMillis 过滤）。 */
    enum class TimeRange(val displayName: String) {
        TODAY("Today"),
        LAST_7_DAYS("Last 7 days"),
        LAST_30_DAYS("Last 30 days"),
        ALL("All"),
    }

    // 内存存储
    private val records = CopyOnWriteArrayList<TaskRecord>()

    init {
        loadFromStorage()
    }

    /**
     * 记录一条任务。
     */
    fun record(record: TaskRecord) {
        synchronized(records) {
            records.add(0, record)
            if (records.size > MAX_INMEM) {
                val toRemove = records.size - MAX_INMEM
                repeat(toRemove) { records.removeAt(records.size - 1) }
            }
        }
        persistToStorage()
        XLog.d(TAG, "record: ${record.displayTaskId} status=${record.status} type=${record.type}")
    }

    /**
     * 工厂方法：生成 displayTaskId 和 commercialTaskId。
     */
    fun newRecord(
        taskText: String,
        status: Status,
        type: TaskType,
        channel: String? = null,
        modelName: String? = null,
        totalTokens: Int = 0,
        totalCost: Double = 0.0,
        toolCalls: Int = 0,
    ): TaskRecord {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val seq = synchronized(records) {
            records.count { it.displayTaskId.contains("-$date-") } + 1
        }
        return TaskRecord(
            displayTaskId = "CMP-TASK-$date-${seq.toString().padStart(4, '0')}",
            commercialTaskId = UUID.randomUUID().toString(),  // 简化：使用 uuid v4
            taskText = taskText.take(500),
            status = status,
            type = type,
            createdAtMillis = now,
            finishedAtMillis = now,
            channel = channel,
            modelName = modelName,
            totalTokens = totalTokens,
            totalCost = totalCost,
            toolCalls = toolCalls,
        )
    }

    /**
     * 查询（应用过滤 + 分页）。
     */
    fun query(filter: Filter): QueryResult {
        val all = synchronized(records) { records.toList() }
        val now = System.currentTimeMillis()
        val cutoff = when (filter.timeRange) {
            TimeRange.TODAY -> startOfTodayMillis()
            TimeRange.LAST_7_DAYS -> now - 7 * 24 * 3600_000L
            TimeRange.LAST_30_DAYS -> now - 30 * 24 * 3600_000L
            TimeRange.ALL -> 0L
        }

        val filtered = all.asSequence()
            .filter { it.createdAtMillis >= cutoff }
            .filter { filter.status == null || it.status == filter.status }
            .filter { filter.type == null || it.type == filter.type }
            .filter { record ->
                if (filter.query.isNullOrBlank()) return@filter true
                val q = filter.query.trim()
                record.displayTaskId.contains(q, ignoreCase = true) ||
                    record.commercialTaskId.contains(q, ignoreCase = true) ||
                    record.taskText.contains(q, ignoreCase = true)
            }
            .sortedByDescending { it.createdAtMillis }
            .toList()

        val totalCount = filtered.size
        val fromIndex = filter.page * filter.pageSize
        val toIndex = (fromIndex + filter.pageSize).coerceAtMost(totalCount)
        val page = if (fromIndex >= totalCount) emptyList() else filtered.subList(fromIndex, toIndex)
        return QueryResult(records = page, totalCount = totalCount, hasMore = toIndex < totalCount)
    }

    /**
     * 总数（无过滤）。
     */
    fun totalCount(): Int = records.size

    /**
     * 清除所有记录。
     */
    fun clear() {
        synchronized(records) { records.clear() }
        KVUtils.remove(KEY_RECORDS)
        XLog.i(TAG, "clear: 历史记录已清空")
    }

    /**
     * 查询结果。
     */
    data class QueryResult(
        val records: List<TaskRecord>,
        val totalCount: Int,
        val hasMore: Boolean,
    )

    // ---- 持久化 ----

    private fun loadFromStorage() {
        try {
            val raw = KVUtils.getString(KEY_RECORDS) ?: return
            // 简化：KV 存储为单行 JSON 数组字符串，结构 id|text|status|type|createdAt|finishedAt|channel|model|tokens|cost|tools
            val lines = raw.split("\n").filter { it.isNotBlank() }
            for (line in lines) {
                val parts = line.split("|")
                if (parts.size >= 6) {
                    val rec = TaskRecord(
                        displayTaskId = parts.getOrNull(0) ?: continue,
                        commercialTaskId = parts.getOrNull(1) ?: continue,
                        taskText = parts.getOrNull(2) ?: "",
                        status = runCatching { Status.valueOf(parts.getOrNull(3) ?: "SUCCESS") }.getOrDefault(Status.SUCCESS),
                        type = runCatching { TaskType.valueOf(parts.getOrNull(4) ?: "UNKNOWN") }.getOrDefault(TaskType.UNKNOWN),
                        createdAtMillis = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                        finishedAtMillis = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
                        channel = parts.getOrNull(7)?.takeIf { it.isNotEmpty() },
                        modelName = parts.getOrNull(8)?.takeIf { it.isNotEmpty() },
                        totalTokens = parts.getOrNull(9)?.toIntOrNull() ?: 0,
                        totalCost = parts.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
                        toolCalls = parts.getOrNull(11)?.toIntOrNull() ?: 0,
                    )
                    records.add(rec)
                }
            }
            XLog.i(TAG, "loadFromStorage: 加载 ${records.size} 条历史")
        } catch (e: Exception) {
            XLog.w(TAG, "loadFromStorage 失败", e)
        }
    }

    private fun persistToStorage() {
        try {
            val toSave = synchronized(records) { records.take(MAX_PERSIST) }
            val lines = toSave.joinToString("\n") { r ->
                "${r.displayTaskId}|${r.commercialTaskId}|${r.taskText.replace("|", "/").replace("\n", " ")}|" +
                    "${r.status}|${r.type}|${r.createdAtMillis}|${r.finishedAtMillis}|" +
                    "${r.channel ?: ""}|${r.modelName ?: ""}|${r.totalTokens}|${r.totalCost}|${r.toolCalls}"
            }
            KVUtils.putString(KEY_RECORDS, lines)
        } catch (e: Exception) {
            XLog.w(TAG, "persistToStorage 失败", e)
        }
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
