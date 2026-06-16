// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 LobsterCommandApi MockWebServer 契约测试

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterCommandApi
import io.agents.pokeclaw.cloud.lobster.model.CommandDetailResult
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteReq
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteResp
import io.agents.pokeclaw.cloud.lobster.model.HermesFeedbackReq
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

class LobsterCommandApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LobsterCommandApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LobsterCommandApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `executeCommand 200 returns executionId`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"executionId":"exec-1","status":"PENDING"}}"""
            )
        )
        val resp = api.executeCommand(CommandExecuteReq(command = "打开微信"))
        assertEquals(200, resp.code())
        // CommonResult.data is Any? (auto-generated non-parameterized), so Gson parses as Map
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("exec-1", data?.get("executionId"))
        assertEquals("PENDING", data?.get("status"))
    }

    @Test fun `getCommandResult SUCCESS returns success status`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"SUCCESS","result":"ok"}}"""
            )
        )
        val resp = api.getCommandResult("exec-1")
        assertEquals(200, resp.code())
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("SUCCESS", data?.get("status"))
        assertEquals("ok", data?.get("result"))
    }

    @Test fun `submitHermesFeedback 200 returns success`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"msg":"ok"}""")
        )
        val resp = api.submitHermesFeedback(
            HermesFeedbackReq(feedbackType = "task_complete", taskUuid = "task-1")
        )
        assertEquals(200, resp.code())
        assertEquals(0, resp.body()?.code)
    }

    // --- HTTP error code handling ---

    @Test fun `executeCommand 500 returns 500 code and null parsed body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
        val resp = api.executeCommand(CommandExecuteReq(command = "boom"))
        assertEquals(500, resp.code())
        assertNotNull("errorBody() for 500 carries the raw error payload", resp.errorBody())
        assertNull("body() for 500 should be null", resp.body())
    }

    @Test fun `executeCommand 400 returns 400 code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        val resp = api.executeCommand(CommandExecuteReq(command = ""))
        assertEquals(400, resp.code())
        assertNull(resp.body())
    }

    @Test fun `submitHermesFeedback 502 returns 502`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val resp = api.submitHermesFeedback(HermesFeedbackReq(feedbackType = "error"))
        assertEquals(502, resp.code())
        assertNull(resp.body())
    }

    // --- response body shape variants ---

    @Test fun `getCommandResult FAILED status carries errorMessage`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"FAILED","errorMessage":"无障碍服务未启用"}}"""
            )
        )
        val resp = api.getCommandResult("exec-fail-1")
        assertEquals(200, resp.code())
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("FAILED", data?.get("status"))
        assertEquals("无障碍服务未启用", data?.get("errorMessage"))
    }

    @Test fun `getCommandResult CANCELLED status is preserved`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"CANCELLED"}}"""
            )
        )
        val resp = api.getCommandResult("exec-cancel-1")
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("CANCELLED", data?.get("status"))
        // No result / errorMessage on cancelled
        assertNull(data?.get("result"))
        assertNull(data?.get("errorMessage"))
    }

    @Test fun `getCommandResult PENDING status with no result field parses cleanly`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"PENDING"}}"""
            )
        )
        val resp = api.getCommandResult("exec-pending-1")
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("PENDING", data?.get("status"))
        assertNull(data?.get("result"))
    }

    @Test fun `submitHermesFeedback body with code not 0 is reported`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":1001,"msg":"unknown feedback type","data":null}""")
        )
        val resp = api.submitHermesFeedback(HermesFeedbackReq(feedbackType = "unknown"))
        assertEquals(200, resp.code())
        assertEquals(1001, resp.body()?.code)
        assertEquals("unknown feedback type", resp.body()?.msg)
    }

    // --- request body / path serialization ---

    @Test fun `executeCommand POST sends command in JSON body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"executionId":"exec-2","status":"PENDING"}}"""
            )
        )
        api.executeCommand(CommandExecuteReq(command = "ping"))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/app-api/claw/app/lobster/command", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body must include command: $body", body.contains("\"command\""))
        assertTrue("body must include literal text: $body", body.contains("ping"))
    }

    @Test fun `executeCommand sends skillId and context when provided`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"executionId":"exec-3","status":"PENDING"}}"""
            )
        )
        api.executeCommand(
            CommandExecuteReq(
                command = "launch",
                skillId = "skill-42",
                context = mapOf("region" to "tw", "tier" to "p0"),
            )
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("body must include skillId: $body", body.contains("\"skillId\":\"skill-42\""))
        assertTrue("body must include region context: $body", body.contains("\"region\":\"tw\""))
        assertTrue("body must include tier context: $body", body.contains("\"tier\":\"p0\""))
    }

    @Test fun `executeCommand omits null skillId and context from body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"executionId":"exec-4","status":"PENDING"}}"""
            )
        )
        api.executeCommand(CommandExecuteReq(command = "ping"))
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        // Gson by default omits null fields; assert no "skillId" / "context" tokens.
        assertTrue("null skillId must be omitted: $body", !body.contains("skillId"))
        assertTrue("null context must be omitted: $body", !body.contains("context"))
    }

    @Test fun `getCommandResult GET path includes executionId`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"SUCCESS","result":"ok"}}"""
            )
        )
        api.getCommandResult("exec-with-dashes-1")
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals(
            "/app-api/claw/app/lobster/command/exec-with-dashes-1/result",
            recorded.path,
        )
    }

    @Test fun `submitHermesFeedback POST sends feedbackType and taskUuid`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"ok"}"""))
        api.submitHermesFeedback(
            HermesFeedbackReq(feedbackType = "task_complete", taskUuid = "task-xyz-9")
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/app-api/claw/hermes/feedback", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body must include feedbackType: $body", body.contains("\"feedbackType\":\"task_complete\""))
        assertTrue("body must include taskUuid: $body", body.contains("\"taskUuid\":\"task-xyz-9\""))
    }

    @Test fun `submitHermesFeedback body with payload serializes payload map`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"ok"}"""))
        api.submitHermesFeedback(
            HermesFeedbackReq(
                feedbackType = "task_complete",
                payload = mapOf("score" to 5, "comment" to "good"),
            )
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("body must include payload key score: $body", body.contains("score"))
        assertTrue("body must include payload key comment: $body", body.contains("comment"))
        assertTrue("body must include payload literal: $body", body.contains("\"payload\""))
    }

    // --- DTO shape sanity (model side) ---

    @Test fun `CommandExecuteReq defaults are null for optional fields`() {
        val req = CommandExecuteReq(command = "x")
        assertNull(req.skillId)
        assertNull(req.context)
        assertEquals("x", req.command)
    }

    @Test fun `CommandDetailResult optional fields default to null`() {
        val detail = CommandDetailResult(status = "RUNNING")
        assertEquals("RUNNING", detail.status)
        assertNull(detail.result)
        assertNull(detail.errorMessage)
        assertNull(detail.progressPercent)
    }

    @Test fun `HermesFeedbackReq defaults are null for optional fields`() {
        val req = HermesFeedbackReq(feedbackType = "x")
        assertNull(req.payload)
        assertNull(req.taskUuid)
    }

    @Test fun `executeCommand 200 body but no data field still parses as 200 with null data`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"msg":"no data"}"""))
        val resp = api.executeCommand(CommandExecuteReq(command = "x"))
        assertEquals(200, resp.code())
        assertNotNull(resp.body())
        assertNull("data should be null when absent", resp.body()?.data)
    }

    @Test fun `CommandExecuteResp field round-trip via direct construction`() {
        val resp = CommandExecuteResp(executionId = "exec-rt-1", status = "PENDING")
        assertEquals("exec-rt-1", resp.executionId)
        assertEquals("PENDING", resp.status)
    }
}
