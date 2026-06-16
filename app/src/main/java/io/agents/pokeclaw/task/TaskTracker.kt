// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.task

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.agents.pokeclaw.utils.XLog

/**
 * Task status state machine + audit log.
 *
 * Per R2 story US-B-CMP-1-3:
 *  - 8-state machine: PENDING / CLAIMED / RUNNING / PAUSED / COMPLETED / FAILED / ABORTED / REJECTED
 *  - Persisted in SQLite (task_track table) for audit/debug
 *  - FIFO cap of 200 transitions (oldest dropped when full)
 *  - Three query entry points: by taskUuid / by actor since timestamp / by status with limit
 */
class TaskTracker(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    enum class TaskStatus {
        PENDING,
        CLAIMED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        ABORTED,
        REJECTED,
    }

    data class Transition(
        val id: Long = 0,
        val taskUuid: String,
        val actor: String,
        val status: TaskStatus,
        val prevStatus: TaskStatus?,
        val occurredAt: Long,
        val note: String = "",
    )

    companion object {
        private const val TAG = "TaskTracker"
        private const val DB_NAME = "pokeclaw_task_track.db"
        private const val DB_VERSION = 1
        const val TABLE = "task_track"
        const val FIFO_CAP = 200
        const val ACTOR_USER = "user"
        const val ACTOR_CLOUD = "cloud"
        const val ACTOR_LOCAL = "local"
        const val ACTOR_SYSTEM = "system"

        /** Validates that the transition is allowed by the state machine. */
        fun isValidTransition(from: TaskStatus?, to: TaskStatus): Boolean {
            if (from == null) return to == TaskStatus.PENDING || to == TaskStatus.CLAIMED || to == TaskStatus.RUNNING
            return when (from) {
                TaskStatus.PENDING -> to in setOf(TaskStatus.CLAIMED, TaskStatus.RUNNING, TaskStatus.ABORTED, TaskStatus.REJECTED, TaskStatus.FAILED)
                TaskStatus.CLAIMED -> to in setOf(TaskStatus.RUNNING, TaskStatus.ABORTED, TaskStatus.REJECTED, TaskStatus.FAILED)
                TaskStatus.RUNNING -> to in setOf(TaskStatus.PAUSED, TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.ABORTED)
                TaskStatus.PAUSED -> to in setOf(TaskStatus.RUNNING, TaskStatus.ABORTED, TaskStatus.FAILED, TaskStatus.REJECTED)
                TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.ABORTED, TaskStatus.REJECTED -> false
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                taskUuid TEXT NOT NULL,
                actor TEXT NOT NULL,
                status TEXT NOT NULL,
                prevStatus TEXT,
                occurredAt INTEGER NOT NULL,
                note TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_track_task ON $TABLE(taskUuid, occurredAt)")
        db.execSQL("CREATE INDEX idx_track_status ON $TABLE(status, occurredAt)")
        XLog.d(TAG, "onCreate: table=$TABLE")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * Record a state transition.
     * Returns the inserted row id, or -1 if the transition is invalid.
     */
    fun transition(
        taskUuid: String,
        newStatus: TaskStatus,
        actor: String = ACTOR_SYSTEM,
        note: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        require(taskUuid.isNotBlank()) { "taskUuid must not be blank" }

        val prevStatus = latestStatus(taskUuid)
        if (!isValidTransition(prevStatus, newStatus)) {
            XLog.w(TAG, "transition: rejected invalid transition task=$taskUuid $prevStatus -> $newStatus")
            return -1L
        }

        val values = ContentValues().apply {
            put("taskUuid", taskUuid)
            put("actor", actor)
            put("status", newStatus.name)
            put("prevStatus", prevStatus?.name)
            put("occurredAt", nowMillis)
            put("note", note.take(maxNoteLength))
        }
        val id = writableDatabase.insert(TABLE, null, values)
        XLog.i(TAG, "task-track transition: task=$taskUuid $prevStatus -> $newStatus actor=$actor note=${note.take(60)}")
        trimToCap()
        return id
    }

    /** All transitions for a task, in chronological order. */
    fun query(taskUuid: String): List<Transition> {
        val rows = mutableListOf<Transition>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "taskUuid", "actor", "status", "prevStatus", "occurredAt", "note"),
            "taskUuid = ?",
            arrayOf(taskUuid),
            null, null, "occurredAt ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(rowToTransition(cursor))
            }
        }
        return rows
    }

    /** All transitions by an actor since a timestamp, newest first. */
    fun query(actor: String, sinceMillis: Long): List<Transition> {
        val rows = mutableListOf<Transition>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "taskUuid", "actor", "status", "prevStatus", "occurredAt", "note"),
            "actor = ? AND occurredAt >= ?",
            arrayOf(actor, sinceMillis.toString()),
            null, null, "occurredAt DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(rowToTransition(cursor))
            }
        }
        return rows
    }

    /** All transitions of a given status, capped by limit, newest first. */
    fun query(status: TaskStatus, limit: Int): List<Transition> {
        val rows = mutableListOf<Transition>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "taskUuid", "actor", "status", "prevStatus", "occurredAt", "note"),
            "status = ?",
            arrayOf(status.name),
            null, null, "occurredAt DESC",
            limit.coerceAtLeast(1).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(rowToTransition(cursor))
            }
        }
        return rows
    }

    /** Most recent status for a task, or null if no transitions recorded. */
    fun latestStatus(taskUuid: String): TaskStatus? {
        readableDatabase.query(
            TABLE,
            arrayOf("status"),
            "taskUuid = ?",
            arrayOf(taskUuid),
            null, null, "occurredAt DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: return null
                return runCatching { TaskStatus.valueOf(name) }.getOrNull()
            }
        }
        return null
    }

    /** Most recent transition for a task, or null. */
    fun latest(taskUuid: String): Transition? {
        readableDatabase.query(
            TABLE,
            arrayOf("id", "taskUuid", "actor", "status", "prevStatus", "occurredAt", "note"),
            "taskUuid = ?",
            arrayOf(taskUuid),
            null, null, "occurredAt DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToNext()) {
                return rowToTransition(cursor)
            }
        }
        return null
    }

    /** Delete all transitions for a task. Returns rows deleted. */
    fun deleteForTask(taskUuid: String): Int {
        val rows = writableDatabase.delete(TABLE, "taskUuid = ?", arrayOf(taskUuid))
        XLog.d(TAG, "deleteForTask: task=$taskUuid rows=$rows")
        return rows
    }

    /** Total transitions recorded. */
    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            return if (cursor.moveToNext()) cursor.getInt(0) else 0
        }
    }

    /** Enforce FIFO cap of FIFO_CAP rows — drop oldest. */
    private fun trimToCap() {
        val total = count()
        if (total <= FIFO_CAP) return
        val toDelete = total - FIFO_CAP
        writableDatabase.execSQL(
            "DELETE FROM $TABLE WHERE id IN (SELECT id FROM $TABLE ORDER BY occurredAt ASC, id ASC LIMIT $toDelete)"
        )
        XLog.d(TAG, "trimToCap: dropped $toDelete old transitions (cap=$FIFO_CAP)")
    }

    private fun rowToTransition(cursor: android.database.Cursor): Transition {
        return Transition(
            id = cursor.getLong(0),
            taskUuid = cursor.getString(1) ?: "",
            actor = cursor.getString(2) ?: "",
            status = runCatching { TaskStatus.valueOf(cursor.getString(3) ?: "") }.getOrNull() ?: TaskStatus.PENDING,
            prevStatus = cursor.getString(4)?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() },
            occurredAt = cursor.getLong(5),
            note = cursor.getString(6) ?: "",
        )
    }

    private val maxNoteLength = 500
}
