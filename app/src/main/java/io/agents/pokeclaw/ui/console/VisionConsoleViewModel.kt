package io.agents.pokeclaw.ui.console

import android.content.Context
import io.agents.pokeclaw.cloud.cloudphone.CloudPhoneClient
import io.agents.pokeclaw.cloud.modelhub.ModelHubClient
import io.agents.pokeclaw.cloud.modelhub.model.ResolveRequestDto
import io.agents.pokeclaw.collect.CollectedSample
import io.agents.pokeclaw.collect.SampleUploader
import io.agents.pokeclaw.collect.TrajectoryRecorder
import io.agents.pokeclaw.device.AccessibilityActuator
import io.agents.pokeclaw.device.AccessibilityObserver
import io.agents.pokeclaw.explore.CoverageReport
import io.agents.pokeclaw.explore.ExploreAction
import io.agents.pokeclaw.explore.ExplorerConfig
import io.agents.pokeclaw.explore.SoftwareExplorer
import io.agents.pokeclaw.vision.VisionConfig
import io.agents.pokeclaw.vision.YoloModelClient
import io.agents.pokeclaw.vision.YoloModelDescriptor
import java.util.concurrent.Executors

/**
 * Backing controller for [VisionConsoleActivity]. Owns the model-hub / cloud-phone
 * clients and runs every network / exploration action on a background thread,
 * emitting log lines back to the UI. Plain class (no AndroidX ViewModel dep) —
 * matches the programmatic-view console style.
 */
class VisionConsoleViewModel(context: Context) {

    private val appContext = context.applicationContext
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile var selectedInstanceId: Long? = null
    @Volatile var lastCandidateModelId: String? = null
    @Volatile var lastTrajectory: String = "(no trajectory yet)"
    @Volatile private var explorer: SoftwareExplorer? = null

    private fun hubOrNull(): ModelHubClient? {
        val url = VisionConfig.modelHubBaseUrl()
        return if (url.isBlank()) null
        else runCatching { ModelHubClient(url, VisionConfig.modelHubToken().ifBlank { null }) }.getOrNull()
    }

    private fun cloudPhoneOrNull(): CloudPhoneClient? {
        val url = VisionConfig.cloudPhoneBaseUrl()
        return if (url.isBlank()) null
        else runCatching {
            CloudPhoneClient(url, VisionConfig.cloudPhoneToken().ifBlank { null }, VisionConfig.cloudPhoneTenant().ifBlank { null })
        }.getOrNull()
    }

    private fun bg(block: () -> String, cb: (String) -> Unit) {
        exec.submit {
            val out = runCatching { block() }.getOrElse { "error: ${it.message}" }
            cb(out)
        }
    }

    fun setHubUrl(url: String) {
        if (url.isNotBlank()) VisionConfig.setModelHubBaseUrl(url)
    }

    // ---- cloud phone instances --------------------------------------------
    fun loadInstances(cb: (String) -> Unit) = bg({
        val cp = cloudPhoneOrNull() ?: return@bg "cloud-phone base URL not configured"
        val list = cp.listInstances()
        if (list.isEmpty()) "no instances (or backend unreachable)"
        else "instances:\n" + list.joinToString("\n") { "  #${it.id} ${it.label()} status=${it.instanceStatus} power=${it.powerStatus}" }
    }, cb)

    fun selectInstance(id: Long, cb: (String) -> Unit) = bg({
        selectedInstanceId = id
        val conn = cloudPhoneOrNull()?.getConnection(id)
        "selected instance #$id" + (conn?.let { "\n  proto=${it.connectionProtocol} url=${it.connectionUrl}" } ?: "")
    }, cb)

