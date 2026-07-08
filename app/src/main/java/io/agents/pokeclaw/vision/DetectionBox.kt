package io.agents.pokeclaw.vision

/**
 * A single YOLO detection / weak-label candidate box in normalized (0..1) cxcywh.
 * Class names match the gxe generic-GUI vocabulary so weak labels and generic-model
 * detections share one label space.
 */
data class DetectionBox(
    val cls: String,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val conf: Float = 1f,
    val source: String = SOURCE_WEAK, // weak (from UI XML) | model (YOLO inference) | manual
) {
    val left: Float get() = cx - w / 2f
    val top: Float get() = cy - h / 2f
    fun area(): Float = w * h

    companion object {
        const val SOURCE_WEAK = "weak"
        const val SOURCE_MODEL = "model"
        const val SOURCE_MANUAL = "manual"
    }
}
