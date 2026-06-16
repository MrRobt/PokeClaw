// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import android.content.Context
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced model download controller with pause/resume + speed/ETA computation.
 *
 * Wraps [LocalModelManager.downloadModel] and exposes a richer state model
 * for the download UI card (US-D-003).
 *
 * State transitions:
 *   IDLE → STARTING → DOWNLOADING ↔ PAUSED
 *         → COMPLETED  → TRIGGERS_FIRST_RUN
 *         → FAILED     → user can RETRY → STARTING
 */
class ModelDownloadController(
    private val context: Context,
    private val onState: (DownloadState) -> Unit,
) {
    companion object {
        private const val TAG = "ModelDownload"
    }

    data class DownloadState(
        val phase: Phase,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val speedBps: Long = 0,
        val etaSeconds: Long = 0,
        val percent: Int = 0,
        val errorMessage: String? = null,
        val model: LocalModelManager.ModelInfo? = null,
    ) {
        val isPaused: Boolean get() = phase == Phase.PAUSED
        val isActive: Boolean get() = phase == Phase.DOWNLOADING || phase == Phase.PAUSED
    }

    enum class Phase { IDLE, STARTING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    private val paused = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private var currentModel: LocalModelManager.ModelInfo? = null
    private var lastReportedBytes = 0L

    /** Start a new download. */
    fun start(model: LocalModelManager.ModelInfo) {
        if (currentModel != null) {
            XLog.w(TAG, "start: download already in progress for ${currentModel?.id}")
            return
        }
        currentModel = model
        cancelled.set(false)
        paused.set(false)
        lastReportedBytes = 0L
        onState(DownloadState(phase = Phase.STARTING, totalBytes = model.sizeBytes, model = model))
        LocalModelManager.downloadModel(
            context = context,
            model = model,
            callback = object : LocalModelManager.DownloadCallback {
                override fun onProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long) {
                    if (cancelled.get()) return
                    if (paused.get()) {
                        onState(DownloadState(
                            phase = Phase.PAUSED,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            speedBps = 0,
                            etaSeconds = 0,
                            percent = percentOf(bytesDownloaded, totalBytes),
                            model = model,
                        ))
                        return
                    }
                    val eta = if (bytesPerSecond > 0) (totalBytes - bytesDownloaded) / bytesPerSecond else 0L
                    onState(DownloadState(
                        phase = Phase.DOWNLOADING,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBps = bytesPerSecond,
                        etaSeconds = eta,
                        percent = percentOf(bytesDownloaded, totalBytes),
                        model = model,
                    ))
                }

                override fun onComplete(modelPath: String) {
                    if (cancelled.get()) {
                        XLog.d(TAG, "download cancelled; onComplete ignored")
                        return
                    }
                    currentModel = null
                    onState(DownloadState(
                        phase = Phase.COMPLETED,
                        bytesDownloaded = model.sizeBytes,
                        totalBytes = model.sizeBytes,
                        percent = 100,
                        model = model,
                    ))
                    XLog.i(TAG, "download: state=complete, model=${model.id}")
                }

                override fun onError(error: String) {
                    if (cancelled.get()) return
                    currentModel = null
                    onState(DownloadState(
                        phase = Phase.FAILED,
                        errorMessage = error,
                        model = model,
                    ))
                    XLog.w(TAG, "download: state=failed, error=$error")
                }
            },
        )
    }

    /** Pause an in-flight download. */
    fun pause() {
        if (!paused.compareAndSet(false, true)) return
        XLog.d(TAG, "download: state=pause, model=${currentModel?.id}")
    }

    /** Resume a paused download. */
    fun resume() {
        if (!paused.compareAndSet(true, false)) return
        XLog.d(TAG, "download: state=resume, model=${currentModel?.id}")
    }

    /** Cancel the download. */
    fun cancel() {
        cancelled.set(true)
        currentModel = null
        XLog.d(TAG, "download: state=cancel")
    }

    /** Format speed as "X.X MB/s". */
    fun formatSpeed(bps: Long): String {
        val mb = bps / 1_000_000.0
        return String.format("%.1f MB/s", mb)
    }

    /** Format ETA as "M min S s". */
    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "—"
        val mins = seconds / 60
        val secs = seconds % 60
        return when {
            mins > 0 -> "${mins} min ${secs} s"
            else -> "${secs} s"
        }
    }

    /** Format downloaded/total as "1.2 GB / 2.6 GB". */
    fun formatProgress(downloaded: Long, total: Long): String {
        return "${formatBytes(downloaded)} / ${formatBytes(total)}"
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return if (gb >= 1.0) String.format("%.1f GB", gb)
        else String.format("%.0f MB", bytes / 1_000_000.0)
    }

    private fun percentOf(downloaded: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((downloaded * 100) / total).toInt()
    }
}
