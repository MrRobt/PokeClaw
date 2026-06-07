// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// P1/P2 端云任务执行器本地验证单测 — 不依赖 Android runtime，
// 用 fake DeviceApi + fake token store 验证完整最小闭环：
//   指令映射 → 本地技能执行 → 结果/证据打包 → 上报（Result 语义 + 离线入队）。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterResponse
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshResponse
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * 端云最小闭环单测套件。
 *
 * 覆盖：
 * - LocalAgentTaskExecutor.execute() — 指令映射 + 技能执行 + 错误码 / artifacts 打包
 * - RetrofitDeviceCloudClient.register() — token 自动写入 tokenStore
 * - RetrofitDeviceCloudClient.submitTaskResult() 失败时入离线队列
 * - RetrofitDeviceCloudClient.flushOfflineQueue() 补报成功后出队
 * - RetrofitDeviceCloudClient.runWithAuthRetry() 401 后刷新重放
 *
 * 不依赖 Android runtime：使用纯 JVM fake（不调 Retrofit 真实现）。
 */
class P1P2CloudNodeIntegrationTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
        SkillRegistry.loadBuiltInSkills()
    }

    @Test
    fun `打开应用指令映射到 launch_app 技能并执行`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-launch-app-1",
            command = "打开设置",
            mode = "ui",
            createdAt = 1_700_000_000L,
            priority = "HIGH",
        )
        val result = executor.execute(task)
        assertTrue("执行器必须返回成功", result.success)
        assertEquals(CloudTaskErrorCode.NONE, result.errorCode)
        // artifacts 至少应包含 skill/taskUuid/params/mode/priority 五项元数据
        assertTrue("artifacts 应包含 skill 字段", result.artifacts.any { it.startsWith("skill:launch_app") })
        assertTrue("artifacts 应包含 taskUuid 字段", result.artifacts.any { it.contains("uuid-launch-app-1") })
    }

    @Test
    fun `未知指令返回 TASK_REJECTED 且不调用执行器`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-unknown-1",
            command = "我想要做一件完全不在支持范围内的奇怪事情",
            mode = "ui",
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertEquals(false, result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
    }

    @Test
    fun `空指令返回 TASK_REJECTED`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-empty-1",
            command = "",
            mode = "ui",
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertEquals(false, result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
    }

    @Test
    fun `register 成功时 token 自动写入 tokenStore`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val api = FakeDeviceApi().apply {
            nextRegister = Response.success(
                DeviceRegister200Response(
                    code = 0,
                    data = DeviceRegisterResponse(
                        deviceToken = "device-tok-001",
                        refreshToken = "refresh-tok-001",
                        expiresIn = 3600,
                    ),
                ),
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            offlineQueue = null,
        )
        val result = client.register(
            DeviceRegisterRequest(
                deviceId = "dev-001",
                deviceName = "test",
                deviceModel = "Pixel-7",
                androidVersion = "14",
                appVersion = "0.7.0",
            ),
        )
        assertTrue(result.isSuccess)
        val snapshot = tokenStore.snapshot()
        assertNotNull("注册后 tokenStore 应有快照", snapshot)
        assertEquals("device-tok-001", snapshot?.deviceToken)
    }

    @Test
    fun `submitTaskResult 网络异常时入离线队列`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("t1", "r1", 3600, 0L)
        }
        val api = FakeDeviceApi().apply {
            submitShouldThrow = true
        }
        val queue = InMemoryEventQueue()
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            offlineQueue = queue,
        )
        val result = client.submitTaskResult(
            taskUuid = "uuid-offline-1",
            request = TaskResultRequest(
                status = TaskResultRequest.Status.SUCCESS,
                result = "ok",
            ),
        )
        assertTrue("网络异常应返回 failure", result.isFailure)
        assertEquals("离线队列应有一条事件", 1, queue.size())
    }

    @Test
    fun `flushOfflineQueue 把队首补报成功后从队列移除`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("t1", "r1", 3600, 0L)
        }
        val api = FakeDeviceApi().apply {
            submitShouldThrow = false
            submitSuccess = true
        }
        val queue = InMemoryEventQueue().apply {
            enqueue(
                taskUuid = "uuid-flush-1",
                payload = TaskResultRequest(
                    status = TaskResultRequest.Status.SUCCESS,
                    result = "buffered",
                ),
                nowMillis = 0L,
            )
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            offlineQueue = queue,
        )
        val flushed = client.flushOfflineQueue(100_000L)
        assertEquals(1, flushed)
        assertEquals("补报成功后队列应清空", 0, queue.size())
    }

    @Test
    fun `401 后自动刷新并重放`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("device-tok-old", "refresh-tok-old", 3600, 0L)
        }
        val api = FakeDeviceApi().apply {
            // 第一次心跳 401，刷新后重放成功
            heartbeatCallCount = AtomicInteger(0)
            registerNextHeartbeatResponse { callIndex ->
                if (callIndex == 0) Response.error(401, okhttp3.ResponseBody.create(null, "Unauthorized"))
                else Response.success(
                    io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response(
                        code = 0,
                        data = io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse(
                            pendingTaskCount = 0,
                            skillVersion = 1,
                            serverTime = 1L,
                        ),
                    ),
                )
            }
            registerNextRefreshResponse {
                Response.success(
                    io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response(
                        code = 0,
                        data = TokenRefreshResponse(
                            deviceToken = "device-tok-new",
                            expiresIn = 3600,
                        ),
                    ),
                )
            }
        }
        val client = RetrofitDeviceCloudClient(
            api = api,
            tokenStore = tokenStore,
            offlineQueue = null,
        )
        val result = client.sendHeartbeat(DeviceHeartbeatRequest(networkType = "wifi"))
        assertTrue("刷新重放后应成功", result.isSuccess)
        assertEquals("心跳调用 2 次（401 + 重放）", 2, api.heartbeatCallCount?.get())
        // token 应被刷新
        assertEquals("device-tok-new", tokenStore.snapshot()?.deviceToken)
    }
}

