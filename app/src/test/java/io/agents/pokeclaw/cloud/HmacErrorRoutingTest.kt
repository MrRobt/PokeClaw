// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 回归测试：403001 TASK_DEVICE_MISMATCH 应触发 token 失效 + onAuthFailed 回调
// + HMAC 时间偏移 provider 被实际消费

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.CancelTaskResponse
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * US-D-037 regression tests for the two critical bugs caught by architect review:
 *
 * 1. **CRITICAL #1**: HTTP 403 with body code=403001 (TASK_DEVICE_MISMATCH) must trigger
 *    `tokenStore.invalidate()` + `onAuthFailed.invoke()` (the old gate `if (code == 401)`
 *    blocked this path).
 *
 * 2. **CRITICAL #2**: The HMAC `X-Claw-Timestamp` must reflect `nowMillis - hmacTimeOffset`,
 *    not raw `nowMillis` (the old code never consumed the offset provider, so clock skew
 *    WARN state had no effect on the signed timestamp).
 */
class HmacErrorRoutingTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Test 1: HTTP 403 + body.code=403001 → invalidate + onAuthFailed ────────────

    @Test
    fun `HTTP 403 with body code 403001 triggers token invalidate and onAuthFailed`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            // 第一次 submitTaskResult：返回 403 + body.code=403001
            submitResponse = Response.error(
                403,
                """{"code":403001,"msg":"TASK_DEVICE_MISMATCH"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        // 1. result is failure
        assertTrue("result should be failure", result.isFailure)
        // 2. tokenStore was invalidated
        assertNull("tokenStore should be invalidated after 403001", tokenStore.snapshot())
        // 3. onAuthFailed was invoked
        assertEquals("onAuthFailed should be invoked once after 403001", 1, authFailedCount)
    }

    // ── Test 2: HTTP 401 + body.code=401004 (DEVICE_MISMATCH) → invalidate + onAuthFailed ─

    @Test
    fun `HTTP 401 with body code 401004 still triggers token invalidate and onAuthFailed`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401004,"msg":"DEVICE_MISMATCH"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue("result should be failure", result.isFailure)
        assertNull("tokenStore should be invalidated after 401004", tokenStore.snapshot())
        assertEquals(1, authFailedCount)
    }

    // ── Test 3: HTTP 200 (success) → no invalidate, no onAuthFailed ────────────────

    @Test
    fun `HTTP 200 success does not invalidate or invoke onAuthFailed`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue("result should be success", result.isSuccess)
        assertNotNull("tokenStore should be preserved on success", tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 4: HMAC X-Claw-Timestamp reflects hmacTimeOffset (CRITICAL #2) ────────

    @Test
    fun `HMAC X-Claw-Timestamp is nowMillis minus hmacTimeOffset`() = runBlocking {
        val baseNow = 10_000_000L
        val observedTimestamps = mutableListOf<Long>()
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = baseNow)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            nowProvider = { baseNow + 60_000L }, // local time 1 min ahead
            hmacTimeOffsetProvider = { 300_000L }, // 5 min WARN skew from serverTime
        )
        client.submitTaskResult(
            taskUuid = "uuid-ts",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        observedTimestamps.add(api.lastSubmitTimestamp ?: -1L)

        // Expected: nowProvider() - hmacTimeOffset = (baseNow + 60_000) - 300_000 = baseNow - 240_000
        val expected = baseNow + 60_000L - 300_000L
        assertEquals("HMAC timestamp should equal nowMillis - hmacTimeOffset", expected, observedTimestamps[0])
    }

    // ── Test 5: Default hmacTimeOffset=0L preserves R7 之前行为 (backward compat) ────

    @Test
    fun `default hmacTimeOffset of 0L leaves HMAC timestamp unchanged`() = runBlocking {
        val baseNow = 10_000_000L
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = baseNow)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            nowProvider = { baseNow + 60_000L },
        )
        client.submitTaskResult(
            taskUuid = "uuid-ts",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertEquals("default offset=0L → HMAC ts = nowProvider()", baseNow + 60_000L, api.lastSubmitTimestamp)
    }

    // ── Test 6: setHmacTimeOffsetProvider allows post-construction binding ─────────

    @Test
    fun `setHmacTimeOffsetProvider updates the offset for subsequent HMAC calls`() = runBlocking {
        val baseNow = 10_000_000L
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = baseNow)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            nowProvider = { baseNow + 60_000L },
        )
        // 第一调用：offset=0
        client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        val firstTs = api.lastSubmitTimestamp
        // 设置新的 offset provider
        client.setHmacTimeOffsetProvider { 300_000L }
        // 第二次调用：offset=300_000
        client.submitTaskResult(
            taskUuid = "uuid-2",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        val secondTs = api.lastSubmitTimestamp
        assertEquals(baseNow + 60_000L, firstTs)
        assertEquals(baseNow + 60_000L - 300_000L, secondTs)
    }

    // ── Test 7: 401001 INVALID_SIGNATURE 不会触发 invalidate ──────────────────

    @Test
    fun `HTTP 401 with body code 401001 INVALID_SIGNATURE does not invalidate token`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401001,"msg":"INVALID_SIGNATURE"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        // INVALID_SIGNATURE 不在 invalidate 触发列表中
        assertNotNull("tokenStore 应保留", tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 8: 401002 TIMESTAMP_EXPIRED 不会触发 invalidate ──────────────────

    @Test
    fun `HTTP 401 with body code 401002 TIMESTAMP_EXPIRED does not invalidate token`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401002,"msg":"TIMESTAMP_EXPIRED"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        assertNotNull(tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 9: 401003 NONCE_DUPLICATE 不会触发 invalidate ─────────────────────

    @Test
    fun `HTTP 401 with body code 401003 NONCE_DUPLICATE does not invalidate token`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401003,"msg":"NONCE_DUPLICATE"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        assertNotNull(tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 10: 401 with empty body → no HmacAuthException ─────────────────────

    @Test
    fun `HTTP 401 with empty body does not produce HmacAuthException`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                "".toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("exception 不应是 HmacAuthException", ex !is io.agents.pokeclaw.cloud.auth.HmacAuthException)
    }

    // ── Test 11: 401 with plain text body 'INVALID_SIGNATURE' fallback ────────────

    @Test
    fun `HTTP 401 with plain text body 'INVALID_SIGNATURE' fallback parses to HmacAuthException`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                "INVALID_SIGNATURE".toResponseBody("text/plain".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("plain text 兜底应解析为 HmacAuthException", ex is io.agents.pokeclaw.cloud.auth.HmacAuthException)
        assertEquals(401001, (ex as io.agents.pokeclaw.cloud.auth.HmacAuthException).errorCode)
    }

    // ── Test 12: HTTP 5xx → no invalidate ───────────────────────────────────────

    @Test
    fun `HTTP 500 does not invalidate token or invoke onAuthFailed`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                500,
                """{"msg":"server error"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        assertNotNull("5xx 不应失效 token", tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 13: HTTP 401 with unknown body code 999999 → no invalidate ─────────

    @Test
    fun `HTTP 401 with unknown body code does not invalidate token`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":999999,"msg":"UNKNOWN_HMAC"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        // 999999 不在 forCode 映射中 → 不会被识别为 HMAC 错误 → 走普通 failure
        assertNotNull("未知 code 不触发 invalidate", tokenStore.snapshot())
        assertEquals(0, authFailedCount)
    }

    // ── Test 14: setHmacTimeOffsetProvider can be called multiple times safely ─

    @Test
    fun `setHmacTimeOffsetProvider 多次调用 以最后一次为准`() = runBlocking {
        val baseNow = 10_000_000L
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = baseNow)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            nowProvider = { baseNow + 60_000L },
        )
        client.setHmacTimeOffsetProvider { 100_000L }
        client.setHmacTimeOffsetProvider { 200_000L }
        client.setHmacTimeOffsetProvider { 300_000L }
        client.submitTaskResult(
            taskUuid = "uuid-multi",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        // 最后一次 300_000 生效
        assertEquals(baseNow + 60_000L - 300_000L, api.lastSubmitTimestamp)
    }

    // ── Test 15: 401001 错误消息含 INVALID_SIGNATURE 关键字 ────────────────────

    @Test
    fun `HTTP 401 with body code 401001 异常 errorCode 为 401001`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401001,"msg":"INVALID_SIGNATURE"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        val ex = result.exceptionOrNull()
        assertTrue("应是 HmacAuthException", ex is io.agents.pokeclaw.cloud.auth.HmacAuthException)
        assertEquals(401001, (ex as io.agents.pokeclaw.cloud.auth.HmacAuthException).errorCode)
    }

    // ── Test 16: 403001 错误码后 第二次调用 snapshot 仍为 null ──────────────────

    @Test
    fun `403001 invalidate 后 第二次调用 tokenStore snapshot 仍为 null`() = runBlocking {
        var authFailedCount = 0
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                403,
                """{"code":403001,"msg":"TASK_DEVICE_MISMATCH"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { authFailedCount += 1 },
        )
        client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        // 第一次调用后 snapshot 已被 invalidate
        assertNull(tokenStore.snapshot())
        assertEquals(1, authFailedCount)

        // 第二次调用：HMAC 路径在 deviceToken 为空时直接返回 IllegalStateException
        val second = client.submitTaskResult(
            taskUuid = "uuid-2",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue("无 deviceToken 时应失败", second.isFailure)
        assertNull("snapshot 仍为 null", tokenStore.snapshot())
    }

    // ── Test 17: HMAC X-Claw-Timestamp 负 offset 也支持 ─────────────────────────

    @Test
    fun `HMAC X-Claw-Timestamp 支持负 hmacTimeOffset 即 local clock 落后于 server`() = runBlocking {
        val baseNow = 10_000_000L
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = baseNow)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.success(
                SubmitTaskResult200Response(code = 0, data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            nowProvider = { baseNow + 60_000L },
            // local clock 落后 server 5 分钟 → offset 为 -300_000
            hmacTimeOffsetProvider = { -300_000L },
        )
        client.submitTaskResult(
            taskUuid = "uuid-neg-offset",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        // 预期: nowProvider() - (-300_000) = nowProvider() + 300_000
        val expected = baseNow + 60_000L - (-300_000L)
        assertEquals(expected, api.lastSubmitTimestamp)
    }

    // ── Test 18: 401004 HmacAuthException 错误消息含 DEVICE_MISMATCH 关键字 ─────

    @Test
    fun `HTTP 401 with body code 401004 异常 errorCode 为 401004 reason 含 DEVICE_MISMATCH`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("dev-token", "ref-token", 3600, nowMillis = 1_000_000L)
        }
        val api = FakeDeviceApi().apply {
            submitResponse = Response.error(
                401,
                """{"code":401004,"msg":"DEVICE_MISMATCH"}"""
                    .toResponseBody("application/json".toMediaTypeOrNull()),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        val ex = result.exceptionOrNull()
        assertTrue(ex is io.agents.pokeclaw.cloud.auth.HmacAuthException)
        val hmacEx = ex as io.agents.pokeclaw.cloud.auth.HmacAuthException
        assertEquals(401004, hmacEx.errorCode)
        assertTrue("reason 应含 DEVICE_MISMATCH: ${hmacEx.reason}", hmacEx.reason.contains("DEVICE_MISMATCH"))
        assertTrue("isDeviceMismatch 应为 true", hmacEx.isDeviceMismatch)
    }

    // ── 测试 fake（nested private，文件级共享避免名字冲突） ──

    private class FakeDeviceApi : DeviceApi {
        var submitResponse: Response<SubmitTaskResult200Response>? = null
        var lastSubmitTimestamp: Long? = null
        private val submitCallCount = AtomicInteger(0)

        override suspend fun registerDevice(request: DeviceRegisterRequest): Response<DeviceRegister200Response> {
            return Response.success(DeviceRegister200Response(code = 0, data = null))
        }
        override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Response<DeviceHeartbeat200Response> {
            return Response.success(DeviceHeartbeat200Response(code = 0, data = null))
        }
        override suspend fun getPendingTasks(deviceId: String): Response<GetPendingTasks200Response> {
            return Response.success(GetPendingTasks200Response(code = 0, data = emptyList()))
        }
        override suspend fun submitTaskResult(
            taskUuid: String,
            timestampMillis: Long,
            nonce: String,
            signature: String,
            request: TaskResultRequest,
        ): Response<SubmitTaskResult200Response> {
            lastSubmitTimestamp = timestampMillis
            submitCallCount.incrementAndGet()
            return submitResponse ?: Response.success(SubmitTaskResult200Response(code = 0, data = null))
        }
        override suspend fun refreshToken(request: TokenRefreshRequest): Response<RefreshDeviceToken200Response> {
            return Response.success(RefreshDeviceToken200Response(code = 0, data = null))
        }
        override suspend fun getTaskByUuid(taskUuid: String): Response<GetTaskByUuidResponse> {
            return Response.success(GetTaskByUuidResponse(code = 0, data = null))
        }
        override suspend fun cancelTask(
            taskUuid: String,
            timestampMillis: Long,
            nonce: String,
            signature: String,
            request: TaskResultRequest,
        ): Response<CancelTaskResponse> {
            return Response.success(CancelTaskResponse(code = 0, data = null))
        }
    }

    private class InMemoryTokenStore : CloudDeviceTokenStore {
        private var snapshotInternal: CloudDeviceTokenSnapshot? = null
        override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
            snapshotInternal = CloudDeviceTokenSnapshot(
                deviceToken = deviceToken,
                refreshToken = refreshToken,
                expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
            )
        }
        override fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long) {
            snapshotInternal = snapshotInternal?.copy(
                deviceToken = deviceToken,
                expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
            )
        }
        override fun snapshot(): CloudDeviceTokenSnapshot? = snapshotInternal
        override fun clear() { snapshotInternal = null }
        override fun invalidate() {
            snapshotInternal = null
        }
    }
}