    // ---- model routing ----------------------------------------------------
    fun resolveModel(pkg: String, cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub base URL not configured"
        val d = hub.resolve(ResolveRequestDto(packageName = pkg, softwareKey = pkg)) ?: return@bg "resolve failed"
        describe(d)
    }, cb)

    private fun describe(d: YoloModelDescriptor): String = buildString {
        append("model: source=${d.source} kind=${d.sourceKind}\n")
        append("  id=${d.modelId} v=${d.version} status=${d.status} fmt=${d.format} size=${d.sizeBytes}\n")
        append("  classes=${d.classes}\n")
        append("  needsData=${d.needsData} updateAvailable=${d.updateAvailable}\n")
        d.candidate?.let { append("  candidate(shadow)=${it.modelId} v${it.version}\n") }
    }

    // ---- registry / datasets ----------------------------------------------
    fun listSoftware(cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub not configured"
        val sw = hub.listSoftware()?.software ?: return@bg "no software"
        "software (per-app model library):\n" + sw.joinToString("\n") {
            "  ${it.softwareKey} [${it.category}] models=${it.modelCount} samples=${it.datasetSampleCount} needsData=${it.needsData}"
        }
    }, cb)

    fun listDatasets(cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub not configured"
        val ds = hub.listDatasets()?.datasets ?: return@bg "no datasets"
        "datasets (by software_key):\n" + ds.joinToString("\n") {
            "  ${it.softwareKey}: samples=${it.sampleCount} classes=${it.numClasses}"
        }
    }, cb)

    // ---- training / publish / rollback ------------------------------------
    fun triggerTraining(sw: String, cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub not configured"
        val job = hub.triggerTraining(sw, "console") ?: return@bg "trigger failed"
        lastCandidateModelId = job.candidateModelId
        "training ${job.status}: candidate=${job.candidateModelId} v${job.candidateVersion} metrics=${job.metrics}"
    }, cb)

    fun promoteCandidate(force: Boolean, cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub not configured"
        val mid = lastCandidateModelId ?: return@bg "no candidate; run training first"
        val r = hub.promote(mid, force) ?: return@bg "promote failed"
        "promote $mid: promoted=${r.promoted} gatePassed=${r.gatePassed} forced=${r.forced} reasons=${r.reasons}"
    }, cb)

    fun rollback(sw: String, cb: (String) -> Unit) = bg({
        val hub = hubOrNull() ?: return@bg "model-hub not configured"
        val r = hub.rollback(sw) ?: return@bg "rollback failed / nothing to roll back"
        "rollback $sw: $r"
    }, cb)

    // ---- auto exploration + collection ------------------------------------
    fun startExplore(pkg: String?, steps: Int, onLine: (String) -> Unit) {
        if (explorer != null) { onLine("explore already running"); return }
        val hub = hubOrNull()
        val uploader = hub?.let { SampleUploader(it) }
        val yolo = YoloModelClient(appContext, hub ?: ModelHubClient(VisionConfig.modelHubBaseUrl().ifBlank { "http://127.0.0.1:8077" }))
        val explorerLocal = SoftwareExplorer(AccessibilityObserver(), AccessibilityActuator(), yolo, uploader)
        explorer = explorerLocal
        val config = ExplorerConfig(maxSteps = steps, targetPackage = pkg?.ifBlank { null })
        exec.submit {
            try {
                explorerLocal.explore(config, object : SoftwareExplorer.Listener {
                    override fun onModel(descriptor: YoloModelDescriptor) = onLine(describe(descriptor))
                    override fun onStateCollected(sample: CollectedSample, isNew: Boolean) =
                        onLine("collected [${sample.stepIndex}] ${sample.activity ?: sample.pkg} boxes=${sample.boxes.size} hash=${sample.pageHash?.take(8)}")
                    override fun onStep(action: ExploreAction, result: String) =
                        onLine("step ${action.type} ${action.targetText ?: ""} -> $result")
                    override fun onFinished(report: CoverageReport, trajectory: TrajectoryRecorder) {
                        lastTrajectory = trajectory.toJsonLines()
                        onLine("DONE: ${report.summary()}")
                    }
                })
            } catch (e: Exception) {
                onLine("explore error: ${e.message}")
            } finally {
                explorer = null
            }
        }
    }

    fun stopExplore(cb: (String) -> Unit) {
        explorer?.cancel()
        cb(if (explorer != null) "stopping exploration…" else "no exploration running")
    }

    fun trajectory(): String = lastTrajectory
}
