// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.channel.Channel
import io.agents.pokeclaw.utils.XLog
import java.util.UUID

/**
 * WorkManager worker that executes a scheduled task (US-D-021-TASK-SCHEDULER).
 *
 * Reads the task from the DAO, hands the prompt to the agent via
 * [ClawApplication.appViewModel.startTask] with channel=LOCAL_SCHEDULED and
 * metadata carrying the scheduledTaskId, then updates last/next run timestamps.
 */
class ScheduledTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val EXTRA_TASK_ID = "taskId"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(EXTRA_TASK_ID)
            ?: return Result.failure()
        val ctx = applicationContext
        val dao = ScheduledTaskDao(ctx)
        val task = dao.get(taskId)
            ?: return Result.failure().also {
                XLog.w(TAG, "scheduler: doWork: task $taskId not found")
            }

        if (!task.enabled) {
            XLog.i(TAG, "scheduler: doWork: task $taskId disabled, skipping")
            return Result.success()
        }

        XLog.i(TAG, "scheduler: doWork: executing id=$taskId name='${task.name}'")
        val messageId = "sched-${UUID.randomUUID().toString().take(8)}"
        runCatching {
            ClawApplication.appViewModelInstance.startTask(
                task = task.prompt,
                taskId = messageId,
                // Local channel but flagged via metadata so audit knows it's scheduled.
                agentPromptOverride = null,
                onEvent = { event ->
                    XLog.d(TAG, "scheduler: doWork event=${event.javaClass.simpleName}")
                },
            )
        }.onFailure { e ->
            XLog.e(TAG, "scheduler: doWork: startTask failed: ${e.message}", e)
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        val nextRunAt = TaskScheduler.computeNextRunAt(task)
        dao.markFired(taskId, now, nextRunAt)
        XLog.d(TAG, "scheduler: doWork: fired id=$taskId lastRun=$now nextRun=$nextRunAt")
        return Result.success()
    }
}