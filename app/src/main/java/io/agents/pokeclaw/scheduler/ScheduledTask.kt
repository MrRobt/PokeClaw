// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

/**
 * A scheduled task definition (US-D-021-TASK-SCHEDULER).
 *
 * `schedule` is interpreted differently per [type]:
 *  - [Type.CRON]     — 5-field cron expression "分 时 日 月 周"
 *  - [Type.ONCE]     — epoch millis at which to fire once
 *  - [Type.INTERVAL] — interval in seconds (≥ [MIN_INTERVAL_SEC])
 *
 * `nextRunAt` is denormalized so the UI can list "what fires next" without
 * recomputing the schedule on every load.
 */
data class ScheduledTask(
    val id: String,
    val name: String,
    val type: Type,
    val schedule: String,
    val prompt: String,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    val nextRunAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Type { CRON, ONCE, INTERVAL }

    companion object {
        const val MIN_INTERVAL_SEC = 60L
        const val INVALID_INTERVAL = "INVALID_INTERVAL"
        const val INVALID_CRON = "INVALID_CRON"
        const val INVALID_TIMESTAMP = "INVALID_TIMESTAMP"
    }
}