package io.agents.pokeclaw.cloudnode

import android.content.Context
import android.content.ContextWrapper
import io.agents.pokeclaw.agent.learning.TaskLearningManager
import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.ExperienceLocalCache
import io.agents.pokeclaw.cloud.ExperienceReader
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudTaskExecutorBridgeLearningTest {
    private val context: Context = object : ContextWrapper(null) {}

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        KVUtils.resetTestBacking()
        SkillRegistry.clear()
        SkillRegistry.loadBuiltInSkills()
    }

    @After
    fun tearDown() {
        SkillRegistry.clear()
        KVUtils.resetTestBacking()
    }

    @Test
    fun `successful cloud instruction updates skill stats and local experience`() {
        val before = SkillRegistry.getRuntimeStats("launch_app")
        val manager = TaskLearningManager(context) { 42L }
        val bridge = CloudTaskExecutorBridge(learningManager = manager)

        val result = bridge.execute(
            CloudExecutorTask(
                taskId = "cloud-task-1",
                deviceId = "device-1",
                instruction = "open settings",
                issuedAtMillis = 1L,
            )
        )

        assertTrue(result.success)
        val after = SkillRegistry.getRuntimeStats("launch_app")
        assertNotNull(after)
        assertEquals((before?.selectCount ?: 0) + 1, after!!.selectCount)
        assertEquals((before?.successCount ?: 0) + 1, after.successCount)

        val experiences = ExperienceLocalCache.load(context)
        assertEquals(1, experiences.size)
        assertEquals(ExperienceReader.Experience.Type.SUCCESS, experiences[0].type)
        assertEquals("cloud-task-1", experiences[0].commercialTaskId)
        assertTrue(experiences[0].summary.contains("open settings"))
    }

    @Test
    fun `unsupported cloud instruction writes a failure experience`() {
        val manager = TaskLearningManager(context) { 84L }
        val bridge = CloudTaskExecutorBridge(learningManager = manager)

        val result = bridge.execute(
            CloudExecutorTask(
                taskId = "cloud-task-unsupported",
                deviceId = "device-1",
                instruction = "do a complex multi app task with no deterministic mapping",
                issuedAtMillis = 1L,
            )
        )

        assertTrue(!result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
        val experiences = ExperienceLocalCache.load(context)
        assertEquals(1, experiences.size)
        assertEquals(ExperienceReader.Experience.Type.FAILURE, experiences[0].type)
        assertEquals("TASK_REJECTED", experiences[0].errorCode)
    }
}
