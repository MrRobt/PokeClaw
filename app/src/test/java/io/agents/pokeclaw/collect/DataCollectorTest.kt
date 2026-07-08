package io.agents.pokeclaw.collect

import io.agents.pokeclaw.device.DeviceObservation
import io.agents.pokeclaw.explore.ExploreAction
import io.agents.pokeclaw.vision.DetectionBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCollectorTest {

    @Test
    fun buildsSampleWithPageHashAndActionFields() {
        val obs = DeviceObservation(
            pkg = "io.claw.app", activity = "Home",
            uiXml = "[Button] text=\"Go\" id=\"io.claw.app:id/go\" pkg=\"io.claw.app\" [clickable] bounds=[10,20][210,120]",
            screenshotB64 = "QUJD", width = 1080, height = 2400,
        )
        val boxes = listOf(DetectionBox("interactable", 0.1f, 0.03f, 0.18f, 0.04f))
        val action = ExploreAction(type = "tap", x = 110, y = 70, targetText = "Go", targetType = "button")

        val s = DataCollector.build(
            sessionId = "sess", stepIndex = 2, softwareKey = "io.claw.app",
            obs = obs, boxes = boxes, action = action, actionResult = "changed",
            modelId = "mdl_1", modelVersion = 1,
        )

        assertEquals("io.claw.app", s.softwareKey)
        assertEquals(1, s.boxes.size)
        assertEquals("tap", s.actionType)
        assertEquals(110, s.actionX)
        assertEquals("Go", s.targetText)
        assertEquals("changed", s.actionResult)
        assertEquals("mdl_1", s.modelId)
        assertTrue("page hash computed", !s.pageHash.isNullOrBlank())
        assertEquals("QUJD", s.screenshotB64)

        // payload round-trips to the hub schema
        val p = SamplePayloadBuilder.build(s)
        assertEquals("io.claw.app", p["package"])
        assertEquals(s.pageHash, p["page_hash"])
    }
}
