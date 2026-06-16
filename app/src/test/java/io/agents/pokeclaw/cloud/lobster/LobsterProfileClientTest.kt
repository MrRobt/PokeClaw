// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-041 LobsterProfileClient 9 用例

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterProfileApi
import io.agents.pokeclaw.cloud.lobster.client.LobsterProfileClient
import io.agents.pokeclaw.cloud.lobster.model.ClawAppExecutionRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterStatsRespVO
import io.agents.pokeclaw.cloud.lobster.model.Suggestion
import io.agents.pokeclaw.cloud.lobster.model.SuggestionResult
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * US-D-041 LobsterProfileClient 9 用例
 */
class LobsterProfileClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Fake API ─────────────────────────────────────────────────────────────────

    private class FakeLobsterProfileApi : LobsterProfileApi {
        data class Enqueued(val block: () -> Response<CommonResult>)

        private val myQueue = ArrayDeque<Enqueued>()
        private val statsQueue = ArrayDeque<Enqueued>()
        private val executionsQueue = ArrayDeque<Enqueued>()
        private val skillsQueue = ArrayDeque<Enqueued>()
        private val suggestionsQueue = ArrayDeque<Enqueued>()

        fun enqueueMy(block: () -> Response<CommonResult>) { myQueue.add(Enqueued(block)) }
        fun enqueueStats(block: () -> Response<CommonResult>) { statsQueue.add(Enqueued(block)) }
        fun enqueueExecutions(block: () -> Response<CommonResult>) { executionsQueue.add(Enqueued(block)) }
        fun enqueueSkills(block: () -> Response<CommonResult>) { skillsQueue.add(Enqueued(block)) }
        fun enqueueSuggestions(block: () -> Response<CommonResult>) { suggestionsQueue.add(Enqueued(block)) }

        override suspend fun getMy(): Response<CommonResult> {
            val e = myQueue.removeFirstOrNull() ?: error("no enqueued my response")
            return e.block()
        }
        override suspend fun getStats(): Response<CommonResult> {
            val e = statsQueue.removeFirstOrNull() ?: error("no enqueued stats response")
            return e.block()
        }
        override suspend fun getExecutions(skillId: String?, pageNo: Int, pageSize: Int): Response<CommonResult> {
            val e = executionsQueue.removeFirstOrNull() ?: error("no enqueued executions response")
            return e.block()
        }
        override suspend fun getMySkills(): Response<CommonResult> {
            val e = skillsQueue.removeFirstOrNull() ?: error("no enqueued skills response")
            return e.block()
        }
        override suspend fun getMySuggestions(): Response<CommonResult> {
            val e = suggestionsQueue.removeFirstOrNull() ?: error("no enqueued suggestions response")
            return e.block()
        }
    }

    // ── Test 1: my ok returns ClawLobsterRespVO ─────────────────────────────────

    @Test
    fun `my ok returns ClawLobsterRespVO`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = ClawLobsterRespVO(
                            id = "lobster-1",
                            nickname = "Muxi",
                            level = 5,
                            currentExp = 1200L,
                            nextLevelExp = 2000L,
                            avatar = "https://example.com/avatar.png",
                            createdAt = 1700000000000L,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getMy()
        assertNotNull(result)
        assertEquals("lobster-1", result!!.id)
        assertEquals("Muxi", result.nickname)
        assertEquals(5, result.level)
        assertEquals(1200L, result.currentExp)
        assertEquals(2000L, result.nextLevelExp)
    }

    // ── Test 2: stats ok returns ClawLobsterStatsRespVO ────────────────────────

    @Test
    fun `stats ok returns ClawLobsterStatsRespVO`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueStats {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = ClawLobsterStatsRespVO(
                            totalExecutions = 100L,
                            successRate = 0.85,
                            totalCreditConsumed = 5000L,
                            last7DaysExecutions = 20L,
                            last30DaysExecutions = 80L,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getStats()
        assertNotNull(result)
        assertEquals(100L, result!!.totalExecutions)
        assertEquals(0.85, result.successRate, 0.0001)
        assertEquals(5000L, result.totalCreditConsumed)
        assertEquals(20L, result.last7DaysExecutions)
        assertEquals(80L, result.last30DaysExecutions)
    }

    // ── Test 3: executions page 1 returns first page ────────────────────────────

    @Test
    fun `executions page 1 returns first page`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PageResult(
                            list = listOf(
                                ClawAppExecutionRespVO(
                                    executionId = "exec-1",
                                    skillId = "skill-a",
                                    skillName = "OpenWeChat",
                                    status = "SUCCESS",
                                    startedAt = 1700000000000L,
                                    completedAt = 1700000005000L,
                                    durationMs = 5000L,
                                    creditConsumed = 100L,
                                    resultSnippet = "OK",
                                ),
                            ),
                            total = 50,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getExecutions(pageNo = 1, pageSize = 20)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("exec-1", result[0].executionId)
        assertEquals("SUCCESS", result[0].status)
    }

    // ── Test 4: executions page 2 returns second page ────────────────────────────

    @Test
    fun `executions page 2 returns second page`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PageResult(
                            list = listOf(
                                ClawAppExecutionRespVO(
                                    executionId = "exec-21",
                                    skillId = "skill-a",
                                    skillName = "OpenWeChat",
                                    status = "SUCCESS",
                                    startedAt = 1700000010000L,
                                    completedAt = 1700000015000L,
                                    durationMs = 5000L,
                                    creditConsumed = 100L,
                                    resultSnippet = "OK",
                                ),
                            ),
                            total = 50,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getExecutions(pageNo = 2, pageSize = 20)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("exec-21", result[0].executionId)
    }

    // ── Test 5: executions with skillId filter flows skillId param ──────────────

    @Test
    fun `executions with skillId filter flows skillId param`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PageResult(
                            list = listOf(
                                ClawAppExecutionRespVO(
                                    executionId = "exec-skill-b",
                                    skillId = "skill-b",
                                    skillName = "OpenAlipay",
                                    status = "FAILED",
                                    startedAt = 1700000020000L,
                                    completedAt = 1700000023000L,
                                    durationMs = 3000L,
                                    creditConsumed = 50L,
                                    resultSnippet = "Error",
                                ),
                            ),
                            total = 1,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getExecutions(skillId = "skill-b", pageNo = 1, pageSize = 20)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("skill-b", result[0].skillId)
        assertEquals("OpenAlipay", result[0].skillName)
    }

    // ── Test 6: my skills 0 items returns empty list ────────────────────────────

    @Test
    fun `my skills 0 items returns empty list`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSkills {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = emptyList<ClawAppSkillRespVO>(),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getMySkills()
        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    // ── Test 7: my skills N items returns parsed list ────────────────────────────

    @Test
    fun `my skills N items returns parsed list`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSkills {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = listOf(
                            ClawAppSkillRespVO(
                                skillId = "skill-1",
                                skillName = "OpenWeChat",
                                version = "1.0.0",
                                installStatus = "INSTALLED",
                                lastUsedAt = 1700000000000L,
                            ),
                            ClawAppSkillRespVO(
                                skillId = "skill-2",
                                skillName = "OpenAlipay",
                                version = "2.0.0",
                                installStatus = "INSTALLED",
                                lastUsedAt = null,
                            ),
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getMySkills()
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("skill-1", result[0].skillId)
        assertEquals("OpenWeChat", result[0].skillName)
        assertEquals("1.0.0", result[0].version)
        assertEquals("INSTALLED", result[0].installStatus)
        assertEquals(1700000000000L, result[0].lastUsedAt)
        assertNull(result[1].lastUsedAt)
    }

    // ── Test 8: suggestions ok returns SuggestionResult ───────────────────────

    @Test
    fun `suggestions ok returns SuggestionResult`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSuggestions {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = SuggestionResult(
                            suggestions = listOf(
                                Suggestion(
                                    type = "skill",
                                    title = "Try OpenWeChat",
                                    description = "Learn how to use OpenWeChat skill",
                                    actionUrl = "https://example.com/skill/openwechat",
                                    priority = 10,
                                ),
                                Suggestion(
                                    type = "tip",
                                    title = "Welcome Tip",
                                    description = "Welcome to PokeClaw",
                                    actionUrl = null,
                                    priority = 5,
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val result = client.getMySuggestions()
        assertNotNull(result)
        assertEquals(2, result!!.suggestions.size)
        assertEquals("skill", result.suggestions[0].type)
        assertEquals("Try OpenWeChat", result.suggestions[0].title)
        assertEquals("Welcome Tip", result.suggestions[1].title)
        assertEquals(10, result.suggestions[0].priority)
    }

    // ── Test 9: network exception throws ──────────────────────────────────────

    @Test
    fun `network exception throws`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy { throw IOException("simulated network failure") }
        }
        val client = LobsterProfileClient(api)
        assertTrue(runCatching { client.getMy() }.isFailure)
    }

    // --- 扩展覆盖 ---

    @Test fun `getMy HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMy())
    }

    @Test fun `getMy HTTP 200 body null 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy { Response.success(null as CommonResult?) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMy())
    }

    @Test fun `getMy code=999 业务码错误 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy { Response.success(CommonResult(code = 999, msg = "rejected")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMy())
    }

    @Test fun `getMy code=200 同视为 success 返回 ClawLobsterRespVO`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueMy {
                Response.success(
                    CommonResult(
                        code = 200,
                        data = ClawLobsterRespVO(
                            id = "x",
                            nickname = "n",
                            level = 1,
                            currentExp = 0L,
                            nextLevelExp = 100L,
                            avatar = null,
                            createdAt = 0L,
                        ),
                    ),
                )
            }
        }
        val client = LobsterProfileClient(api)
        val r = client.getMy()
        assertNotNull(r)
        assertEquals("x", r!!.id)
    }

    @Test fun `getStats HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueStats { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getStats())
    }

    @Test fun `getExecutions HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getExecutions())
    }

    @Test fun `getExecutions HTTP 200 body null 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions { Response.success(null as CommonResult?) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getExecutions())
    }

    @Test fun `getExecutions code=999 业务码错误 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueExecutions { Response.success(CommonResult(code = 999, msg = "bad")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getExecutions())
    }

    @Test fun `getMySkills HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSkills { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMySkills())
    }

    @Test fun `getMySkills code=999 业务码错误 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSkills { Response.success(CommonResult(code = 999, msg = "rejected")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMySkills())
    }

    @Test fun `getMySuggestions HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSuggestions { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMySuggestions())
    }

    @Test fun `getMySuggestions code=999 业务码错误 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSuggestions { Response.success(CommonResult(code = 999, msg = "rejected")) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMySuggestions())
    }

    @Test fun `getMySuggestions HTTP 200 body null 返回 null`() = runBlocking {
        val api = FakeLobsterProfileApi().apply {
            enqueueSuggestions { Response.success(null as CommonResult?) }
        }
        val client = LobsterProfileClient(api)
        assertNull(client.getMySuggestions())
    }

    @Test fun `4 个其他方法在网络异常时均抛出`() = runBlocking {
        // 仅 getMy 在原 Test 9 验证；其他 4 个方法需各自冒泡
        val apiStats = FakeLobsterProfileApi().apply {
            enqueueStats { throw IOException("net down") }
        }
        assertTrue(runCatching { LobsterProfileClient(apiStats).getStats() }.isFailure)

        val apiExec = FakeLobsterProfileApi().apply {
            enqueueExecutions { throw IOException("net down") }
        }
        assertTrue(runCatching { LobsterProfileClient(apiExec).getExecutions() }.isFailure)

        val apiSkills = FakeLobsterProfileApi().apply {
            enqueueSkills { throw IOException("net down") }
        }
        assertTrue(runCatching { LobsterProfileClient(apiSkills).getMySkills() }.isFailure)

        val apiSugg = FakeLobsterProfileApi().apply {
            enqueueSuggestions { throw IOException("net down") }
        }
        assertTrue(runCatching { LobsterProfileClient(apiSugg).getMySuggestions() }.isFailure)
    }
}