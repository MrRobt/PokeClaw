// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 SkillMarketplaceClient 10 用例

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterSkillMarketplaceApi
import io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient
import io.agents.pokeclaw.cloud.lobster.model.BatchSkillStatusReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillSaveReqVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * US-D-039 SkillMarketplaceClient 10 用例
 */
class SkillMarketplaceClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Fake API ─────────────────────────────────────────────────────────────────

    private class FakeSkillMarketplaceApi : LobsterSkillMarketplaceApi {
        data class Enqueued(val block: () -> Response<CommonResult>)

        private val listQueue = ArrayDeque<Enqueued>()
        private val installQueue = ArrayDeque<Enqueued>()
        private val saveQueue = ArrayDeque<Enqueued>()
        private val removeQueue = ArrayDeque<Enqueued>()
        private val batchQueue = ArrayDeque<Enqueued>()

        fun enqueueList(block: () -> Response<CommonResult>) { listQueue.add(Enqueued(block)) }
        fun enqueueInstall(block: () -> Response<CommonResult>) { installQueue.add(Enqueued(block)) }
        fun enqueueSave(block: () -> Response<CommonResult>) { saveQueue.add(Enqueued(block)) }
        fun enqueueRemove(block: () -> Response<CommonResult>) { removeQueue.add(Enqueued(block)) }
        fun enqueueBatch(block: () -> Response<CommonResult>) { batchQueue.add(Enqueued(block)) }

        override suspend fun listSkills(): Response<CommonResult> {
            val e = listQueue.removeFirstOrNull() ?: error("no enqueued list response")
            return e.block()
        }

        override suspend fun installSkill(skillId: Map<String, String>): Response<CommonResult> {
            val e = installQueue.removeFirstOrNull() ?: error("no enqueued install response")
            return e.block()
        }

        override suspend fun saveSkill(req: ClawAppSkillSaveReqVO): Response<CommonResult> {
            val e = saveQueue.removeFirstOrNull() ?: error("no enqueued save response")
            return e.block()
        }

        override suspend fun removeSkill(id: String): Response<CommonResult> {
            val e = removeQueue.removeFirstOrNull() ?: error("no enqueued remove response")
            return e.block()
        }

        override suspend fun batchUpdateStatus(req: BatchSkillStatusReqVO): Response<CommonResult> {
            val e = batchQueue.removeFirstOrNull() ?: error("no enqueued batch response")
            return e.block()
        }
    }

    // ── Test 1: list 5 items returns parsed list ────────────────────────────────

    @Test
    fun `list 5 items returns parsed list`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueList {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = listOf(
                            ClawAppSkillMarketRespVO(skillId = "s1", skillName = "WeChat", description = "desc1", vendor = "vc1", installStatus = "INSTALLED", version = "1.0", channelCode = "wechat"),
                            ClawAppSkillMarketRespVO(skillId = "s2", skillName = "Telegram", description = "desc2", vendor = "vc2", installStatus = "AVAILABLE", version = "2.0", channelCode = "tg"),
                            ClawAppSkillMarketRespVO(skillId = "s3", skillName = "WhatsApp", description = "desc3", vendor = "vc3", installStatus = "INSTALLED", version = "1.5", channelCode = "wa"),
                            ClawAppSkillMarketRespVO(skillId = "s4", skillName = "Email", description = "desc4", vendor = "vc4", installStatus = "AVAILABLE", version = "1.0", channelCode = "email"),
                            ClawAppSkillMarketRespVO(skillId = "s5", skillName = "Browser", description = "desc5", vendor = "vc5", installStatus = "INSTALLED", version = "3.0", channelCode = "browser"),
                        ),
                    ),
                )
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.listSkills()
        assertTrue(result is SkillMarketplaceClient.Result.OkList)
        val ok = result as SkillMarketplaceClient.Result.OkList
        assertEquals(5, ok.skills.size)
        assertEquals("WeChat", ok.skills[0].skillName)
        assertEquals("Telegram", ok.skills[1].skillName)
    }

    // ── Test 2: list 0 items returns empty list ─────────────────────────────────

    @Test
    fun `list 0 items returns empty list`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueList {
                Response.success(CommonResult(code = 0, data = emptyList<Any>()))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.listSkills()
        assertTrue(result is SkillMarketplaceClient.Result.OkList)
        val ok = result as SkillMarketplaceClient.Result.OkList
        assertEquals(0, ok.skills.size)
    }

    // ── Test 3: install ok returns true ─────────────────────────────────────────

    @Test
    fun `install ok returns true`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueInstall {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.installSkill("skill-1")
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(true, ok.value)
    }

    // ── Test 4: install fail (false) returns false ──────────────────────────────

    @Test
    fun `install fail (false) returns false`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueInstall {
                Response.success(CommonResult(code = 0, data = false))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.installSkill("skill-2")
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(false, ok.value)
    }

    // ── Test 5: save new returns String id ─────────────────────────────────────

    @Test
    fun `save new returns String id`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueSave {
                Response.success(CommonResult(code = 0, data = "newly-created-skill-id"))
            }
        }
        val client = SkillMarketplaceClient(api)
        val req = ClawAppSkillSaveReqVO(
            skillName = "My New Skill",
            vendor = "PokeClaw",
            channelCode = "custom"
        )
        val result = client.saveSkill(req)
        assertTrue(result is SkillMarketplaceClient.Result.OkStringId)
        val ok = result as SkillMarketplaceClient.Result.OkStringId
        assertEquals("newly-created-skill-id", ok.id)
    }

    // ── Test 6: save update returns Boolean ────────────────────────────────────

    @Test
    fun `save update returns Boolean`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueSave {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = SkillMarketplaceClient(api)
        val req = ClawAppSkillSaveReqVO(
            id = "existing-skill-id",
            skillName = "Updated Skill",
            vendor = "PokeClaw",
            channelCode = "custom"
        )
        val result = client.saveSkill(req)
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(true, ok.value)
    }

    // ── Test 7: remove ok returns true ─────────────────────────────────────────

    @Test
    fun `remove ok returns true`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueRemove {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.removeSkill("skill-to-delete")
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(true, ok.value)
    }

    // ── Test 8: remove fail returns false ──────────────────────────────────────

    @Test
    fun `remove fail returns false`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueRemove {
                Response.success(CommonResult(code = 0, data = false))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.removeSkill("nonexistent-skill")
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(false, ok.value)
    }

    // ── Test 9: batch-status ok returns true ───────────────────────────────────

    @Test
    fun `batch-status ok returns true`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueBatch {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = SkillMarketplaceClient(api)
        val result = client.batchUpdateStatus(listOf("skill-1", "skill-2", "skill-3"), enable = true)
        assertTrue(result is SkillMarketplaceClient.Result.OkBoolean)
        val ok = result as SkillMarketplaceClient.Result.OkBoolean
        assertEquals(true, ok.value)
    }

    // ── Test 10: network exception throws ──────────────────────────────────────

    @Test
    fun `network exception throws`() = runBlocking {
        val api = FakeSkillMarketplaceApi().apply {
            enqueueList { throw IOException("simulated network failure") }
        }
        val client = SkillMarketplaceClient(api)
        assertTrue(runCatching { client.listSkills() }.isFailure)
    }
}