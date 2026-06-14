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
        val data = resp.body()?.data as? CommandExecuteResp
        assertEquals("exec-1", data?.executionId)
        assertEquals("PENDING", data?.status)
    }

    @Test fun `getCommandResult SUCCESS returns success status`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"status":"SUCCESS","result":"ok"}}"""
            )
        )
        val resp = api.getCommandResult("exec-1")
        assertEquals(200, resp.code())
        val data = resp.body()?.data as? CommandDetailResult
        assertEquals("SUCCESS", data?.status)
        assertEquals("ok", data?.result)
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
}