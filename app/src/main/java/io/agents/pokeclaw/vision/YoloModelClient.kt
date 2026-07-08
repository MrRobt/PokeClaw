package io.agents.pokeclaw.vision

import android.content.Context
import io.agents.pokeclaw.cloud.modelhub.ModelHubClient
import io.agents.pokeclaw.cloud.modelhub.model.ResolveRequestDto
import io.agents.pokeclaw.utils.XLog
import java.io.File

/**
 * Resolves, caches, and version-updates the per-software YOLO model, then hands
 * back a [Detector] bound to the active model. Enforces "don't switch mid-task":
 * an update that arrives while a task runs is deferred to the next task.
 */
class YoloModelClient(
    private val hub: ModelHubClient,
    private val cache: ModelCache,
) {
    constructor(context: Context, hub: ModelHubClient) :
        this(hub, ModelCache(File(context.filesDir, "yolo_models")))

    data class Resolved(
        val descriptor: YoloModelDescriptor,
        val detector: Detector,
        val modelFile: File?,
        val updated: Boolean,
    )

    fun ensureModel(identity: SoftwareIdentity, taskRunning: Boolean = false): Resolved {
        val key = identity.softwareKey()
        val cachedVersion = key?.let { cache.cachedVersion(it) } ?: 0
        val req = ResolveRequestDto(
            softwareKey = identity.softwareKey(),
            packageName = identity.packageName,
            activity = identity.activity,
            windowTitle = identity.windowTitle,
            taskGoal = identity.taskGoal,
            category = identity.category,
            currentVersion = if (cachedVersion > 0) cachedVersion else null,
        )
        val desc = hub.resolve(req)
        if (desc == null) {
            XLog.w(TAG, "resolve failed for ${identity.packageName}; weak-label fallback")
            return Resolved(offlineDescriptor(identity), WeakLabelBackend(), null, false)
        }
        XLog.i(TAG, "resolved source=${desc.source} model=${desc.modelId} v${desc.version} needsData=${desc.needsData}")

        val storeKey = desc.softwareKey ?: key
        var modelFile: File? =
            if (storeKey != null && desc.usable) cache.modelFile(storeKey, desc.version).takeIf { it.isFile } else null
        var updated = false

        if (storeKey != null && desc.usable) {
            val decision = ModelUpdatePolicy.decide(
                cache.cachedVersion(storeKey), cache.cachedChecksum(storeKey), desc, taskRunning,
            )
            if (decision.shouldDownload && desc.downloadUrl != null) {
                XLog.i(TAG, "downloading model: ${decision.reason}")
                val dest = cache.modelFile(storeKey, desc.version)
                val bytes = hub.download(desc.downloadUrl, dest)
                if (bytes != null) {
                    try {
                        cache.store(storeKey, desc.version, desc.modelId, desc.checksum, bytes)
                        modelFile = dest
                        updated = true
                    } catch (e: Exception) {
                        XLog.e(TAG, "cache store failed (checksum mismatch?)", e)
                    }
                }
            } else {
                XLog.d(TAG, "no download: ${decision.reason}")
            }
        }

        val detector: Detector =
            if (modelFile != null && desc.usable && !desc.isGenericFallback) {
                YoloModelBackend(modelFile, desc.modelId, desc.version, desc.classes)
            } else {
                WeakLabelBackend(desc.modelId, desc.version)
            }
        return Resolved(desc, detector, modelFile, updated)
    }

    private fun offlineDescriptor(identity: SoftwareIdentity) = YoloModelDescriptor(
        softwareKey = identity.softwareKey(), packageName = identity.packageName, modelId = null,
        version = 0, status = "none", source = "none", sourceKind = "generic",
        classes = emptyList(), defaultConfidence = 0.3f, confidenceThresholds = emptyMap(),
        downloadUrl = null, checksum = null, sizeBytes = 0, format = "none",
        needsData = true, updateAvailable = false, candidate = null,
    )

    companion object {
        private const val TAG = "PokeClaw/YoloModelClient"
    }
}
