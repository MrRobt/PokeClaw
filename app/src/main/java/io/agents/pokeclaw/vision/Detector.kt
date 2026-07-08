package io.agents.pokeclaw.vision

import java.io.File

/** Produces detection candidate boxes for a screen, tagged with the model identity. */
interface Detector {
    val modelId: String?
    val modelVersion: Int
    fun detect(uiXml: String?, screenW: Int, screenH: Int): List<DetectionBox>
}

/**
 * Weak-label detector: derives boxes from the UI control tree. Used as the generic
 * fallback and to bootstrap data collection before a trained model exists.
 */
class WeakLabelBackend(
    override val modelId: String? = null,
    override val modelVersion: Int = 0,
) : Detector {
    override fun detect(uiXml: String?, screenW: Int, screenH: Int): List<DetectionBox> =
        WeakLabelDetector.detect(uiXml, screenW, screenH)
}

/**
 * Real YOLO model backend. A production runtime (onnxruntime / LiteRT) would load
 * the cached artifact and run inference on the screenshot bitmap. Until that native
 * runtime is wired it delegates to weak labels so the pipeline stays runnable —
 * detections still carry this model's id/version for downstream evaluation.
 */
class YoloModelBackend(
    private val modelFile: File,
    override val modelId: String?,
    override val modelVersion: Int,
    private val classes: List<String>,
) : Detector {
    override fun detect(uiXml: String?, screenW: Int, screenH: Int): List<DetectionBox> =
        // TODO: wire onnx/LiteRT inference on the screenshot using modelFile ($modelFile, classes=$classes)
        WeakLabelDetector.detect(uiXml, screenW, screenH)
            .map { it.copy(source = DetectionBox.SOURCE_MODEL) }
}
