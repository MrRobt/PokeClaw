// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import io.agents.pokeclaw.utils.XLog
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Files 工具：search / read / share。
 *
 * 权限：READ_EXTERNAL_STORAGE (Android 10 以前) / scoped storage (Android 11+)。
 * 无权限时仍可读取 app 私有目录（filesDir、cacheDir）— 这是安全区。
 *
 * Action 路径：
 *  - search_files(query, basePath?)         → List<FileEntry>
 *  - read_file_content(path)                 → 文本内容
 *  - share_file(path, mimeType?)             → Intent.ACTION_SEND
 */
class FilesTool(private val appContext: Context) : BaseTool() {

    companion object {
        private const val TAG = "PokeClaw/FilesTool"
        const val TOOL_NAME = "files"
        private const val FILE_PROVIDER_AUTHORITY = "io.agents.pokeclaw.fileprovider"
    }

    override fun getName(): String = TOOL_NAME

    override fun getDisplayName(): String = "Files"

    override fun getDescriptionEN(): String =
        "Search, read, or share files on device. Actions: search_files | read_file_content | share_file."

    override fun getDescriptionCN(): String =
        "在设备上搜索、读取或分享文件。操作：search_files（搜索） | read_file_content（读取） | share_file（分享）。"

    override fun getParameters(): List<ToolParameter> = listOf(
        ToolParameter("action", "string", "search_files | read_file_content | share_file", true),
        ToolParameter("query", "string", "Filename keyword (search)", false),
        ToolParameter("basePath", "string", "Base path to search (default: app filesDir)", false),
        ToolParameter("path", "string", "Absolute file path (read/share)", false),
        ToolParameter("mimeType", "string", "MIME type for share (default: auto-detect)", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: return ToolResult.error("Missing action")
        XLog.d(TAG, "files-tool: action=$action")
        return try {
            when (action) {
                "search_files" -> {
                    val query = params["query"] as? String ?: return ToolResult.error("Missing query")
                    val basePath = params["basePath"] as? String ?: appContext.filesDir.absolutePath
                    searchFiles(query, basePath)
                }
                "read_file_content" -> {
                    val path = params["path"] as? String ?: return ToolResult.error("Missing path")
                    readFileContent(path)
                }
                "share_file" -> {
                    val path = params["path"] as? String ?: return ToolResult.error("Missing path")
                    val mime = params["mimeType"] as? String ?: "application/octet-stream"
                    shareFile(path, mime)
                }
                else -> ToolResult.error("Unknown action: $action. Allowed: search_files | read_file_content | share_file")
            }
        } catch (e: SecurityException) {
            XLog.w(TAG, "files-tool: 权限被拒", e)
            ToolResult.error("PERMISSION_DENIED: ${e.message}")
        } catch (e: Exception) {
            XLog.e(TAG, "files-tool: 执行失败", e)
            ToolResult.error("FILES_ERROR: ${e.message}")
        }
    }

    private fun searchFiles(query: String, basePath: String): ToolResult {
        val baseDir = File(basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            return ToolResult.error("Base path not found or not a directory: $basePath")
        }
        val results = mutableListOf<Map<String, Any>>()
        val q = query.lowercase()
        val maxResults = 200
        baseDir.walkTopDown()
            .onEnter { f -> f.isDirectory || f.name.lowercase().contains(q) }
            .forEach { f ->
                if (results.size >= maxResults) return@forEach
                if (f.isFile) {
                    results.add(mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "size" to f.length(),
                        "lastModified" to f.lastModified(),
                    ))
                }
            }
        val summary = "Found ${results.size} files matching '$query' under $basePath"
        XLog.i(TAG, "files-tool: search query=$query count=${results.size}")
        val payload = JSONObject().apply {
            put("files", JSONArray(results))
            put("summary", summary)
        }
        return ToolResult.success(payload.toString())
    }

    private fun readFileContent(path: String): ToolResult {
        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("File not found: $path")
        }
        if (file.length() > 1_000_000) {
            return ToolResult.error("File too large (>1MB): $path")
        }
        if (!file.canRead()) {
            return ToolResult.error("PERMISSION_DENIED: cannot read $path")
        }
        val content = file.readText(Charsets.UTF_8)
        XLog.i(TAG, "files-tool: read path=$path bytes=${file.length()}")
        val payload = JSONObject().apply {
            put("path", path)
            put("size", file.length())
            put("content", content)
        }
        return ToolResult.success(payload.toString())
    }

    private fun shareFile(path: String, mimeType: String): ToolResult {
        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("File not found: $path")
        }
        val authority = "$FILE_PROVIDER_AUTHORITY"
        val uri: Uri = try {
            FileProvider.getUriForFile(appContext, authority, file)
        } catch (e: Exception) {
            // 退回 file:// — 仅适用于旧版（API<24）
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
        XLog.i(TAG, "files-tool: share path=$path mime=$mimeType")
        val payload = JSONObject().apply {
            put("shared", path)
            put("mime", mimeType)
        }
        return ToolResult.success(payload.toString())
    }
}
