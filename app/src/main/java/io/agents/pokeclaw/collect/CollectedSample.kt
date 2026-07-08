package io.agents.pokeclaw.collect

import io.agents.pokeclaw.vision.DetectionBox

/**
 * One collected exploration step, holding everything the cloud needs to build a
 * per-software YOLO dataset sample: screenshot, UI XML, software/activity info,
 * detection candidate boxes, click coords, control text/type, action, page hash,
 * model id/version, and the collection session id.
 */
data class CollectedSample(
    val sessionId: String,
    val stepIndex: Int,
    val softwareKey: String,
    val pkg: String?,
    val activity: String?,
    val pageHash: String?,
    val width: Int,
    val height: Int,
    val boxes: List<DetectionBox>,
    val actionType: String?,
    val actionX: Int? = null,
    val actionY: Int? = null,
    val actionX2: Int? = null,
    val actionY2: Int? = null,
    val targetText: String? = null,
    val targetType: String? = null,
    val inputText: String? = null,
    val actionResult: String? = null,
    val modelId: String? = null,
    val modelVersion: Int? = null,
    val screenshotB64: String? = null,
    val uiXml: String? = null,
)
