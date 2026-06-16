// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Aggregates task history into rollups for stats display.
 *
 * Stored in KV as JSON (manual encoding) to avoid Room schema migration.
 * Buckets:
 *  - Total counts by type (chat / task / monitor / cloud-task)
 *  - Total counts by status (success / fail / timeout / cancelled)
 *  - Daily counts for last 7 days
 */
object TaskStatisticsManager {

    private const val TAG = "TaskStats"
    private const val KV_KEY = "task_stats_v1"
    const val WINDOW_DAYS = 7

    data class Stats(
        val totalCount: Int,
        val successCount: Int,
        val failureCount: Int,
        val timeoutCount: Int,
        val cancelledCount: Int,
        val byType: Map<String, Int>,
        val byDay: List<DailyBucket>,
        val successRate: Float,
    ) {
        val failureRate: Float get() = if (totalCount == 0) 0f else failureCount.toFloat() / totalCount
    }

    data class DailyBucket(val day: String, val count: Int, val successCount: Int)

    /** Compute stats from the in-memory + KV task history. */
    fun compute(context: Context, windowDays: Int = WINDOW_DAYS): Stats {
        val history: List<TaskHistoryManager.TaskRecord> = TaskHistoryManager.query(
            TaskHistoryManager.Filter(timeRange = TaskHistoryManager.TimeRange.ALL)
        ).records
        val cutoff = System.currentTimeMillis() - windowDays * 24L * 3600_000L
        val windowed = history.filter { it.createdAtMillis >= cutoff }

        val total = windowed.size
        val success = windowed.count { it.status == TaskHistoryManager.Status.SUCCESS }
        val failure = windowed.count { it.status == TaskHistoryManager.Status.FAILED }
        // RUNNING counts as "in-flight", not a terminal state; treat as neither timeout nor cancelled.
        val timeout = 0
        val cancelled = windowed.count { it.status == TaskHistoryManager.Status.CANCELLED }

        val byType = windowed.groupingBy { it.type.name }.eachCount()
        val byDay = computeDailyBuckets(windowed, windowDays)
        val rate = if (total == 0) 0f else success.toFloat() / total

        val stats = Stats(
            totalCount = total,
            successCount = success,
            failureCount = failure,
            timeoutCount = timeout,
            cancelledCount = cancelled,
            byType = byType,
            byDay = byDay,
            successRate = rate,
        )
        XLog.d(TAG, "stats: window=${windowDays}d success=$success fail=$failure rate=${"%.2f".format(rate)}")
        return stats
    }

    private fun computeDailyBuckets(history: List<TaskHistoryManager.TaskRecord>, days: Int): List<DailyBucket> {
        val now = System.currentTimeMillis()
        val buckets = mutableListOf<DailyBucket>()
        for (i in days - 1 downTo 0) {
            val dayStart = now - i * 24L * 3600_000L
            val dayEnd = dayStart + 24L * 3600_000L
            val inDay = history.filter { it.createdAtMillis in dayStart until dayEnd }
            val successCount = inDay.count { it.status == TaskHistoryManager.Status.SUCCESS }
            val dayLabel = android.text.format.DateFormat.format("MM-dd", dayStart).toString()
            buckets.add(DailyBucket(dayLabel, inDay.size, successCount))
        }
        return buckets
    }

    /** Persist a snapshot for quick load (e.g., home page widget). */
    fun persistSnapshot(context: Context, stats: Stats) {
        val sb = StringBuilder()
        sb.append("total=${stats.totalCount};")
        sb.append("success=${stats.successCount};")
        sb.append("fail=${stats.failureCount};")
        sb.append("timeout=${stats.timeoutCount};")
        sb.append("cancelled=${stats.cancelledCount};")
        sb.append("rate=${stats.successRate}")
        KVUtils.putString(KV_KEY, sb.toString())
    }

    fun loadSnapshot(context: Context): String? = KVUtils.getString(KV_KEY)
}
