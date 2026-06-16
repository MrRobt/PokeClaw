// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-033：task 单点查询 client 测试。
// 覆盖：成功 / 404（data=null）/ 未认证 / 5xx / 401 后自动刷新。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceTaskVO
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.CancelTaskResponse
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * C3-01：单任务查询端点测试。
 *
 * 覆盖：
 * 1. 成功（200 + data 非空）
 * 2. 任务不存在（200 + data=null）
 * 3. 未认证（401 非 HMAC → 走 runWithAuthRetry）
 * 4. 服务端错误（5xx → failure 不入队）
 * 5. 401 后刷新重放成功
 * 6. 业务 code 非 0/200 时仍返回 success（Result 包装 HTTP 层）
 */
class GetTaskByUuidClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    @Test
    fun `getTaskByUuid 200 返回带 taskUuid 的 DeviceTaskVO`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(
                        id = 100,
                        taskUuid = "uuid-q-001",
                        deviceId = "dev-1",
                        command = "打开微信",
                        status = DeviceTaskVO.Status.RUNNING,
                    ),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-q-001")
        assertTrue(result.isSuccess)
        val payload = result.getOrNull()!!
        assertEquals(0, payload.code)
        assertNotNull("data 必须非空", payload.data)
        assertEquals("uuid-q-001", payload.data?.taskUuid)
    }

    @Test
    fun `getTaskByUuid 200 但 data 为 null 表示任务不存在`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(GetTaskByUuidResponse(code = 0, data = null))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("missing-uuid")
        assertTrue(result.isSuccess)
        val payload = result.getOrNull()!!
        assertNull("data 必须为 null（任务不存在）", payload.data)
    }

    @Test
    fun `getTaskByUuid 5xx 返回 failure 不刷新 token`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.error(503, ResponseBody.create(null, "Service Unavailable"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-q-503")
        assertTrue(result.isFailure)
        // 不应该触发 token 刷新
        assertEquals("t1", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `getTaskByUuid 401 触发 token 刷新并重放`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("old-tok", "old-refresh", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            getTaskCallCount = AtomicInteger(0)
            registerNextGetTaskByUuidResponse { callIndex ->
                if (callIndex == 0) {
                    Response.error(401, ResponseBody.create(null, "Unauthorized"))
                } else {
                    Response.success(
                        GetTaskByUuidResponse(
                            code = 0,
                            data = DeviceTaskVO(taskUuid = "uuid-retry"),
                        ),
                    )
                }
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
        val result = client.getTaskByUuid("uuid-retry")
        assertTrue("刷新重放后应成功", result.isSuccess)
        assertEquals("新 token 已生效", "new-tok", tokenStore.snapshot()?.deviceToken)
        assertEquals(2, api.getTaskCallCount?.get())
    }

    @Test
    fun `getTaskByUuid 业务 code 非 0 仍返回 success（HTTP 层 200）`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(code = 999, msg = "自定义业务码", data = null),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-biz-err")
        // 当前实现：HTTP 2xx 即视为 Result.success，由调用方按 data 判定业务
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.data)
    }

    @Test
    fun `getTaskByUuid 网络异常返回 failure`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply { getTaskShouldThrow = true }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-net-err")
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    // --- HTTP 4xx 错误码返回 failure ---

    @Test
    fun `getTaskByUuid 404 返回 failure 不刷新 token`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.error(404, ResponseBody.create(null, "Not Found"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-404")
        assertTrue(result.isFailure)
        // 404 不触发 token 刷新
        assertEquals("t1", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `getTaskByUuid 403 返回 failure 不刷新 token`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.error(403, ResponseBody.create(null, "Forbidden"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-403")
        assertTrue(result.isFailure)
        assertEquals("t1", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `getTaskByUuid 400 返回 failure 不刷新 token`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.error(400, ResponseBody.create(null, "Bad Request"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-400")
        assertTrue(result.isFailure)
        assertEquals("t1", tokenStore.snapshot()?.deviceToken)
    }

    // --- token 状态边界 ---

    @Test
    fun `getTaskByUuid 401 但 tokenStore snapshot 为 null 不刷新直接返回 failure`() = runBlocking {
        // tokenStore 没有任何 token → runWithAuthRetry 不会尝试 refresh
        val tokenStore = InMemoryTokenStore()
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.error(401, ResponseBody.create(null, "Unauthorized"))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-no-token-401")
        assertTrue(result.isFailure)
        assertNull("snapshot 应仍为 null", tokenStore.snapshot())
    }

    @Test
    fun `getTaskByUuid 401 后 refresh 失败 返回 first failure 不重试`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("old-tok", "old-refresh", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            getTaskCallCount = AtomicInteger(0)
            registerNextGetTaskByUuidResponse { _ ->
                Response.error(401, ResponseBody.create(null, "Unauthorized"))
            }
            registerNextRefreshResponse { _ ->
                // refresh 也失败
                Response.error(500, ResponseBody.create(null, "Server Error"))
            }
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("uuid-401-500")
        assertTrue(result.isFailure)
        // getTask 只调用了 1 次（首次失败，refresh 失败后不再重试）
        assertEquals(1, api.getTaskCallCount?.get())
    }

    // --- DeviceTaskVO Status 各枚举 ---

    @Test
    fun `getTaskByUuid 200 PENDING status`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-pending", status = DeviceTaskVO.Status.PENDING),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-pending")
        assertTrue(result.isSuccess)
        assertEquals(DeviceTaskVO.Status.PENDING, result.getOrNull()?.data?.status)
    }

    @Test
    fun `getTaskByUuid 200 SUCCESS status`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-success", status = DeviceTaskVO.Status.SUCCESS),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-success")
        assertTrue(result.isSuccess)
        assertEquals(DeviceTaskVO.Status.SUCCESS, result.getOrNull()?.data?.status)
    }

    @Test
    fun `getTaskByUuid 200 FAILED status`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-failed", status = DeviceTaskVO.Status.FAILED),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-failed")
        assertTrue(result.isSuccess)
        assertEquals(DeviceTaskVO.Status.FAILED, result.getOrNull()?.data?.status)
    }

    @Test
    fun `getTaskByUuid 200 CANCELLED status`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-cancelled", status = DeviceTaskVO.Status.CANCELLED),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-cancelled")
        assertTrue(result.isSuccess)
        assertEquals(DeviceTaskVO.Status.CANCELLED, result.getOrNull()?.data?.status)
    }

    @Test
    fun `getTaskByUuid 200 status 字段不传时为 null`() = runBlocking {
        // DeviceTaskVO.status 是 nullable，不传时为 null
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-default"),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-default")
        assertTrue(result.isSuccess)
        assertNull("status 不传时为 null", result.getOrNull()?.data?.status)
    }

    // --- DeviceTaskVO 字段 ---

    @Test
    fun `getTaskByUuid 200 仅带 taskUuid 其余字段全 null`() = runBlocking {
        // 模拟"任务存在但只有 taskUuid"的极端情况
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(taskUuid = "u-min"),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-min")
        val data = result.getOrNull()?.data
        assertNotNull(data)
        assertEquals("u-min", data?.taskUuid)
        assertNull("id 默认 null", data?.id)
        assertNull("deviceId 默认 null", data?.deviceId)
        assertNull("command 默认 null", data?.command)
        assertNull("mode 默认 null", data?.mode)
    }

    @Test
    fun `getTaskByUuid 200 带完整 DeviceTaskVO 字段全透传`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    data = DeviceTaskVO(
                        id = 999,
                        taskUuid = "u-full",
                        deviceId = "device-007",
                        command = "发短信给张三",
                        mode = "TASK",
                        status = DeviceTaskVO.Status.RUNNING,
                    ),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-full")
        val data = result.getOrNull()?.data
        assertEquals(999, data?.id)
        assertEquals("u-full", data?.taskUuid)
        assertEquals("device-007", data?.deviceId)
        assertEquals("发短信给张三", data?.command)
        assertEquals("TASK", data?.mode)
        assertEquals(DeviceTaskVO.Status.RUNNING, data?.status)
    }

    @Test
    fun `getTaskByUuid 200 业务 code=0 但 msg 非空时仍视为 success HTTP 层`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            nextGetTaskByUuid = Response.success(
                GetTaskByUuidResponse(
                    code = 0,
                    msg = "操作成功但带附加信息",
                    data = DeviceTaskVO(taskUuid = "u-with-msg"),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.getTaskByUuid("u-with-msg")
        assertTrue(result.isSuccess)
        assertEquals("操作成功但带附加信息", result.getOrNull()?.msg)
    }

    // ── 测试 fake（nested private，文件级共享避免名字冲突） ──

    private class InMemoryTokenStore : CloudDeviceTokenStore {
        private var snapshot: CloudDeviceTokenSnapshot? = null
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

    private class FakeDeviceApi : DeviceApi {
        var nextGetTaskByUuid: Response<GetTaskByUuidResponse>? = null
        var getTaskShouldThrow: Boolean = false
        var getTaskCallCount: AtomicInteger? = null
        private var getTaskResponder: ((Int) -> Response<GetTaskByUuidResponse>)? = null
        private var refreshResponder: ((Int) -> Response<RefreshDeviceToken200Response>)? = null
        private val refreshCallIndex = AtomicInteger(0)

        fun registerNextGetTaskByUuidResponse(block: (Int) -> Response<GetTaskByUuidResponse>) {
            getTaskResponder = block
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
            if (getTaskShouldThrow) throw java.io.IOException("simulated network failure")
            val callIndex = getTaskCallCount?.getAndIncrement() ?: 0
            return getTaskResponder?.invoke(callIndex)
                ?: nextGetTaskByUuid
                ?: Response.success(GetTaskByUuidResponse(code = 0, data = null))
        }
        override suspend fun cancelTask(
            taskUuid: String,
            timestampMillis: Long,
            nonce: String,
            signature: String,
            request: TaskResultRequest,
        ): Response<CancelTaskResponse> {
            return Response.success(CancelTaskResponse(code = 0, data = false))
        }
    }
}
