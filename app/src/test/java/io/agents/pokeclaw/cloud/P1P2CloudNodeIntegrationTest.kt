// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// P1/P2 端云任务执行器本地验证单测 — 不依赖 Android runtime，
// 用 fake DeviceApi + fake token store 验证完整最小闭环：
//   指令映射 → 本地技能执行 → 结果/证据打包 → 上报（Result 语义 + 离线入队）。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.api.DeviceApi
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterResponse
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskMode
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshResponse
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // 核心契约：指令确实被映射到 launch_app 技能（artifacts 体现），
        // 后续的 tool 执行依赖 Android runtime（get_screen_info/launch_app 工具），
        // 因此 success 在纯 JVM 测试中可能因工具不可用而失败 — 但绝不能是 TASK_REJECTED。
        assertNotEquals(
            "指令应被接受（不是 TASK_REJECTED）",
            CloudTaskErrorCode.TASK_REJECTED,
            result.errorCode,
        )
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

    // --- 扩展覆盖 ---

    @Test
    fun `LocalAgentTaskExecutor DRY_RUN mode 跳过实际执行 返回 success 含 dry_run artifact`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-dry-run",
            command = "打开微信",
            mode = TaskMode.DRY_RUN.raw,
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertTrue("DRY_RUN 应返回 success", result.success)
        assertTrue("artifacts 应包含 dry_run:true", result.artifacts.any { it == "dry_run:true" })
        assertTrue("artifacts 应包含 mode:dry_run", result.artifacts.any { it == "mode:dry_run" })
        assertTrue("artifacts 应包含 taskUuid", result.artifacts.any { it.contains("uuid-dry-run") })
    }

    @Test
    fun `LocalAgentTaskExecutor PREPARE_ONLY mode 跳过实际执行 返回 success 含 prepare_only artifact`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-prepare-only",
            command = "打开微信",
            mode = TaskMode.PREPARE_ONLY.raw,
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertTrue(result.success)
        assertTrue(result.artifacts.any { it == "mode:prepare_only" })
        // stub 分支不构建 skill/taskUuid/steps 等 skill-execution 元数据
        assertFalse("stub 不应进入技能执行路径", result.artifacts.any { it.startsWith("skill:") })
    }

    @Test
    fun `LocalAgentTaskExecutor mode=null artifacts 包含 mode default`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-no-mode",
            command = "打开微信",
            mode = null,
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        // 命令能映射到 launch_app skill，故不应是 TASK_REJECTED
        assertNotEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
        assertTrue(result.artifacts.any { it == "mode:default" })
    }

    @Test
    fun `LocalAgentTaskExecutor whitespace command 返回 TASK_REJECTED`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-ws",
            command = "   \t\n  ",
            mode = "ui",
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertFalse(result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
    }

    @Test
    fun `LocalAgentTaskExecutor getModelName 返回配置的 modelProvider`() {
        val executor = LocalAgentTaskExecutor(modelProvider = { "my-custom-model" })
        assertEquals("my-custom-model", executor.getModelName())
    }

    @Test
    fun `LocalAgentTaskExecutor getModelName 默认返回 local-skill-executor`() {
        val executor = LocalAgentTaskExecutor()
        assertEquals("local-skill-executor", executor.getModelName())
    }

    @Test
    fun `ExternalAutomationTaskExecutor blank command 返回 TASK_REJECTED`() = runBlocking {
        val executor = ExternalAutomationTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-ext-blank",
            command = "",
            mode = "ui",
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertFalse(result.success)
        assertEquals(CloudTaskErrorCode.TASK_REJECTED, result.errorCode)
    }

    @Test
    fun `ExternalAutomationTaskExecutor 正常 command 返回 success 含外部自动化 artifact`() = runBlocking {
        val executor = ExternalAutomationTaskExecutor()
        val task = PendingTaskItem(
            taskUuid = "uuid-ext-1",
            command = "打开微信",
            mode = "ui",
            createdAt = 1L,
            priority = "NORMAL",
        )
        val result = executor.execute(task)
        assertTrue(result.success)
        assertTrue(result.artifacts.any { it == "entry:external-automation" })
        assertTrue(result.artifacts.any { it.contains("uuid-ext-1") })
    }

    @Test
    fun `ExternalAutomationTaskExecutor getModelName 返回配置的 modelProvider`() {
        val executor = ExternalAutomationTaskExecutor(modelProvider = { "ext-bridge" })
        assertEquals("ext-bridge", executor.getModelName())
    }

    @Test
    fun `register data=null 时 tokenStore 不写入`() = runBlocking {
        // 后端 200 但 data 字段缺失 — 视为不写 token（保持旧 snapshot）
        val tokenStore = InMemoryTokenStore().apply {
            saveTokens("existing-tok", "existing-ref", 3600, 0L)
        }
        val api = FakeDeviceApi().apply {
            nextRegister = Response.success(DeviceRegister200Response(code = 0, data = null))
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.register(DeviceRegisterRequest(deviceId = "dev-002"))
        assertTrue(result.isSuccess)
        // tokenStore 的旧 snapshot 不应被覆盖
        assertEquals("existing-tok", tokenStore.snapshot()?.deviceToken)
    }

    @Test
    fun `submitTaskResult 500 服务端错误 返回 failure 且入离线队列（区别于 cancelTask 仅入 IOException）`() = runBlocking {
        // submitTaskResult 策略：除 HmacAuthException 外全部入队（含 5xx，方便等下次心跳重试）
        // cancelTask 策略：仅 IOException 入队（5xx 视为服务端错误，不入队）
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            submitShouldThrow = false
            submitSuccess = false  // 触发 500
        }
        val queue = InMemoryEventQueue()
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore, offlineQueue = queue)
        val result = client.submitTaskResult(
            taskUuid = "uuid-500",
            request = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
        )
        assertTrue(result.isFailure)
        assertEquals("submitTaskResult 5xx 仍入离线队列", 1, queue.size())
    }

    @Test
    fun `flushOfflineQueue 空队列 返回 0`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi()
        val queue = InMemoryEventQueue()
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore, offlineQueue = queue)
        val flushed = client.flushOfflineQueue(nowMillis = 1_000_000L)
        assertEquals(0, flushed)
    }

    @Test
    fun `flushOfflineQueue 单条重试失败 不增加 success 计数 但仍 markFailed`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val api = FakeDeviceApi().apply {
            submitShouldThrow = true  // 持续抛 IOException
        }
        val queue = InMemoryEventQueue().apply {
            enqueue(
                taskUuid = "uuid-retry-fail",
                payload = TaskResultRequest(status = TaskResultRequest.Status.SUCCESS),
                nowMillis = 0L,
            )
        }
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore, offlineQueue = queue)
        val flushed = client.flushOfflineQueue(nowMillis = 100L)
        assertEquals("失败不应计入 success", 0, flushed)
        assertEquals("队列仍保留事件", 1, queue.size())
        val due = queue.peekDue(nowMillis = 2000L, limit = 10)
        assertEquals(1, due.size)
        assertTrue("retryCount 应递增", due[0].retryCount >= 1)
    }

    @Test
    fun `sendHeartbeat snapshot 为 null 时 401 不触发 refresh 不重试`() = runBlocking {
        // 边界：没有 token 的 snapshot → runWithAuthRetry 直接 return first
        val tokenStore = InMemoryTokenStore()  // 空快照
        val api = FakeDeviceApi().apply {
            heartbeatCallCount = AtomicInteger(0)
            registerNextHeartbeatResponse { _ ->
                Response.error(401, okhttp3.ResponseBody.create(null, "Unauthorized"))
            }
        }
        val refreshCount = AtomicInteger(0)
        // FakeDeviceApi.refreshResponder 在 tokenStore.snapshot() == null 路径下根本不会被调用
        val client = RetrofitDeviceCloudClient(api = api, tokenStore = tokenStore)
        val result = client.sendHeartbeat(DeviceHeartbeatRequest(networkType = "wifi"))
        assertTrue(result.isFailure)
        assertEquals("心跳只调 1 次", 1, api.heartbeatCallCount?.get())
        assertNull(tokenStore.snapshot())
    }

    @Test
    fun `getModelName 在 LocalAgentTaskExecutor 与 ExternalAutomationTaskExecutor 上独立返回`() = runBlocking {
        val local = LocalAgentTaskExecutor(modelProvider = { "local-x" })
        val ext = ExternalAutomationTaskExecutor(modelProvider = { "ext-x" })
        assertEquals("local-x", local.getModelName())
        assertEquals("ext-x", ext.getModelName())
        // 默认值也独立
        assertEquals("local-skill-executor", LocalAgentTaskExecutor().getModelName())
        assertEquals("external-automation-bridge", ExternalAutomationTaskExecutor().getModelName())
    }

    @Test
    fun `LocalAgentTaskExecutor 优先级 NORMAL HIGH LOW 均能进入技能映射`() = runBlocking {
        val executor = LocalAgentTaskExecutor()
        listOf("NORMAL", "HIGH", "LOW", null).forEachIndexed { idx, prio ->
            val task = PendingTaskItem(
                taskUuid = "uuid-prio-$idx",
                command = "打开微信",
                mode = "ui",
                createdAt = 1L,
                priority = prio,
            )
            val r = executor.execute(task)
            assertNotEquals("优先级 $prio 不应被拒绝", CloudTaskErrorCode.TASK_REJECTED, r.errorCode)
            assertTrue(r.artifacts.any { it.startsWith("priority:") })
        }
    }

    // ── 测试 fake（nested private，文件级共享避免名字冲突） ──

    /**
     * InMemoryTokenStore — 模拟 CloudDeviceTokenStore，存于 HashMap。
     */
    private class InMemoryTokenStore : CloudDeviceTokenStore {
        private var snapshot: CloudDeviceTokenSnapshot? = null

        override fun saveTokens(
            deviceToken: String,
            refreshToken: String,
            expiresInSeconds: Int,
            nowMillis: Long,
        ) {
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

        override fun size(): Int = events.size
    }

    /**
     * FakeDeviceApi — 模拟 Retrofit DeviceApi，可预设响应。
     */
    private class FakeDeviceApi : DeviceApi {
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
            timestampMillis: Long,
            nonce: String,
            signature: String,
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
        override suspend fun getTaskByUuid(taskUuid: String): Response<io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse> {
            return Response.success(
                io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse(code = 0, data = null),
            )
        }
        override suspend fun cancelTask(
            taskUuid: String,
            timestampMillis: Long,
            nonce: String,
            signature: String,
            request: TaskResultRequest,
        ): Response<io.agents.pokeclaw.cloud.model.CancelTaskResponse> {
            return Response.success(
                io.agents.pokeclaw.cloud.model.CancelTaskResponse(code = 0, data = true),
            )
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
}
