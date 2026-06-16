// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.debug

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.agents.pokeclaw.utils.XLog
import org.json.JSONObject

/**
 * Records each step of a task execution for later playback/debugging.
 *
 * Persisted in SQLite (task_steps table) so users can review even after a restart.
 *
 * Per the PRD (US-D-004):
 *  - Records: model_response / tool_call / tool_result / screenshot / text_observation
 *  - Schema: id, taskUuid, seq, type, input, output, screenshotPath?, durationMs, timestamp
 */
class TaskStepRecorder(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "TaskStep"
        private const val DB_NAME = "pokeclaw_task_steps.db"
        private const val DB_VERSION = 1
        const val TABLE = "task_steps"
    }

    data class Step(
        val id: Long = 0,
        val taskUuid: String,
        val seq: Int,
        val type: String,        // model_response | tool_call | tool_result | screenshot | text_observation
        val input: String,
        val output: String,
        val screenshotPath: String? = null,
        val durationMs: Long,
        val timestamp: Long,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                taskUuid TEXT NOT NULL,
                seq INTEGER NOT NULL,
                type TEXT NOT NULL,
                input TEXT,
                output TEXT,
                screenshotPath TEXT,
                durationMs INTEGER,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_steps_task ON $TABLE(taskUuid, seq)")
        XLog.d(TAG, "onCreate: table=$TABLE")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** Record a step. Returns the inserted row id. */
    fun record(step: Step): Long {
        val values = ContentValues().apply {
            put("taskUuid", step.taskUuid)
            put("seq", step.seq)
            put("type", step.type)
            put("input", step.input)
            put("output", step.output)
            put("screenshotPath", step.screenshotPath)
            put("durationMs", step.durationMs)
            put("timestamp", step.timestamp)
        }
        val id = writableDatabase.insert(TABLE, null, values)
        XLog.d(TAG, "record: task=${step.taskUuid} seq=${step.seq} type=${step.type} id=$id")
        return id
    }

    /** Load all steps for a task in order. */
    fun loadForTask(taskUuid: String): List<Step> {
        val steps = mutableListOf<Step>()
        readableDatabase.query(
            TABLE,
            arrayOf("id", "taskUuid", "seq", "type", "input", "output", "screenshotPath", "durationMs", "timestamp"),
            "taskUuid = ?",
            arrayOf(taskUuid),
            null, null, "seq ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                steps.add(Step(
                    id = cursor.getLong(0),
                    taskUuid = cursor.getString(1),
                    seq = cursor.getInt(2),
                    type = cursor.getString(3),
                    input = cursor.getString(4) ?: "",
                    output = cursor.getString(5) ?: "",
                    screenshotPath = cursor.getString(6),
                    durationMs = cursor.getLong(7),
                    timestamp = cursor.getLong(8),
                ))
            }
        }
        return steps
    }

    /** Compute aggregate stats for a task. */
    fun statsForTask(taskUuid: String): Map<String, Any> {
        val steps = loadForTask(taskUuid)
        val totalMs = steps.sumOf { it.durationMs }
        val toolCount = steps.count { it.type == "tool_call" }
        val modelCount = steps.count { it.type == "model_response" }
        return mapOf(
            "stepCount" to steps.size,
            "totalDurationMs" to totalMs,
            "toolCallCount" to toolCount,
            "modelCallCount" to modelCount,
        )
    }

    /** Delete all steps for a task. */
    fun deleteForTask(taskUuid: String): Int {
        val rows = writableDatabase.delete(TABLE, "taskUuid = ?", arrayOf(taskUuid))
        XLog.d(TAG, "deleteForTask: task=$taskUuid rows=$rows")
        return rows
    }

    /** Export all steps for a task to a JSON file in Downloads. */
    fun exportToJson(context: Context, taskUuid: String): String? {
        return try {
            val steps = loadForTask(taskUuid)
            val arr = org.json.JSONArray()
            steps.forEach { step ->
                val obj = JSONObject().apply {
                    put("seq", step.seq)
                    put("type", step.type)
                    put("input", step.input)
                    put("output", step.output)
                    put("screenshotPath", step.screenshotPath ?: JSONObject.NULL)
                    put("durationMs", step.durationMs)
                    put("timestamp", step.timestamp)
                }
                arr.put(obj)
            }
            val json = JSONObject().apply {
                put("taskUuid", taskUuid)
                put("stepCount", steps.size)
                put("steps", arr)
            }
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloads, "task_${taskUuid}_steps.json")
            file.writeText(json.toString(2))
            XLog.i(TAG, "exportToJson: task=$taskUuid path=${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            XLog.w(TAG, "exportToJson failed: ${e.message}")
            null
        }
    }
}
