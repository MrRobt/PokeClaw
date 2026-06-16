// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.agents.pokeclaw.utils.XLog

/**
 * SQLite-backed DAO for scheduled tasks (US-D-021-TASK-SCHEDULER).
 *
 * Table: scheduled_tasks
 *  id TEXT PRIMARY KEY, name TEXT, type TEXT, schedule TEXT, prompt TEXT,
 *  enabled INTEGER, last_run_at INTEGER, next_run_at INTEGER, created_at INTEGER
 */
class ScheduledTaskDao(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "ScheduledTaskDao"
        private const val DB_NAME = "pokeclaw_scheduled_tasks.db"
        private const val DB_VERSION = 1
        const val TABLE = "scheduled_tasks"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                schedule TEXT NOT NULL,
                prompt TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_run_at INTEGER NOT NULL DEFAULT 0,
                next_run_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sched_next ON $TABLE(enabled, next_run_at)")
        XLog.d(TAG, "onCreate: table=$TABLE")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun upsert(task: ScheduledTask): Long {
        val values = ContentValues().apply {
            put("id", task.id)
            put("name", task.name)
            put("type", task.type.name)
            put("schedule", task.schedule)
            put("prompt", task.prompt)
            put("enabled", if (task.enabled) 1 else 0)
            put("last_run_at", task.lastRunAt)
            put("next_run_at", task.nextRunAt)
            put("created_at", task.createdAt)
        }
        return writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(id: String): ScheduledTask? {
        readableDatabase.query(
            TABLE,
            arrayOf("id", "name", "type", "schedule", "prompt", "enabled", "last_run_at", "next_run_at", "created_at"),
            "id = ?", arrayOf(id),
            null, null, null, "1"
        ).use { cursor ->
            if (cursor.moveToNext()) return rowToTask(cursor)
        }
        return null
    }

    fun listAll(): List<ScheduledTask> {
        val out = mutableListOf<ScheduledTask>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "name", "type", "schedule", "prompt", "enabled", "last_run_at", "next_run_at", "created_at"),
            null, null, null, null, "next_run_at ASC, name ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out += rowToTask(cursor)
            }
        }
        return out
    }

    fun listEnabled(): List<ScheduledTask> = listAll().filter { it.enabled }

    fun setEnabled(id: String, enabled: Boolean) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET enabled = ? WHERE id = ?",
            arrayOf<Any>(if (enabled) 1 else 0, id)
        )
    }

    fun markFired(id: String, lastRunAt: Long, nextRunAt: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET last_run_at = ?, next_run_at = ? WHERE id = ?",
            arrayOf<Any>(lastRunAt, nextRunAt, id)
        )
    }

    fun delete(id: String): Int =
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))

    private fun rowToTask(cursor: android.database.Cursor): ScheduledTask {
        val typeName = cursor.getString(2) ?: "ONCE"
        val type = runCatching { ScheduledTask.Type.valueOf(typeName) }.getOrDefault(ScheduledTask.Type.ONCE)
        return ScheduledTask(
            id = cursor.getString(0) ?: "",
            name = cursor.getString(1) ?: "",
            type = type,
            schedule = cursor.getString(3) ?: "",
            prompt = cursor.getString(4) ?: "",
            enabled = cursor.getInt(5) != 0,
            lastRunAt = cursor.getLong(6),
            nextRunAt = cursor.getLong(7),
            createdAt = cursor.getLong(8),
        )
    }
}