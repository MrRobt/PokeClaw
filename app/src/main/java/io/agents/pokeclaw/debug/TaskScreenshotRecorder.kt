// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.debug

import android.content.Context
import android.graphics.Bitmap
import io.agents.pokeclaw.utils.XLog
import java.io.File
import java.io.FileOutputStream

/**
 * Task screenshot recorder — captures key moments during task execution.
 *
 * Storage layout (scoped, auto-cleaned on uninstall):
 *   /Android/data/io.agents.pokeclaw/files/screenshots/{taskUuid}/{seq}.png
 *
 * Per the PRD (US-D-006):
 *  - Capture before/after each tool call
 *  - Capture on failure
 *  - Optional: export to Pictures/PokeClaw/ (uses MediaStore on Android 10+)
 */
class TaskScreenshotRecorder(private val context: Context) {

    companion object {
        private const val TAG = "TaskScreenshot"
        const val EXPORT_DIR = "Pictures/PokeClaw"
    }

    data class Capture(
        val taskUuid: String,
        val seq: Int,
        val filePath: String,
        val sizeBytes: Long,
    )

    private val captureCounter = HashMap<String, Int>()

    /** Reset the counter for a new task. */
    fun beginTask(taskUuid: String) {
        captureCounter[taskUuid] = 0
    }

    /** End-of-task cleanup hook (no-op for now; screenshots persist for the session). */
    fun endTask(taskUuid: String) {
        captureCounter.remove(taskUuid)
    }

    /** Capture a screenshot at a specific step. */
    fun capture(taskUuid: String, label: String = "step"): Capture? {
        try {
            val service = io.agents.pokeclaw.service.ClawAccessibilityService.getInstance()
            val bitmap = service?.takeScreenshot(3000) ?: return null
            val seq = captureCounter.getOrPut(taskUuid) { 0 }.let {
                val next = it + 1
                captureCounter[taskUuid] = next
                next
            }
            val dir = File(context.getExternalFilesDir(null), "screenshots/$taskUuid").apply { mkdirs() }
            val file = File(dir, "${seq}_$label.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            bitmap.recycle()
            val size = file.length()
            XLog.d(TAG, "screenshot: task=$taskUuid seq=$seq size=${size / 1024}Kb path=${file.absolutePath}")
            return Capture(taskUuid, seq, file.absolutePath, size)
        } catch (e: Exception) {
            XLog.w(TAG, "capture: failed: ${e.message}")
            return null
        }
    }

    /** List all screenshots for a task. */
    fun listForTask(taskUuid: String): List<File> {
        val dir = File(context.getExternalFilesDir(null), "screenshots/$taskUuid")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Export all screenshots for a task to Pictures/PokeClaw/ via MediaStore.
     * Returns the number of files exported.
     */
    fun exportAllToGallery(taskUuid: String): Int {
        return try {
            val files = listForTask(taskUuid)
            if (files.isEmpty()) return 0
            var exported = 0
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            files.forEach { file ->
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "${taskUuid}_${file.name}")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, EXPORT_DIR)
                }
                resolver.insert(collection, values)?.let { uri ->
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    exported++
                }
            }
            XLog.i(TAG, "export: task=$taskUuid files=$exported")
            exported
        } catch (e: Exception) {
            XLog.w(TAG, "export failed: ${e.message}")
            0
        }
    }
}