// ── 测试 fake（不依赖 Android，pure JVM） ──

/**
 * InMemoryTokenStore — 模拟 CloudDeviceTokenStore，存于 HashMap。
 */
private class InMemoryTokenStore : io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore {
    private var snapshot: io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot? = null

    override fun saveTokens(
        deviceToken: String,
        refreshToken: String,
        expiresInSeconds: Int,
        nowMillis: Long,
    ) {
        snapshot = io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot(
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
}

/**
 * InMemoryEventQueue — 模拟 OfflineResultQueue（不依赖 SharedPreferences / Android Context）。
 */
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
        events.filter { it.nextAttemptAtMillis <= nowMillis }
            .sortedBy { it.createdAtMillis }
            .take(limit.coerceAtLeast(1))

    override fun markSucceeded(requestId: String) {
        events.removeAll { it.requestId == requestId }
    }

    override fun markFailed(requestId: String, nowMillis: Long) {
        val idx = events.indexOfFirst { it.requestId == requestId }
        if (idx >= 0) {
            val e = events[idx]
            events[idx] = e.copy(
                retryCount = e.retryCount + 1,
                nextAttemptAtMillis = nowMillis + 1000L,
            )
        }
    }
}

/**
 * FakeDeviceApi — 模拟 Retrofit DeviceApi，可预设响应。
 */
private class FakeDeviceApi : io.agents.pokeclaw.cloud.api.DeviceApi {
    var nextRegister: Response<DeviceRegister200Response>? = null
    var submitShouldThrow: Boolean = false
    var submitSuccess: Boolean = true
    var heartbeatCallCount: AtomicInteger? = null
    private var heartbeatResponder: ((Int) -> Response<io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response>)? = null
    private var refreshResponder: ((Int) -> Response<io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response>)? = null
    private val refreshCallIndex = AtomicInteger(0)

    fun registerNextHeartbeatResponse(block: (Int) -> Response<io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response>) {
        heartbeatResponder = block
    }
    fun registerNextRefreshResponse(block: (Int) -> Response<io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response>) {
        refreshResponder = block
    }

    override suspend fun registerDevice(request: DeviceRegisterRequest): Response<DeviceRegister200Response> {
        return nextRegister ?: Response.success(DeviceRegister200Response(code = 0, data = null))
    }
    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Response<io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response> {
        val callIndex = heartbeatCallCount?.getAndIncrement() ?: 0
        return heartbeatResponder?.invoke(callIndex)
            ?: Response.success(
                io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response(
                    code = 0,
                    data = io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse(
                        pendingTaskCount = 0,
                        skillVersion = 1,
                        serverTime = 1L,
                    ),
                ),
            )
    }
    override suspend fun getPendingTasks(deviceId: String): Response<io.agents.pokeclaw.cloud.model.GetPendingTasks200Response> {
        return Response.success(
            io.agents.pokeclaw.cloud.model.GetPendingTasks200Response(code = 0, data = emptyList()),
        )
    }
    override suspend fun submitTaskResult(
        taskUuid: String,
        request: TaskResultRequest,
    ): Response<io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response> {
        if (submitShouldThrow) throw java.io.IOException("simulated network failure")
        return if (submitSuccess) {
            Response.success(
                io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response(
                    code = 0,
                    data = io.agents.pokeclaw.cloud.model.SubmitTaskResult200ResponseData(),
                ),
            )
        } else {
            Response.error(500, okhttp3.ResponseBody.create(null, "Internal Error"))
        }
    }
    override suspend fun refreshToken(request: TokenRefreshRequest): Response<io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response> {
        val idx = refreshCallIndex.getAndIncrement()
        return refreshResponder?.invoke(idx)
            ?: Response.success(
                io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response(
                    code = 0,
                    data = TokenRefreshResponse(deviceToken = "fallback", expiresIn = 3600),
                ),
            )
    }
}
