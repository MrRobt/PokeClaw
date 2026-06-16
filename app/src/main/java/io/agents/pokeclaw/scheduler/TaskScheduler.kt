// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.agents.pokeclaw.utils.XLog

/**
 * Central scheduler that arms and disarms alarms (US-D-021-TASK-SCHEDULER).
 *
 * The AlarmManager fires [ScheduledTaskReceiver] which enqueues a
 * [ScheduledTaskWorker]. The receiver also calls back into [reArmIfEnabled]
 * to keep the loop going.
 */
object TaskScheduler {

    private const val TAG = "TaskScheduler"

    /**
     * Arm the given task. Calculates `nextRunAt` from the task schedule,
     * persists it on the row, and schedules an AlarmManager alarm.
     * No-op when the task is disabled.
     */
    fun enable(context: Context, task: ScheduledTask) {
        if (!task.enabled) {
            disable(context, task)
            return
        }
        val nextRunAt = computeNextRunAt(task)
        val dao = ScheduledTaskDao(context)
        dao.upsert(task.copy(nextRunAt = nextRunAt))
        if (nextRunAt > 0L) {
            armAlarm(context, task.id, nextRunAt)
        }
        XLog.d(TAG, "scheduler: enable id=${task.id} nextRunAt=$nextRunAt")
    }

    /** Disable: cancel the alarm; keep the row but mark enabled=false. */
    fun disable(context: Context, task: ScheduledTask) {
        cancelAlarm(context, task.id)
        val dao = ScheduledTaskDao(context)
        dao.setEnabled(task.id, false)
        XLog.d(TAG, "scheduler: disable id=${task.id}")
    }

    /**
     * Called by the receiver after a fire. Looks the task up, recomputes
     * `nextRunAt`, and arms the next alarm if still enabled.
     */
    fun reArmIfEnabled(context: Context, taskId: String) {
        val dao = ScheduledTaskDao(context)
        val task = dao.get(taskId) ?: return
        if (!task.enabled) return
        // For ONCE tasks the alarm shouldn't fire again — disable.
        if (task.type == ScheduledTask.Type.ONCE) {
            dao.setEnabled(taskId, false)
            cancelAlarm(context, taskId)
            return
        }
        val nextRunAt = computeNextRunAt(task)
        dao.markFired(taskId, System.currentTimeMillis(), nextRunAt)
        if (nextRunAt > 0L) armAlarm(context, taskId, nextRunAt)
    }

    /**
     * Compute the next epoch-millis the task should fire. Returns 0L for
     * unsupported schedules or when the schedule cannot fire in the next year.
     */
    fun computeNextRunAt(task: ScheduledTask): Long {
        val now = System.currentTimeMillis()
        return when (task.type) {
            ScheduledTask.Type.CRON -> CronParser.nextRunAfter(task.schedule, now) ?: 0L
            ScheduledTask.Type.ONCE -> task.schedule.toLongOrNull()?.coerceAtLeast(now) ?: 0L
            ScheduledTask.Type.INTERVAL -> {
                val seconds = task.schedule.toLongOrNull() ?: return 0L
                if (seconds < ScheduledTask.MIN_INTERVAL_SEC) return 0L
                now + seconds * 1000L
            }
        }
    }

    private fun armAlarm(context: Context, taskId: String, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, taskId)
        // Try exact-allow-while-idle; fall back to inexact on older devices that
        // deny exact-alarm permissions.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            XLog.i(TAG, "scheduler: alarm armed id=$taskId at=$triggerAtMillis")
        } catch (e: SecurityException) {
            XLog.w(TAG, "scheduler: exact alarm denied, falling back to setAndAllowWhileIdle", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun cancelAlarm(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context, taskId))
    }

    private fun buildPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = "io.agents.pokeclaw.scheduler.FIRE"
            putExtra(ScheduledTaskReceiver.EXTRA_TASK_ID, taskId)
        }
        // Update current preserves the requestCode-based identity.
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}