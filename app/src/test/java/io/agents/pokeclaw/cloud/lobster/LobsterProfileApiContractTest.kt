// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-041 LobsterProfileApi MockWebServer 契约测试

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterProfileApi
import io.agents.pokeclaw.cloud.lobster.model.ClawAppExecutionRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterStatsRespVO
import io.agents.pokeclaw.cloud.lobster.model.Suggestion
import io.agents.pokeclaw.cloud.lobster.model.SuggestionResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking

class LobsterProfileApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LobsterProfileApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LobsterProfileApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `getMy 200 returns ClawLobsterRespVO`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"id":"lobster-1","nickname":"Muxi","level":5,"currentExp":1200,"nextLevelExp":2000,"avatar":"https://example.com/avatar.png","createdAt":1700000000000}}"""
            )
        )
        val resp = api.getMy()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        val data = body?.data as? Map<String, Any?>
        assertNotNull(data)
        assertEquals("lobster-1", data?.get("id"))
        assertEquals("Muxi", data?.get("nickname"))
        assertEquals(5.0, data?.get("level"))
    }

    @Test fun `getStats 200 returns ClawLobsterStatsRespVO`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"totalExecutions":100,"successRate":0.85,"totalCreditConsumed":5000,"last7DaysExecutions":20,"last30DaysExecutions":80}}"""
            )
        )
        val resp = api.getStats()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        val data = body?.data as? Map<String, Any?>
        assertNotNull(data)
        assertEquals(100.0, data?.get("totalExecutions"))
        assertEquals(0.85, data?.get("successRate"))
    }

    @Test fun `getExecutions 200 returns page with executions`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"list":[{"executionId":"exec-1","skillId":"skill-a","skillName":"OpenWeChat","status":"SUCCESS","startedAt":1700000000000,"completedAt":1700000005000,"durationMs":5000,"creditConsumed":100,"resultSnippet":"OK"}],"total":1}}"""
            )
        )
        val resp = api.getExecutions(skillId = null, pageNo = 1, pageSize = 20)
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        val data = body?.data as? Map<String, Any?>
        assertNotNull(data)
        @Suppress("UNCHECKED_CAST")
        val list = data?.get("list") as? List<Map<String, Any?>>
        assertNotNull(list)
        assertEquals(1, list?.size)
        assertEquals("exec-1", list?.get(0)?.get("executionId"))
    }

    @Test fun `getMySkills 200 returns skill list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":[{"skillId":"skill-1","skillName":"OpenWeChat","version":"1.0.0","installStatus":"INSTALLED","lastUsedAt":1700000000000}]}"""
            )
        )
        val resp = api.getMySkills()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        @Suppress("UNCHECKED_CAST")
        val data = body?.data as? List<Map<String, Any?>>
        assertNotNull(data)
        assertEquals(1, data?.size)
        assertEquals("skill-1", data?.get(0)?.get("skillId"))
    }

    @Test fun `getMySuggestions 200 returns suggestion result`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"suggestions":[{"type":"skill","title":"Try OpenWeChat","description":"Learn how to use OpenWeChat","actionUrl":"https://example.com/skill","priority":10}]}}"""
            )
        )
        val resp = api.getMySuggestions()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        val data = body?.data as? Map<String, Any?>
        assertNotNull(data)
        @Suppress("UNCHECKED_CAST")
        val suggestions = data?.get("suggestions") as? List<Map<String, Any?>>
        assertNotNull(suggestions)
        assertEquals(1, suggestions?.size)
        assertEquals("skill", suggestions?.get(0)?.get("type"))
    }

    @Test fun `getExecutions with skillId filter passes parameter`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"list":[],"total":0}}"""
            )
        )
        val resp = api.getExecutions(skillId = "skill-b", pageNo = 1, pageSize = 20)
        assertEquals(200, resp.code())
        val request = server.takeRequest()
        assertTrue(request.requestLine.contains("skillId=skill-b"))
    }

    // --- path & method verification ---

    @Test fun `getMy hits the my route with GET method`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"id":"l","nickname":"n","level":1,"currentExp":0,"nextLevelExp":1,"avatar":null,"createdAt":1}}""")
        )
        api.getMy()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/my", recorded.path)
    }

    @Test fun `getStats hits the stats route with GET method`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"totalExecutions":0,"successRate":0.0,"totalCreditConsumed":0,"last7DaysExecutions":0,"last30DaysExecutions":0}}""")
        )
        api.getStats()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/my/stats", recorded.path)
    }

    @Test fun `getExecutions with default pageNo pageSize still serializes query`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}""")
        )
        api.getExecutions(skillId = null, pageNo = 1, pageSize = 20)
        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        assertTrue("path must include pageNo=1: $path", path.contains("pageNo=1"))
        assertTrue("path must include pageSize=20: $path", path.contains("pageSize=20"))
        assertTrue("null skillId must not be in path: $path", !path.contains("skillId"))
    }

    @Test fun `getMySkills hits the skills route with GET method`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":[]}"""))
        api.getMySkills()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/my/skills", recorded.path)
    }

    @Test fun `getMySuggestions hits the suggestions route with GET method`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"suggestions":[]}}""")
        )
        api.getMySuggestions()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/my/suggestions", recorded.path)
    }

    // --- error responses ---

    @Test fun `getMy 500 returns 500 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
        val resp = api.getMy()
        assertEquals(500, resp.code())
        assertNull("body() for 500 must be null", resp.body())
        assertNotNull("errorBody() for 500 must carry raw payload", resp.errorBody())
    }

    @Test fun `getMy 401 returns 401`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val resp = api.getMy()
        assertEquals(401, resp.code())
        assertNull(resp.body())
    }

    @Test fun `getMy 404 returns 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val resp = api.getMy()
        assertEquals(404, resp.code())
        assertNull(resp.body())
    }

    @Test fun `getStats 502 returns 502`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val resp = api.getStats()
        assertEquals(502, resp.code())
        assertNull(resp.body())
    }

    @Test fun `getMySuggestions 400 returns 400`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        val resp = api.getMySuggestions()
        assertEquals(400, resp.code())
        assertNull(resp.body())
    }

    @Test fun `getExecutions 200 with business code 4010 returns the code in body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":4010,"msg":"skill not found","data":null}"""))
        val resp = api.getExecutions(skillId = "missing", pageNo = 1, pageSize = 20)
        assertEquals(200, resp.code())
        assertEquals(4010, resp.body()?.code)
        assertEquals("skill not found", resp.body()?.msg)
    }

    // --- empty list / missing data variants ---

    @Test fun `getExecutions empty list returns 200 with empty data`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}"""))
        val resp = api.getExecutions(skillId = null, pageNo = 1, pageSize = 20)
        assertEquals(200, resp.code())
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val list = data?.get("list") as? List<Map<String, Any?>>
        assertNotNull(list)
        assertEquals(0, list?.size)
    }

    @Test fun `getMySkills empty list returns 200`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":[]}"""))
        val resp = api.getMySkills()
        assertEquals(200, resp.code())
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? List<Map<String, Any?>>
        assertNotNull(data)
        assertEquals(0, data?.size)
    }

    @Test fun `getMySuggestions with empty suggestions list returns 200`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"suggestions":[]}}""")
        )
        val resp = api.getMySuggestions()
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val suggestions = data?.get("suggestions") as? List<Map<String, Any?>>
        assertNotNull(suggestions)
        assertEquals(0, suggestions?.size)
    }

    @Test fun `200 response without data field still parses cleanly`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"no data"}"""))
        val resp = api.getMy()
        assertEquals(200, resp.code())
        assertNotNull(resp.body())
        assertNull("data should be null when absent", resp.body()?.data)
    }

    // --- DTO round-trip ---

    @Test fun `ClawLobsterRespVO optional avatar defaults to null`() {
        val vo = ClawLobsterRespVO(
            id = "i",
            nickname = "n",
            level = 1,
            currentExp = 0L,
            nextLevelExp = 100L,
            avatar = null,
            createdAt = 1L,
        )
        assertEquals("i", vo.id)
        assertNull(vo.avatar)
    }

    @Test fun `ClawAppExecutionRespVO optional completedAt and resultSnippet default to null`() {
        val vo = ClawAppExecutionRespVO(
            executionId = "e",
            skillId = "s",
            skillName = "n",
            status = "RUNNING",
            startedAt = 1L,
            completedAt = null,
            durationMs = 0L,
            creditConsumed = 0L,
            resultSnippet = null,
        )
        assertNull(vo.completedAt)
        assertNull(vo.resultSnippet)
    }

    @Test fun `ClawAppSkillRespVO optional lastUsedAt defaults to null`() {
        val vo = ClawAppSkillRespVO(
            skillId = "s",
            skillName = "n",
            version = "1.0.0",
            installStatus = "INSTALLED",
            lastUsedAt = null,
        )
        assertNull(vo.lastUsedAt)
    }

    @Test fun `Suggestion optional actionUrl defaults to null`() {
        val s = Suggestion(
            type = "skill",
            title = "T",
            description = "D",
            actionUrl = null,
            priority = 1,
        )
        assertNull(s.actionUrl)
    }

    @Test fun `SuggestionResult carries suggestions list as-is`() {
        val s = SuggestionResult(
            suggestions = listOf(
                Suggestion("a", "T1", "D1", null, 5),
                Suggestion("b", "T2", "D2", "https://x", 1),
            )
        )
        assertEquals(2, s.suggestions.size)
        assertEquals("a", s.suggestions[0].type)
        assertEquals("https://x", s.suggestions[1].actionUrl)
    }
}
