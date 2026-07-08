package io.agents.pokeclaw.collect

import io.agents.pokeclaw.device.DeviceObservation
import io.agents.pokeclaw.explore.ExploreAction
import io.agents.pokeclaw.explore.StateHasher
import io.agents.pokeclaw.vision.DetectionBox
import io.agents.pokeclaw.vision.WeakLabelDetector

/**
 * Assembles a [CollectedSample] from an observation + detection boxes + the action
 * taken. Computes the page-state hash from the UI tree. Pure — unit-tested.
 */
object DataCollector {
    fun build(
        sessionId: String,
        stepIndex: Int,
        softwareKey: String,
        obs: DeviceObservation,
        boxes: List<DetectionBox>,
        action: ExploreAction?,
        actionResult: String?,
        modelId: String?,
        modelVersion: Int?,
        includeScreenshot: Boolean = true,
        includeXml: Boolean = true,
    ): CollectedSample {
        val nodes = WeakLabelDetector.parseNodes(obs.uiXml)
        val pageHash = StateHasher.hash(obs.pkg, obs.activity, nodes)
        return CollectedSample(
            sessionId = sessionId,
            stepIndex = stepIndex,
            softwareKey = softwareKey,
            pkg = obs.pkg,
            activity = obs.activity,
            pageHash = pageHash,
            width = obs.width,
            height = obs.height,
            boxes = boxes,
            actionType = action?.type,
            actionX = action?.x,
            actionY = action?.y,
            actionX2 = action?.x2,
            actionY2 = action?.y2,
            targetText = action?.targetText,
            targetType = action?.targetType,
            inputText = action?.inputText,
            actionResult = actionResult,
            modelId = modelId,
            modelVersion = modelVersion,
            screenshotB64 = if (includeScreenshot) obs.screenshotB64 else null,
            uiXml = if (includeXml) obs.uiXml else null,
        )
    }
}
