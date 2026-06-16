// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * Calendar 工具：query / add / delete events。
 *
 * 权限：READ_CALENDAR / WRITE_CALENDAR（无权限 → 返回 PERMISSION_DENIED 错误）。
 *
 * Action 路径：
 *  - query_events(startMillis, endMillis)  → List<Event>
 *  - add_event(title, startMillis, durationMinutes, description?)
 *  - delete_event(eventId)
 */
class CalendarTool(private val appContext: Context) : BaseTool() {

    companion object {
        private const val TAG = "PokeClaw/CalendarTool"
        const val TOOL_NAME = "calendar"
    }

    override fun getName(): String = TOOL_NAME

    override fun getDisplayName(): String = "Calendar"

    override fun getDescriptionEN(): String =
        "Query, add, or delete calendar events. Actions: query_events | add_event | delete_event."

    override fun getDescriptionCN(): String =
        "查询、新增或删除日历事件。操作：query_events（查询） | add_event（新增） | delete_event（删除）。"

    override fun getParameters(): List<ToolParameter> = listOf(
        ToolParameter("action", "string", "query_events | add_event | delete_event", true),
        ToolParameter("startMillis", "long", "Start time in epoch millis (query/add)", false),
        ToolParameter("endMillis", "long", "End time in epoch millis (query)", false),
        ToolParameter("title", "string", "Event title (add)", false),
        ToolParameter("durationMinutes", "int", "Duration in minutes (add, default 60)", false),
        ToolParameter("description", "string", "Event description (add)", false),
        ToolParameter("eventId", "long", "Event ID to delete (delete)", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: return ToolResult.error("Missing action")
        XLog.d(TAG, "calendar-tool: action=$action")

        // 权限校验
        if (!hasReadPermission()) {
            return ToolResult.error("PERMISSION_DENIED: READ_CALENDAR not granted")
        }
        return try {
            when (action) {
                "query_events" -> {
                    val start = (params["startMillis"] as? Number)?.toLong()
                        ?: (System.currentTimeMillis())
                    val end = (params["endMillis"] as? Number)?.toLong()
                        ?: (start + 7 * 24 * 3600_000L)
                    queryEvents(start, end)
                }
                "add_event" -> {
                    if (!hasWritePermission()) return ToolResult.error("PERMISSION_DENIED: WRITE_CALENDAR not granted")
                    val title = params["title"] as? String ?: return ToolResult.error("Missing title")
                    val start = (params["startMillis"] as? Number)?.toLong() ?: return ToolResult.error("Missing startMillis")
                    val duration = (params["durationMinutes"] as? Number)?.toInt() ?: 60
                    val desc = params["description"] as? String ?: ""
                    addEvent(title, start, duration, desc)
                }
                "delete_event" -> {
                    if (!hasWritePermission()) return ToolResult.error("PERMISSION_DENIED: WRITE_CALENDAR not granted")
                    val id = (params["eventId"] as? Number)?.toLong() ?: return ToolResult.error("Missing eventId")
                    deleteEvent(id)
                }
                else -> ToolResult.error("Unknown action: $action. Allowed: query_events | add_event | delete_event")
            }
        } catch (e: SecurityException) {
            XLog.w(TAG, "calendar-tool: 权限被拒", e)
            ToolResult.error("PERMISSION_DENIED: ${e.message}")
        } catch (e: Exception) {
            XLog.e(TAG, "calendar-tool: 执行失败", e)
            ToolResult.error("CALENDAR_ERROR: ${e.message}")
        }
    }

    private fun hasReadPermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWritePermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryEvents(startMillis: Long, endMillis: Long): ToolResult {
        val resolver: ContentResolver = appContext.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val cursor = resolver.query(uri, projection, selection, selectionArgs, "${CalendarContract.Events.DTSTART} ASC")
            ?: return ToolResult.error("Cursor null")

        val events = JSONArray()
        cursor.use {
            while (it.moveToNext()) {
                events.put(JSONObject().apply {
                    put("id", it.getLong(0))
                    put("title", it.getString(1) ?: "")
                    put("startMillis", it.getLong(2))
                    put("endMillis", it.getLong(3))
                })
            }
        }
        val summary = "Found ${events.length()} events between $startMillis and $endMillis"
        XLog.i(TAG, "calendar-tool: query result count=${events.length()}")
        val payload = JSONObject().apply {
            put("events", events)
            put("summary", summary)
        }
        return ToolResult.success(payload.toString())
    }

    private fun addEvent(title: String, startMillis: Long, durationMinutes: Int, description: String): ToolResult {
        val resolver = appContext.contentResolver
        val endMillis = startMillis + durationMinutes * 60_000L
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, 1)  // primary
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return ToolResult.error("Insert returned null uri")
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: -1L
        XLog.i(TAG, "calendar-tool: add_event id=$eventId title=$title")
        val payload = JSONObject().apply {
            put("eventId", eventId)
            put("title", title)
        }
        return ToolResult.success(payload.toString())
    }

    private fun deleteEvent(eventId: Long): ToolResult {
        val resolver = appContext.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendPath(eventId.toString())
            .build()
        val rows = resolver.delete(uri, null, null)
        XLog.i(TAG, "calendar-tool: delete_event id=$eventId rows=$rows")
        return if (rows > 0) {
            val payload = JSONObject().apply {
                put("deleted", rows)
                put("eventId", eventId)
            }
            ToolResult.success(payload.toString())
        } else {
            ToolResult.error("No event found with id=$eventId")
        }
    }
}
