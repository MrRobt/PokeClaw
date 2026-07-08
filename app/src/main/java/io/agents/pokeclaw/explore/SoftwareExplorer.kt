package io.agents.pokeclaw.explore

import io.agents.pokeclaw.collect.CollectedSample
import io.agents.pokeclaw.collect.DataCollector
import io.agents.pokeclaw.collect.SampleUploader
import io.agents.pokeclaw.collect.TrajectoryRecorder
import io.agents.pokeclaw.collect.TrajectoryStep
import io.agents.pokeclaw.device.DeviceActuator
import io.agents.pokeclaw.device.DeviceObservation
import io.agents.pokeclaw.device.DeviceObserver
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.vision.Detector
import io.agents.pokeclaw.vision.SoftwareIdentity
import io.agents.pokeclaw.vision.VisionConfig
import io.agents.pokeclaw.vision.WeakLabelBackend
import io.agents.pokeclaw.vision.WeakLabelDetector
import io.agents.pokeclaw.vision.YoloModelClient
import io.agents.pokeclaw.vision.YoloModelDescriptor
import java.util.UUID

/**
 * Top-level auto-exploration + YOLO-data-collection driver for one target software.
 *
 * 1. observe the foreground software and resolve its YOLO model (active/category/generic).
 * 2. explore with [ExplorationEngine] (state-hash dedup, unexplored queue, coverage).
 * 3. for every NEW page, collect a labeled sample (screenshot + UI XML + detection
 *    boxes + action + page hash + model id/version) and upload it by software_key.
 * 4. record the full observation/action/result trajectory.
 *
 * Runs on a background thread (blocking). Call [cancel] to stop gracefully.
 */
class SoftwareExplorer(
    private val observer: DeviceObserver,
    private val actuator: DeviceActuator,
    private val yolo: YoloModelClient,
    private val uploader: SampleUploader?,
    private val settleMs: Long = 800,
) {
    interface Listener {
        fun onModel(descriptor: YoloModelDescriptor) {}
        fun onStateCollected(sample: CollectedSample, isNew: Boolean) {}
        fun onStep(action: ExploreAction, result: String) {}
        fun onFinished(report: CoverageReport, trajectory: TrajectoryRecorder) {}
    }

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    fun explore(config: ExplorerConfig, listener: Listener? = null): CoverageReport {
        val sessionId = "explore_${UUID.randomUUID().toString().take(12)}"
        val trajectory = TrajectoryRecorder(sessionId)

        // (1) identify software + resolve model
        val first = observer.observe(captureScreenshot = false)
        val identity = SoftwareIdentity(
            packageName = config.targetPackage ?: first.pkg,
            activity = first.activity,
            taskGoal = config.targetPage,
        )
        val resolved = yolo.ensureModel(identity, taskRunning = false)
        val detector: Detector = resolved.detector
        listener?.onModel(resolved.descriptor)
        val softwareKey = identity.softwareKey() ?: resolved.descriptor.softwareKey ?: (first.pkg ?: "unknown")
        val modelId = resolved.descriptor.modelId
        val modelVersion = resolved.descriptor.version
        XLog.i(TAG, "explore start sw=$softwareKey model=$modelId v$modelVersion")

        var lastObs = DeviceObservation.EMPTY
        var stepCounter = 0

        lateinit var engine: ExplorationEngine
        engine = ExplorationEngine(
            config = config,
            observe = {
                val obs = if (cancelled) DeviceObservation.EMPTY else observer.observe(captureScreenshot = true)
                lastObs = obs
                UiState(obs.pkg, obs.activity, WeakLabelDetector.parseNodes(obs.uiXml), obs.width, obs.height)
            },
            act = { a ->
                val r = actuator.perform(a)
                if (settleMs > 0) runCatching { Thread.sleep(settleMs) }
                trajectory.record(
                    TrajectoryStep(
                        trajectory.size(), lastObs.pkg, lastObs.activity, null,
                        a.type, a.targetText, r, 0, modelId, modelVersion,
                    )
                )
                listener?.onStep(a, r)
                r
            },
            onState = { _, isNew ->
                if (isNew && !cancelled) {
                    val boxes = detector.detect(lastObs.uiXml, lastObs.width, lastObs.height)
                    val sample = DataCollector.build(
                        sessionId = sessionId, stepIndex = stepCounter++, softwareKey = softwareKey,
                        obs = lastObs, boxes = boxes, action = null, actionResult = null,
                        modelId = modelId, modelVersion = modelVersion,
                        includeScreenshot = VisionConfig.uploadScreenshots(),
                    )
                    engine.markCollected()
                    listener?.onStateCollected(sample, true)
                    if (VisionConfig.collectionEnabled()) uploader?.upload(sample)
                }
            },
        )

        val report = engine.run()
        listener?.onFinished(report, trajectory)
        XLog.i(TAG, "explore done: ${report.summary()} trajectory=${trajectory.size()}")
        return report
    }

    companion object {
        private const val TAG = "PokeClaw/SoftwareExplorer"
    }
}
