// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-034：task 主动取消 client 测试。
// 覆盖：成功 / 终态 false / 网络异常入队 / token refresh 重放 / HMAC 头注入 / 5xx / 空 deviceToken / 401004 触发重注册。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.auth.HmacAuthException
import io.agents.pokeclaw.cloud.api.DeviceApi
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
import io.agents.pokeclaw.cloud.model.TokenRefreshResponse
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * C3-01：取消任务端点测试。
 *
 * 覆盖：
 * 1. 成功（200 + data=true） → 真正取消
 * 2. 终态 false（200 + data=false） → 调用方降级
 * 3. 网络异常 → failure + 入离线队列
 * 4. 401 非 HMAC → runWithAuthRetry 重试
 * 5. 401004 DEVICE_MISMATCH → HmacAuthException + 触发 onAuthFailed
 * 6. 401001 INVALID_SIGNATURE → HmacAuthException
 * 7. 5xx → failure
 * 8. 缺 deviceToken → failure（不调云端）
 */
class CancelTaskClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    @Test
    fun `cancelTask 成功 - data true 表示真正取消`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.success(CancelTaskResponse(code = 0, data = true))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-cancel-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED, result = "user abort"),
        )
        assertTrue(result.isSuccess)
        assertTrue("data=true 表示真正取消", result.getOrNull()!!.cancelled())
    }

    @Test
    fun `cancelTask data false - 任务已是终态`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.success(CancelTaskResponse(code = 0, data = false, msg = "already SUCCESS"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-cancel-terminal",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue("HTTP 2xx 仍视为 success", result.isSuccess)
        assertFalse("data=false 表示未真正取消", result.getOrNull()!!.cancelled())
    }

    @Test
    fun `cancelTask 网络异常 - 入离线队列`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply { cancelShouldThrow = true }
        val queue = InMemoryEventQueue()
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore, offlineQueue = queue)
        val result = client.cancelTask(
            taskUuid = "uuid-cancel-offline",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        assertEquals("离线队列应有 1 条 cancel 事件", 1, queue.size())
    }

    @Test
    fun `cancelTask 401 后返回 failure 不刷新不重放（HMAC 路径不自动重试）`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("old-tok", "old-refresh", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            cancelCallCount = AtomicInteger(0)
            registerNextCancelTaskResponse { _ ->
                Response.error(401, ResponseBody.create(null, "Unauthorized"))
            }
            registerNextRefreshResponse {
                Response.success(
                    RefreshDeviceToken200Response(
                        code = 0,
                        data = TokenRefreshResponse(deviceToken = "new-tok", expiresIn = 3600),
                    ),
                )
            }
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-cancel-retry",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        // HMAC 路径设计：401 不透明重试（避免签名被滥用 / 时钟漂移连锁）。
        // 调用方收到 failure 后由上层 orchestrator 决定是否走完整 token 刷新 + 重派。
        assertTrue("401 应返回 failure", result.isFailure)
        assertEquals("cancelTask 应只被调用 1 次（无重试）", 1, api.cancelCallCount?.get())
        assertEquals("旧 token 不应被刷新（401 非 HMAC 业务码）", "old-tok", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `cancelTask 401004 DEVICE_MISMATCH 解析为 HmacAuthException 并触发 onAuthFailed`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                401,
                ResponseBody.create(null, """{"code":401004,"msg":"DEVICE_MISMATCH"}"""),
            )
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-mismatch",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull("HmacAuthException 必须抛出", ex)
        assertTrue("异常必须是 HmacAuthException", ex is HmacAuthException)
        assertEquals(401004, (ex as HmacAuthException).errorCode)
        assertTrue("DEVICE_MISMATCH 必须触发 onAuthFailed", onAuthFailedCalled)
    }

    @Test
    fun `cancelTask 401001 INVALID_SIGNATURE 解析为 HmacAuthException`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                401,
                ResponseBody.create(null, """{"code":401001,"msg":"INVALID_SIGNATURE"}"""),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-bad-sig",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? HmacAuthException
        assertNotNull(ex)
        assertEquals(401001, ex!!.errorCode)
        assertTrue(ex.isInvalidSignature)
    }

    @Test
    fun `cancelTask 5xx 返回 failure 不入队`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(502, ResponseBody.create(null, "Bad Gateway"))
        }
        val queue = InMemoryEventQueue()
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore, offlineQueue = queue)
        val result = client.cancelTask(
            taskUuid = "uuid-502",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        assertEquals("5xx 不入离线队列（仅网络异常入队）", 0, queue.size())
    }

    @Test
    fun `cancelTask 缺 deviceToken 返回 failure 不调云端`() = runBlocking {
        val tokenStore = InMemoryTokenStore()  // 空快照
        val api = FakeDeviceApi().apply { cancelCallCount = AtomicInteger(0) }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-no-tok",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        assertEquals("缺少 token 不应调云端", 0, api.cancelCallCount?.get() ?: 0)
    }

    // --- 扩展覆盖 ---

    @Test
    fun `cancelTask HTTP 200 但 data 字段缺失 返回 failure 响应体为空`() = runBlocking {
        // 2xx 但 body 为 null — handleHmacResponse 视为响应体空 → failure(IllegalStateException)
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.success(null as CancelTaskResponse?)
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-empty-body",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("应为 IllegalStateException", ex is IllegalStateException)
        assertTrue(
            "异常信息应提到 cancelTask 和响应体为空",
            ex!!.message?.contains("cancelTask") == true && ex.message?.contains("响应体为空") == true,
        )
    }

    @Test
    fun `cancelTask 401002 TIMESTAMP_EXPIRED 解析为 HmacAuthException 不触发 invalidate`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                401,
                ResponseBody.create(null, """{"code":401002,"msg":"TIMESTAMP_EXPIRED"}"""),
            )
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-stale",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? HmacAuthException
        assertNotNull("必须解析为 HmacAuthException", ex)
        assertEquals(401002, ex!!.errorCode)
        assertTrue("401002 不应触发 onAuthFailed（仅时钟漂移）", !onAuthFailedCalled)
        assertTrue("token 不应被 invalidate（仅 401004/403001 才清）", tokenStore.snapshot() != null)
    }

    @Test
    fun `cancelTask 401003 NONCE_DUPLICATE 解析为 HmacAuthException 不触发 invalidate`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                401,
                ResponseBody.create(null, """{"code":401003,"msg":"NONCE_DUPLICATE"}"""),
            )
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-dup-nonce",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? HmacAuthException
        assertNotNull(ex)
        assertEquals(401003, ex!!.errorCode)
        assertTrue(!onAuthFailedCalled)
        assertTrue(tokenStore.snapshot() != null)
    }

    @Test
    fun `cancelTask 401 空 body 不解析为 HmacAuthException 返回普通 HTTP failure`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(401, ResponseBody.create(null, ""))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-empty-401",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertFalse("空 body 不应解析为 HmacAuthException", ex is HmacAuthException)
        assertTrue(ex!!.message?.contains("HTTP 401") == true)
    }

    @Test
    fun `cancelTask 401 plain text DEVICE_MISMATCH 兜底映射为 HmacAuthException 并触发 invalidate`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(401, ResponseBody.create(null, "DEVICE_MISMATCH"))
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-plain-mismatch",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? HmacAuthException
        assertNotNull("plain text 兜底必须解析", ex)
        assertEquals(401004, ex!!.errorCode)
        assertTrue("DEVICE_MISMATCH 触发 invalidate", tokenStore.snapshot() == null)
        assertTrue("onAuthFailed 必须调用", onAuthFailedCalled)
    }

    @Test
    fun `cancelTask 403001 TASK_DEVICE_MISMATCH 触发 invalidate 与 onAuthFailed`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                403,
                ResponseBody.create(null, """{"code":403001,"msg":"TASK_DEVICE_MISMATCH"}"""),
            )
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-task-mismatch",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? HmacAuthException
        assertNotNull(ex)
        assertEquals(403001, ex!!.errorCode)
        assertTrue(ex.isTaskDeviceMismatch)
        assertTrue(tokenStore.snapshot() == null)
        assertTrue(onAuthFailedCalled)
    }

    @Test
    fun `cancelTask 404 NOT_FOUND 返回普通 failure 非 HmacAuthException`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(404, ResponseBody.create(null, "task not found"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-404",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertFalse("404 不应解析为 HmacAuthException", ex is HmacAuthException)
        assertTrue(ex!!.message?.contains("HTTP 404") == true)
    }

    @Test
    fun `cancelTask 403 未知 code 不触发 invalidate`() = runBlocking {
        // 403 + 未知 code = 不在 hmacErrorTextToCode 也不在 forCode() 里 → 普通 HTTP failure
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextCancelTask = Response.error(
                403,
                ResponseBody.create(null, """{"code":999999,"msg":"UNKNOWN"}"""),
            )
        }
        var onAuthFailedCalled = false
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            onAuthFailed = { onAuthFailedCalled = true },
        )
        val result = client.cancelTask(
            taskUuid = "uuid-403-unknown",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        assertFalse("未知 code 不应触发 onAuthFailed", onAuthFailedCalled)
        assertTrue("token 不应被 invalidate", tokenStore.snapshot() != null)
        val ex = result.exceptionOrNull()
        assertFalse(ex is HmacAuthException)
    }

    @Test
    fun `cancelTask 连续两次成功 第二次重用 tokenStore 状态`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            cancelCallCount = AtomicInteger(0)
            // 两次都返回 success(data=true)
            registerNextCancelTaskResponse { _ ->
                Response.success(CancelTaskResponse(code = 0, data = true))
            }
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val r1 = client.cancelTask(
            taskUuid = "uuid-1",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        val r2 = client.cancelTask(
            taskUuid = "uuid-2",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)
        assertEquals("cancelTask 应被调用 2 次", 2, api.cancelCallCount?.get())
        assertEquals("token 不变", "t1", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `cancelTask CancelTaskResponse 默认值 code=null data=null 且 isSuccess 返回 false`() {
        // 验证 data class 默认值 + isSuccess()/cancelled() 单元语义
        val resp = CancelTaskResponse()
        assertEquals(null, resp.code)
        assertEquals(null, resp.data)
        assertEquals(null, resp.msg)
        assertFalse("code=null 不算 success", resp.isSuccess())
        assertFalse("code=null 不算 cancelled", resp.cancelled())
    }

    @Test
    fun `cancelTask CancelTaskResponse code=200 与 code=0 同样视为 success`() {
        // OpenAPI 兼容：后端可能用 0（业务码）也可能用 200（HTTP 状态）
        assertTrue(CancelTaskResponse(code = 0, data = true).isSuccess())
        assertTrue(CancelTaskResponse(code = 200, data = true).isSuccess())
        assertTrue(CancelTaskResponse(code = 0, data = true).cancelled())
        assertFalse(CancelTaskResponse(code = 999, data = true).isSuccess())
        // code=0 + data=false 不算 cancelled（业务失败）
        assertFalse(CancelTaskResponse(code = 0, data = false).cancelled())
        // code=0 + data=null 同样不算 cancelled
        assertFalse(CancelTaskResponse(code = 0, data = null).cancelled())
    }

    @Test
    fun `cancelTask blank deviceToken 返回 failure 不调云端`() = runBlocking {
        // snapshot() 存在但 deviceToken 为空字符串 → isNullOrBlank 命中 → 不调云端
        val tokenStore = InMemoryTokenStore().apply {
            snapshot = CloudDeviceTokenSnapshot(
                deviceToken = "   ",
                refreshToken = "r1",
                expiresAtMillis = System.currentTimeMillis() + 3600_000L,
            )
        }
        val api = FakeDeviceApi().apply { cancelCallCount = AtomicInteger(0) }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.cancelTask(
            taskUuid = "uuid-blank-tok",
            request = TaskResultRequest(status = TaskResultRequest.Status.CANCELLED),
        )
        assertTrue(result.isFailure)
        assertEquals("blank token 不应调云端", 0, api.cancelCallCount?.get() ?: 0)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalStateException)
    }

    // ── 测试 fake（nested private，文件级共享避免名字冲突） ──

    private class InMemoryTokenStore : CloudDeviceTokenStore {
        var snapshot: CloudDeviceTokenSnapshot? = null
        override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
            snapshot = CloudDeviceTokenSnapshot(
                deviceToken = deviceToken,
                refreshToken = refreshToken,
                expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
            )
        }
        override fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long) {
            snapshot = snapshot?.copy(
                deviceToken = deviceToken,
                expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
            )
        }
        override fun snapshot() = snapshot
        override fun clear() { snapshot = null }
        override fun invalidate() { snapshot = null }
    }

    private class InMemoryEventQueue : OfflineResultQueue {
        private val events = mutableListOf<QueuedResult>()
        override fun enqueue(taskUuid: String, payload: TaskResultRequest, nowMillis: Long): Boolean {
            events.add(
                QueuedResult(
                    requestId = "req-${events.size + 1}",
                    taskUuid = taskUuid,
                    payload = payload,
                    createdAtMillis = nowMillis,
                    nextAttemptAtMillis = nowMillis,
                    retryCount = 0,
                ),
            )
            return true
        }
        override fun peekDue(nowMillis: Long, limit: Int): List<QueuedResult> =
            events.filter { it.nextAttemptAtMillis <= nowMillis }.take(limit.coerceAtLeast(1))
        override fun markSucceeded(requestId: String) { events.removeAll { it.requestId == requestId } }
        override fun markFailed(requestId: String, nowMillis: Long) {
            val idx = events.indexOfFirst { it.requestId == requestId }
            if (idx >= 0) {
                val e = events[idx]
                events[idx] = e.copy(retryCount = e.retryCount + 1, nextAttemptAtMillis = nowMillis + 1000L)
            }
        }
        override fun size(): Int = events.size
    }

    private class FakeDeviceApi : DeviceApi {
        var nextCancelTask: Response<CancelTaskResponse>? = null
        var cancelShouldThrow: Boolean = false
        var cancelCallCount: AtomicInteger? = null
        private var cancelResponder: ((Int) -> Response<CancelTaskResponse>)? = null
        private var refreshResponder: ((Int) -> Response<RefreshDeviceToken200Response>)? = null
        private val refreshCallIndex = AtomicInteger(0)

        fun registerNextCancelTaskResponse(block: (Int) -> Response<CancelTaskResponse>) {
            cancelResponder = block
        }
        fun registerNextRefreshResponse(block: (Int) -> Response<RefreshDeviceToken200Response>) {
            refreshResponder = block
        }

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
            return Response.success(SubmitTaskResult200Response(code = 0, data = null))
        }
        override suspend fun refreshToken(request: TokenRefreshRequest): Response<RefreshDeviceToken200Response> {
            val idx = refreshCallIndex.getAndIncrement()
            return refreshResponder?.invoke(idx)
                ?: Response.success(RefreshDeviceToken200Response(code = 0, data = null))
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
            if (cancelShouldThrow) throw java.io.IOException("simulated network failure")
            val callIndex = cancelCallCount?.getAndIncrement() ?: 0
            return cancelResponder?.invoke(callIndex) ?: nextCancelTask
                ?: Response.success(CancelTaskResponse(code = 0, data = true))
        }
    }
}
