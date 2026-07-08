package io.agents.pokeclaw.collect

import io.agents.pokeclaw.vision.DetectionBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectPayloadTest {

    private fun sample() = CollectedSample(
        sessionId = "sess-1", stepIndex = 3, softwareKey = "io.claw.app",
        pkg = "io.claw.app", activity = "Home", pageHash = "abcd", width = 1080, height = 2400,
        boxes = listOf(DetectionBox("interactable", 0.25f, 0.1f, 0.2f, 0.05f, 1f, "weak")),
        actionType = "tap", actionX = 270, actionY = 240,
        targetText = "Login", targetType = "button", actionResult = "changed",
        modelId = "mdl_1", modelVersion = 1,
        screenshotB64 = "QUJD", uiXml = "<hierarchy/>",
    )

    @Test
    fun payloadMatchesHubSchema() {
        val p = SamplePayloadBuilder.build(sample())
        assertEquals("sess-1", p["session_id"])
        assertEquals(3, p["step_index"])
        assertEquals("io.claw.app", p["package"])
        assertEquals("abcd", p["page_hash"])
        assertEquals(1080, p["width"])
        assertEquals("QUJD", p["screenshot_b64"])
        assertEquals("<hierarchy/>", p["ui_xml"])
        assertEquals("mdl_1", p["model_id"])

        @Suppress("UNCHECKED_CAST")
        val action = p["action"] as Map<String, Any?>
        assertEquals("tap", action["type"])
        assertEquals(270, action["x"])
        assertEquals("Login", action["target_text"])
        assertEquals("button", action["target_type"])
        assertEquals("changed", action["result"])

        @Suppress("UNCHECKED_CAST")
        val boxes = p["boxes"] as List<Map<String, Any?>>
        assertEquals(1, boxes.size)
        assertEquals("interactable", boxes[0]["cls"])
        assertEquals(0.25f, boxes[0]["cx"])
    }

    @Test
    fun trajectoryRecordsAndSerializes() {
        val rec = TrajectoryRecorder("sess-1")
        rec.record(TrajectoryStep(0, "io.claw.app", "Home", "h0", "tap", "Login", "changed", 5, "mdl_1", 1))
        rec.record(TrajectoryStep(1, "io.claw.app", "Settings", "h1", "back", null, "ok", 3, "mdl_1", 1))
        assertEquals(2, rec.size())

        val lines = rec.toJsonLines().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"step\":0"))
        assertTrue(lines[0].contains("\"action\":\"tap\""))
        assertTrue(lines[0].contains("\"activity\":\"Home\""))
        assertTrue(lines[1].contains("\"target\":null"))
    }
}
