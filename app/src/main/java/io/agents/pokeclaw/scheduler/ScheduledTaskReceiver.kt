// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.agents.pokeclaw.utils.XLog

/**
 * AlarmManager-triggered entry point (US-D-021-TASK-SCHEDULER).
 *
 * Receives an Intent with extra "taskId", enqueues a [ScheduledTaskWorker]
 * unique by task id so duplicate alarm deliveries don't double-fire, then
 * tells [TaskScheduler] to compute and schedule the next run.
 */
class ScheduledTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
        const val EXTRA_TASK_ID = "taskId"
        private const val WORK_NAME_PREFIX = "scheduled-task-"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        XLog.i(TAG, "scheduler: fire id=$taskId")

        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(androidx.work.Data.Builder().putString(EXTRA_TASK_ID, taskId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_PREFIX + taskId,
            ExistingWorkPolicy.REPLACE,
            request,
        )

        // Recompute and re-arm the next alarm if this task is still enabled.
        runCatching {
            TaskScheduler.reArmIfEnabled(context, taskId)
        }.onFailure { e ->
            XLog.w(TAG, "scheduler: reArm failed for id=$taskId err=${e.message}", e)
        }
    }
}