package io.agents.pokeclaw.collect

/**
 * Builds the JSON-serializable body for
 * `POST /api/v1/datasets/{software_key}/samples`, matching the hub `SampleIn`
 * schema exactly. Returns a map so it is pure and directly assertable in tests;
 * the uploader serializes it with Gson.
 */
object SamplePayloadBuilder {
    fun build(s: CollectedSample): LinkedHashMap<String, Any?> {
        val action = linkedMapOf<String, Any?>(
            "type" to (s.actionType ?: "none"),
            "x" to s.actionX,
            "y" to s.actionY,
            "x2" to s.actionX2,
            "y2" to s.actionY2,
            "target_text" to s.targetText,
            "target_type" to s.targetType,
            "text" to s.inputText,
            "result" to s.actionResult,
        )
        val boxes = s.boxes.map {
            linkedMapOf<String, Any?>(
                "cls" to it.cls,
                "cx" to it.cx, "cy" to it.cy, "w" to it.w, "h" to it.h,
                "conf" to it.conf, "source" to it.source,
            )
        }
        return linkedMapOf(
            "session_id" to s.sessionId,
            "step_index" to s.stepIndex,
            "package" to s.pkg,
            "activity" to s.activity,
            "page_hash" to s.pageHash,
            "width" to s.width,
            "height" to s.height,
            "boxes" to boxes,
            "action" to action,
            "model_id" to s.modelId,
            "model_version" to s.modelVersion,
            "screenshot_b64" to s.screenshotB64,
            "ui_xml" to s.uiXml,
        )
    }
}
