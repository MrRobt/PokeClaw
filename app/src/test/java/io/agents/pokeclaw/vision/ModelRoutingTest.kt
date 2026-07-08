package io.agents.pokeclaw.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoutingTest {

    private fun desc(
        source: String,
        version: Int = 1,
        status: String = "active",
        checksum: String? = "c$version",
        candidate: CandidateModel? = null,
    ) = YoloModelDescriptor(
        softwareKey = "io.claw.app", packageName = "io.claw.app", modelId = "mdl_$version",
        version = version, status = status, source = source,
        sourceKind = if (source == "generic_fallback") "generic" else "software",
        classes = listOf("interactable", "text"), defaultConfidence = 0.3f,
        confidenceThresholds = mapOf("interactable" to 0.5f), downloadUrl = "http://h/$version",
        checksum = checksum, sizeBytes = 100, format = "stub-v1",
        needsData = source != "software_active", updateAvailable = false, candidate = candidate,
    )

    @Test
    fun priorityIsSoftwareThenCategoryThenGeneric() {
        val sw = desc("software_active")
        val cat = desc("category")
        val gen = desc("generic_fallback")

        assertEquals(
            ModelRoutePolicy.Source.SOFTWARE_ACTIVE,
            ModelRoutePolicy.choose(ModelRoutePolicy.Available(sw, cat, gen)).source,
        )
        assertEquals(
            ModelRoutePolicy.Source.CATEGORY,
            ModelRoutePolicy.choose(ModelRoutePolicy.Available(null, cat, gen)).source,
        )
        val g = ModelRoutePolicy.choose(ModelRoutePolicy.Available(null, null, gen))
        assertEquals(ModelRoutePolicy.Source.GENERIC, g.source)
        assertTrue(g.needsData)
        assertEquals(ModelRoutePolicy.Source.NONE, ModelRoutePolicy.choose(ModelRoutePolicy.Available()).source)
    }

    @Test
    fun candidateNeverControlsOnlyShadows() {
        val cand = CandidateModel("cand_2", 2, "cc2", "http://h/cand", listOf("interactable"))
        val routed = desc("generic_fallback", candidate = cand)
        // controlling model is the active route, not the candidate
        assertEquals("mdl_1", ModelRoutePolicy.controllingModel(routed)!!.modelId)
        assertEquals("cand_2", ModelRoutePolicy.shadowCandidate(routed)!!.modelId)

        // a candidate-status descriptor may not control
        val candidateStatus = desc("software_active", status = "candidate")
        assertNull(ModelRoutePolicy.controllingModel(candidateStatus))
    }

    @Test
    fun thresholdsFallBackToDefault() {
        val d = desc("software_active")
        assertEquals(0.5f, d.thresholdFor("interactable"))
        assertEquals(0.3f, d.thresholdFor("text"))
    }

    @Test
    fun updatePolicyHonorsCacheAndMidTaskRule() {
        val v2 = desc("software_active", version = 2, checksum = "c2")

        assertTrue(ModelUpdatePolicy.decide(0, null, v2, taskRunning = false).shouldDownload)     // not cached
        assertFalse(ModelUpdatePolicy.decide(2, "c2", v2, taskRunning = false).shouldDownload)    // up to date
        assertTrue(ModelUpdatePolicy.decide(1, "c1", v2, taskRunning = false).shouldDownload)     // newer, idle
        // newer but a task is running -> defer to next task
        assertFalse(ModelUpdatePolicy.decide(1, "c1", v2, taskRunning = true).shouldDownload)
    }
}
