// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 LobsterCommandClient 12 用例

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.client.LobsterCommandClient
import io.agents.pokeclaw.cloud.lobster.model.CommandDetailResult
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteReq
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteResp
import io.agents.pokeclaw.cloud.lobster.model.HermesFeedbackReq
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.util.PollingPolicy
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * US-D-038 LobsterCommandClient 12 用例
 */
class LobsterCommandClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Fake API ─────────────────────────────────────────────────────────────────

    private class FakeLobsterCommandApi : io.agents.pokeclaw.cloud.lobster.api.LobsterCommandApi {
        data class Enqueued(val block: () -> Response<CommonResult>)

        private val executeQueue = ArrayDeque<Enqueued>()
        private val getResultQueue = ArrayDeque<Enqueued>()
        private val feedbackQueue = ArrayDeque<Enqueued>()

        fun enqueueExecute(block: () -> Response<CommonResult>) { executeQueue.add(Enqueued(block)) }
        fun enqueueGetResult(block: () -> Response<CommonResult>) { getResultQueue.add(Enqueued(block)) }
        fun enqueueFeedback(block: () -> Response<CommonResult>) { feedbackQueue.add(Enqueued(block)) }

        override suspend fun executeCommand(req: CommandExecuteReq): Response<CommonResult> {
            val e = executeQueue.removeFirstOrNull() ?: error("no enqueued execute response")
            return e.block()
        }
        override suspend fun getCommandResult(executionId: String): Response<CommonResult> {
            val e = getResultQueue.removeFirstOrNull() ?: error("no enqueued getResult response for $executionId")
            return e.block()
        }
        override suspend fun submitHermesFeedback(req: HermesFeedbackReq): Response<CommonResult> {
            val e = feedbackQueue.removeFirstOrNull() ?: error("no enqueued feedback response")
            return e.block()
        }
    }

    // ── Test 1: submit returns Ok with executionId on 200 ─────────────────────

    @Test
    fun `submit returns Ok with executionId on 200`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueExecute {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = CommandExecuteResp(executionId = "exec-1", status = "PENDING"),
                    ),
                )
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.submit("open WeChat")
        assertTrue(result is LobsterCommandClient.Result.Ok)
        val ok = result as LobsterCommandClient.Result.Ok
        assertEquals("exec-1", ok.executionId)
        assertEquals("PENDING", ok.status)
    }

    // ── Test 2: submit returns Rejected on biz code 999 ───────────────────────

    @Test
    fun `submit returns Rejected on biz code 999`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueExecute {
                Response.success(CommonResult(code = 999, msg = "bad request"))
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.submit("open WeChat")
        assertTrue(result is LobsterCommandClient.Result.Rejected)
    }

    // ── Test 3: submit throws on network exception ────────────────────────────

    @Test
    fun `submit throws on network exception`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueExecute { throw IOException("simulated network failure") }
        }
        val client = LobsterCommandClient(api)
        assertTrue(runCatching { client.submit("open WeChat") }.isFailure)
    }

    // ── Test 4: poll returns Ok with SUCCESS ──────────────────────────────────

    @Test
    fun `poll returns Ok with SUCCESS`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueGetResult {
                Response.success(
                    CommonResult(code = 0, data = CommandDetailResult(status = "RUNNING")),
                )
            }
            enqueueGetResult {
                Response.success(
                    CommonResult(code = 0, data = CommandDetailResult(status = "SUCCESS", result = "ok")),
                )
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.poll("exec-1")
        assertTrue(result is LobsterCommandClient.Result.Ok)
        val ok = result as LobsterCommandClient.Result.Ok
        assertEquals("SUCCESS", ok.status)
    }

    // ── Test 5: poll returns Ok with FAILED carrying errorMessage ───────────────

    @Test
    fun `poll returns Ok with FAILED carrying errorMessage`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueGetResult {
                Response.success(CommonResult(code = 0, data = CommandDetailResult(status = "RUNNING")))
            }
            enqueueGetResult {
                Response.success(
                    CommonResult(code = 0, data = CommandDetailResult(status = "FAILED", errorMessage = "boom")),
                )
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.poll("exec-1")
        assertTrue(result is LobsterCommandClient.Result.Ok)
        val ok = result as LobsterCommandClient.Result.Ok
        assertEquals("FAILED", ok.status)
        assertEquals("boom", ok.detail?.errorMessage)
    }

    // ── Test 6: poll returns Ok with TIMEOUT ─────────────────────────────────

    @Test
    fun `poll returns Ok with TIMEOUT`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueGetResult {
                Response.success(CommonResult(code = 0, data = CommandDetailResult(status = "RUNNING")))
            }
            enqueueGetResult {
                Response.success(CommonResult(code = 0, data = CommandDetailResult(status = "TIMEOUT")))
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.poll("exec-1")
        assertTrue(result is LobsterCommandClient.Result.Ok)
        val ok = result as LobsterCommandClient.Result.Ok
        assertEquals("TIMEOUT", ok.status)
    }

    // ── Test 7: poll returns PollTimeout when total elapsed exceeds 5min ───────

    @Test
    fun `poll returns PollTimeout when total elapsed exceeds 5min`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueGetResult {
                Response.success(CommonResult(code = 0, data = CommandDetailResult(status = "RUNNING")))
            }
        }
        var callCount = 0
        val nowProvider = {
            callCount++
            if (callCount == 1) 0L else 6 * 60 * 1_000L  // 6 minutes > 5min timeout
        }
        val policy = PollingPolicy()
        val client = LobsterCommandClient(api, policy, nowProvider)
        val result = client.poll("exec-1")
        assertTrue(result is LobsterCommandClient.Result.PollTimeout)
        val ok = result as LobsterCommandClient.Result.PollTimeout
        assertEquals("exec-1", ok.executionId)
    }

    // ── Test 8: poll returns NotFound on 404 ──────────────────────────────────

    @Test
    fun `poll returns NotFound on 404`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueGetResult {
                Response.error(404, ResponseBody.create(null, "not found"))
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.poll("exec-nonexistent")
        assertTrue(result is LobsterCommandClient.Result.NotFound)
        val ok = result as LobsterCommandClient.Result.NotFound
        assertEquals("exec-nonexistent", ok.executionId)
    }

    // ── Test 9: submitHermesFeedback returns true on 200 ───────────────────────

    @Test
    fun `submitHermesFeedback returns true on 200`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueFeedback {
                Response.success(CommonResult(code = 0))
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.submitHermesFeedback("task_complete", taskUuid = "uuid-1")
        assertTrue(result)
    }

    // ── Test 10: submitHermesFeedback returns true on 202 ─────────────────────

    @Test
    fun `submitHermesFeedback returns true on 202`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueFeedback {
                // Retrofit's Response.error requires code >= 400, so we use Response.success
                // with a synthetic raw Response that has code 202. The implementation only
                // checks resp.code() == 200 || == 202, so the body content is irrelevant.
                val raw = okhttp3.Response.Builder()
                    .request(okhttp3.Request.Builder().url("https://example.com/").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(202)
                    .message("Accepted")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
                Response.success(CommonResult(code = 0, data = true), raw)
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.submitHermesFeedback("task_complete", taskUuid = "uuid-1")
        assertTrue(result)
    }

    // ── Test 11: submitHermesFeedback throws on network exception ───────────────

    @Test
    fun `submitHermesFeedback throws on network exception`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueFeedback { throw IOException("simulated network failure") }
        }
        val client = LobsterCommandClient(api)
        assertTrue(runCatching { client.submitHermesFeedback("task_complete") }.isFailure)
    }

    // ── Test 12: submitHermesFeedback returns false on non-2xx HTTP ────────────

    @Test
    fun `submitHermesFeedback returns false on non-2xx HTTP`() = runBlocking {
        val api = FakeLobsterCommandApi().apply {
            enqueueFeedback {
                Response.error(400, ResponseBody.create(null, "bad request"))
            }
        }
        val client = LobsterCommandClient(api)
        val result = client.submitHermesFeedback("task_complete")
        assertFalse(result)
    }
}