// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// P0 #1: CloudNodeOrchestrator 状态机单测 — 在 JVM 上验证 IDLE/REGISTERING/RUNNING/EXECUTING/STOPPED/ERROR
// 转换、注册失败、心跳连续失败、任务领取、取消当前任务、deviceId 持久化、register 请求体字段。
// 不依赖 Android runtime：FakeDeviceCloudClient / InMemoryTokenStore / InMemoryOfflineQueue / FakeDeviceInfoProvider。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.model.CancelTaskResponse
import io.agents.pokeclaw.cloud.model.DeviceHeartbeat200Response
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegister200Response
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterResponse
import io.agents.pokeclaw.cloud.model.GetPendingTasks200Response
import io.agents.pokeclaw.cloud.model.NetworkType
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.RefreshDeviceToken200Response
import io.agents.pokeclaw.cloud.model.SubmitTaskResult200Response
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshRequest
import io.agents.pokeclaw.cloud.model.TokenRefreshResponse
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * CloudNodeOrchestrator 状态机单测。
 *
 * 覆盖：
 * - start() 状态转移：IDLE → REGISTERING → RUNNING
 * - start() 缺 token + register 失败 → ERROR
 * - start() 已有 token → 跳过注册直接 RUNNING
 * - start() 二次调用幂等
 * - stop() → STOPPED
 * - onPendingTasksAvailable()：取首个任务 → EXECUTING
 * - onPendingTasksAvailable() 在 EXECUTING 中：跳过
 * - cancelTask() 无当前任务 → failure
 * - cancelTask() 有当前任务 → 调 cloudClient.cancelTask
 * - registerDevice() 请求体字段对齐 deviceInfo provider
 * - deviceInfo.loadOrGenerateDeviceId() 持久化到 KVUtils
 * - heartbeat 连续失败 3 次 → ERROR
 */
class CloudNodeOrchestratorTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        XLog.setTestMode(true)
        KVUtils.resetTestBacking()
    }

    @After
    fun tearDown() {
        // 取消所有挂在 testScope 的协程（heartbeat 循环），避免跨测试残留。
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        KVUtils.resetTestBacking()
    }

    // ── 基础生命周期 ──

    @Test
    fun `start 无 token 注册成功 - IDLE 转 REGISTERING 转 RUNNING`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val orchestrator = newOrchestrator(client, tokenStore)
        assertEquals(CloudNodeOrchestrator.State.IDLE, orchestrator.state)

        orchestrator.start()
        // start() 走协程，runBlocking + Unconfined 调度让协程同步推进直到第一个 delay。
        testSchedulerAdvance(orchestrator)

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertNotNull("注册成功后 deviceId 应被持久化", orchestrator.getDeviceId())
        assertEquals(1, client.registerCallCount.get())
    }

    @Test
    fun `start 无 token 注册失败 - 进入 ERROR 状态`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.failure(IllegalStateException("network unreachable"))
        }
        val orchestrator = newOrchestrator(client, tokenStore)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        assertEquals(CloudNodeOrchestrator.State.ERROR, orchestrator.state)
        assertEquals("注册失败不应消耗额外 register 调用", 1, client.registerCallCount.get())
    }

    @Test
    fun `start 已有 token - 跳过 register 直接 RUNNING`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val client = FakeDeviceCloudClient()
        val orchestrator = newOrchestrator(client, tokenStore)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertEquals("已有 token 不应调 register", 0, client.registerCallCount.get())
    }

    @Test
    fun `start 二次调用幂等 - 不重复启动`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val orchestrator = newOrchestrator(client, tokenStore)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)
        val stateAfterFirst = orchestrator.state

        // 二次 start：当前是 RUNNING，应被忽略
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        assertEquals(stateAfterFirst, orchestrator.state)
        assertEquals("二次 start 不应重复调 register", 1, client.registerCallCount.get())
    }

    @Test
    fun `stop - 任意状态都可转 STOPPED`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val orchestrator = newOrchestrator(client, tokenStore)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)
        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)

        orchestrator.stop()
        assertEquals(CloudNodeOrchestrator.State.STOPPED, orchestrator.state)
    }

    // ── 任务领取与执行 ──

    @Test
    fun `onPendingTasksAvailable 收到任务 - 转移 EXECUTING 执行后回 RUNNING`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val executor = RecordingTaskExecutor().apply { nextResult = CloudTaskExecutionResult.success("ok") }
        val orchestrator = newOrchestrator(client, tokenStore, executor = executor)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        val tasks = listOf(
            PendingTaskItem(taskUuid = "uuid-1", command = "open settings", createdAt = 1L),
            PendingTaskItem(taskUuid = "uuid-2", command = "send message", createdAt = 2L),
        )
        orchestrator.onPendingTasksAvailable(tasks)
        testSchedulerAdvance(orchestrator)

        assertEquals(1, executor.executedTasks.size)
        assertEquals("uuid-1", executor.executedTasks[0].taskUuid)
        // 执行完成后回到 RUNNING
        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertNull("执行结束后 currentTaskUuid 应清空", orchestrator.getCurrentTaskUuid())
    }

    @Test
    fun `onPendingTasksAvailable 在 EXECUTING 中 - 跳过不抢任务`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val executor = BlockingTaskExecutor()  // 挂起，让状态卡在 EXECUTING
        val orchestrator = newOrchestrator(client, tokenStore, executor = executor)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        // 异步触发任务执行（任务在 EXECUTING 中挂起）
        val executionJob = testScope.launch {
            orchestrator.onPendingTasksAvailable(listOf(PendingTaskItem(taskUuid = "uuid-1", command = "first", createdAt = 1L)))
        }
        Thread.sleep(50)
        assertEquals(CloudNodeOrchestrator.State.EXECUTING, orchestrator.state)

        // 在 EXECUTING 中再次推送 → 跳过
        val second = orchestrator.onPendingTasksAvailable(listOf(PendingTaskItem(taskUuid = "uuid-2", command = "second", createdAt = 2L)))

        // 释放挂起的 execute()，让 executionJob 完成
        executor.release()
        executionJob.join()

        assertEquals("只执行了第一个任务", 1, executor.executedTasks.size)
        assertEquals("uuid-1", executor.executedTasks[0].taskUuid)
    }

    @Test
    fun `onPendingTasksAvailable 空列表 - 忽略不报错`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val executor = RecordingTaskExecutor()
        val orchestrator = newOrchestrator(client, tokenStore, executor = executor)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        orchestrator.onPendingTasksAvailable(emptyList())
        testSchedulerAdvance(orchestrator)

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertEquals(0, executor.executedTasks.size)
    }

    // ── 取消任务 ──

    @Test
    fun `cancelTask 无当前任务 - 返回 failure 不调云端`() = runBlocking {
        val client = FakeDeviceCloudClient()
        val tokenStore = InMemoryTokenStore()
        val orchestrator = newOrchestrator(client, tokenStore)

        val result = orchestrator.cancelTask("user aborted")
        assertTrue("无任务时 cancel 应失败", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, client.cancelCallCount.get())
    }

    @Test
    fun `cancelTask 有当前任务 - 调 cloudClient cancelTask 并返回 cancelled=true`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
            nextCancel = Result.success(CancelTaskResponse(code = 0, data = true))
        }
        val executor = BlockingTaskExecutor()  // 挂起，让任务挂在 EXECUTING
        val orchestrator = newOrchestrator(client, tokenStore, executor = executor)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        // 异步触发任务执行（在 EXECUTING 中保持）
        val executionJob = testScope.launch {
            orchestrator.onPendingTasksAvailable(listOf(PendingTaskItem(taskUuid = "uuid-1", command = "long task", createdAt = 1L)))
        }
        // 给任务执行器一点时间进入 EXECUTING
        Thread.sleep(50)

        val result = orchestrator.cancelTask("user clicked stop")
        // 释放挂起的 execute()，让 executionJob 完成
        executor.release()
        executionJob.join()

        assertTrue("cancelTask 网络层成功时返回 success", result.isSuccess)
        assertEquals(true, result.getOrNull())
        assertEquals(1, client.cancelCallCount.get())
    }

    @Test
    fun `cancelTask data=false 表示任务已是终态 - 仍 success 但 cancelled=false`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
            nextCancel = Result.success(CancelTaskResponse(code = 0, data = false, msg = "already SUCCESS"))
        }
        val executor = BlockingTaskExecutor()
        val orchestrator = newOrchestrator(client, tokenStore, executor = executor)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        val executionJob = testScope.launch {
            orchestrator.onPendingTasksAvailable(listOf(PendingTaskItem(taskUuid = "uuid-1", command = "long", createdAt = 1L)))
        }
        Thread.sleep(50)

        val result = orchestrator.cancelTask()
        executor.release()
        executionJob.join()

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
    }

    // ── 注册请求体字段 ──

    @Test
    fun `register 请求体字段从 DeviceInfoProvider 取值`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val deviceInfo = FakeDeviceInfoProvider(
            deviceId = "pokeclaw-fixed-id",
            model = "Pixel-7-Test",
            android = "14",
            appVersion = "0.7.0-test",
        )
        val orchestrator = newOrchestrator(client, tokenStore, deviceInfo = deviceInfo)
        orchestrator.start()
        testSchedulerAdvance(orchestrator)

        val captured = client.lastRegisterRequest
        assertNotNull("应捕获到 register 请求", captured)
        assertEquals("pokeclaw-fixed-id", captured!!.deviceId)
        assertEquals("Pixel-7-Test", captured.deviceName)
        assertEquals("Pixel-7-Test", captured.deviceModel)
        assertEquals("14", captured.androidVersion)
        assertEquals("0.7.0-test", captured.appVersion)
    }

    // ── 设备 ID 持久化 ──

    @Test
    fun `loadOrGenerateDeviceId 持久化到 KVUtils 并复用`() = runBlocking {
        val tokenStore = InMemoryTokenStore()
        val client = FakeDeviceCloudClient().apply {
            nextRegister = Result.success(DeviceRegister200Response(code = 0, data = fakeRegisterData()))
        }
        val deviceInfo = FakeDeviceInfoProvider(deviceId = "sticky-id-001")

        val orch1 = newOrchestrator(client, tokenStore, deviceInfo = deviceInfo)
        orch1.start()
        testSchedulerAdvance(orch1)
        val firstDeviceId = orch1.getDeviceId()
        assertEquals("sticky-id-001", firstDeviceId)

        // 验证 KV 里有值（fake provider 第一次返回提供的值，第二次应被 KVUtils 持久化）
        // 第二次 newOrchestrator，loadOrGenerateDeviceId() 会先查 KV，找不到则用 deviceInfo.deviceId
        val orch2 = newOrchestrator(client, tokenStore, deviceInfo = deviceInfo)
        orch2.start()
        testSchedulerAdvance(orch2)
        assertEquals(firstDeviceId, orch2.getDeviceId())
    }

    // ── 心跳连续失败 → ERROR ──

    @Test
    fun `heartbeat 连续失败 3 次 - 进入 ERROR 状态`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val client = FakeDeviceCloudClient().apply {
            heartbeatResult = Result.failure(IllegalStateException("network unreachable"))
        }
        val config = CloudNodeOrchestrator.OrchestratorConfig(
            heartbeatIntervalMs = 1L,  // 1ms 让循环快速推进
            maxConsecutiveHeartbeatFailures = 3,
            flushQueueOnHeartbeat = false,
        )
        val orchestrator = newOrchestrator(client, tokenStore, config = config)
        orchestrator.start()
        // 等心跳循环跑够 3 次失败
        Thread.sleep(100)
        testSchedulerAdvance(orchestrator)

        assertEquals(
            "连续 3 次心跳失败后应进入 ERROR",
            CloudNodeOrchestrator.State.ERROR,
            orchestrator.state,
        )
        assertTrue(
            "心跳调用应至少 3 次",
            client.heartbeatCallCount.get() >= 3,
        )

        orchestrator.stop()
    }

    @Test
    fun `heartbeat 成功时 - 失败计数清零`() = runBlocking {
        val tokenStore = InMemoryTokenStore().apply { saveTokens("t1", "r1", 3600, 0L) }
        val client = FakeDeviceCloudClient().apply {
            heartbeatResult = Result.success(
                DeviceHeartbeat200Response(
                    code = 0,
                    data = io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse(
                        pendingTaskCount = 0,
                        skillVersion = 1,
                        serverTime = 1L,
                    ),
                ),
            )
        }
        val config = CloudNodeOrchestrator.OrchestratorConfig(
            heartbeatIntervalMs = 1L,
            maxConsecutiveHeartbeatFailures = 3,
            flushQueueOnHeartbeat = false,
        )
        val orchestrator = newOrchestrator(client, tokenStore, config = config)
        orchestrator.start()
        Thread.sleep(50)
        testSchedulerAdvance(orchestrator)

        assertEquals(
            "心跳一直成功，状态保持 RUNNING",
            CloudNodeOrchestrator.State.RUNNING,
            orchestrator.state,
        )
        assertTrue(client.heartbeatCallCount.get() > 0)
        orchestrator.stop()
    }

    // ── State 枚举稳定性 ──

    @Test
    fun `State 枚举值集合稳定 - 用于 CloudNodeOrchestrator 状态机契约`() {
        val states = CloudNodeOrchestrator.State.values().toSet()
        assertEquals(
            setOf(
                CloudNodeOrchestrator.State.IDLE,
                CloudNodeOrchestrator.State.REGISTERING,
                CloudNodeOrchestrator.State.RUNNING,
                CloudNodeOrchestrator.State.EXECUTING,
                CloudNodeOrchestrator.State.STOPPED,
                CloudNodeOrchestrator.State.ERROR,
            ),
            states,
        )
    }

    // ── Helpers ──

    private fun newOrchestrator(
        client: DeviceCloudClient,
        tokenStore: CloudDeviceTokenStore,
        executor: CloudTaskExecutor = RecordingTaskExecutor(),
        offlineQueue: OfflineResultQueue = InMemoryOfflineQueue(),
        config: CloudNodeOrchestrator.OrchestratorConfig = CloudNodeOrchestrator.OrchestratorConfig(
            heartbeatIntervalMs = 60_000L,  // 默认 60s 防止测试运行太慢
            maxConsecutiveHeartbeatFailures = 3,
            flushQueueOnHeartbeat = false,
        ),
        deviceInfo: DeviceInfoProvider = FakeDeviceInfoProvider(),
    ): CloudNodeOrchestrator {
        return CloudNodeOrchestrator(
            cloudClient = client,
            tokenStore = tokenStore,
            offlineQueue = offlineQueue,
            taskExecutor = executor,
            config = config,
            scope = testScope,
            deviceInfo = deviceInfo,
        )
    }

    /**
     * 给协程一点时间推进（因为 heartbeatLoop 用 delay，Unconfined 不会真正等）。
     * 仅作为简单的"协程已至少跑过一次"的同步手段；具体验证靠后续断言。
     */
    private fun testSchedulerAdvance(@Suppress("UNUSED_PARAMETER") orch: CloudNodeOrchestrator) {
        Thread.sleep(20)
    }

    private fun fakeRegisterData(): DeviceRegisterResponse = DeviceRegisterResponse(
        deviceToken = "device-tok-${UUID.randomUUID()}",
        refreshToken = "refresh-tok-${UUID.randomUUID()}",
        expiresIn = 3600,
    )

    // ── Fakes（文件级 private 避免命名冲突） ──

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

    private class InMemoryOfflineQueue : OfflineResultQueue {
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
        override fun peekDue(nowMillis: Long, limit: Int): List<QueuedResult> = events.take(limit)
        override fun markSucceeded(requestId: String) { events.removeAll { it.requestId == requestId } }
        override fun markFailed(requestId: String, nowMillis: Long) {}
        override fun size(): Int = events.size
    }

    private class FakeDeviceCloudClient : DeviceCloudClient {
        var nextRegister: Result<DeviceRegister200Response>? = null
        var nextCancel: Result<CancelTaskResponse>? = null
        var heartbeatResult: Result<DeviceHeartbeat200Response> = Result.success(
            DeviceHeartbeat200Response(
                code = 0,
                data = io.agents.pokeclaw.cloud.model.DeviceHeartbeatResponse(
                    pendingTaskCount = 0,
                    skillVersion = 1,
                    serverTime = 1L,
                ),
            ),
        )
        var pendingTasksResult: Result<GetPendingTasks200Response> = Result.success(
            GetPendingTasks200Response(code = 0, data = emptyList()),
        )

        val registerCallCount = AtomicInteger(0)
        val heartbeatCallCount = AtomicInteger(0)
        val cancelCallCount = AtomicInteger(0)
        var lastRegisterRequest: DeviceRegisterRequest? = null

        override suspend fun register(request: DeviceRegisterRequest): Result<DeviceRegister200Response> {
            registerCallCount.incrementAndGet()
            lastRegisterRequest = request
            return nextRegister ?: Result.failure(IllegalStateException("no nextRegister stub"))
        }

        override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Result<DeviceHeartbeat200Response> {
            heartbeatCallCount.incrementAndGet()
            return heartbeatResult
        }

        override suspend fun getPendingTasks(deviceId: String): Result<GetPendingTasks200Response> = pendingTasksResult

        override suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Result<SubmitTaskResult200Response> =
            Result.success(SubmitTaskResult200Response(code = 0, data = io.agents.pokeclaw.cloud.model.SubmitTaskResult200ResponseData()))

        override suspend fun refreshToken(request: TokenRefreshRequest): Result<RefreshDeviceToken200Response> =
            Result.success(RefreshDeviceToken200Response(code = 0, data = TokenRefreshResponse(deviceToken = "x", expiresIn = 3600)))

        override suspend fun getTaskByUuid(taskUuid: String): Result<io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse> =
            Result.success(io.agents.pokeclaw.cloud.model.GetTaskByUuidResponse(code = 0, data = null))

        override suspend fun cancelTask(taskUuid: String, request: TaskResultRequest): Result<CancelTaskResponse> {
            cancelCallCount.incrementAndGet()
            return nextCancel ?: Result.success(CancelTaskResponse(code = 0, data = true))
        }
    }

    /**
     * 顺序执行器：记录所有被 execute 的任务，返回预设结果。
     */
    private class RecordingTaskExecutor : CloudTaskExecutor {
        var nextResult: CloudTaskExecutionResult = CloudTaskExecutionResult.success("ok")
        val executedTasks = mutableListOf<PendingTaskItem>()
        override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
            executedTasks.add(task)
            return nextResult
        }
        override fun getModelName(): String = "test-recorder"
    }

    /**
     * 阻塞执行器（suspend 形式）：用于测试 cancelTask 在 EXECUTING 中调用的场景。
     * execute() 挂起等待 releaseSignal 完成，**不占用线程**（用 CompletableDeferred），
     * 避免阻塞 Dispatchers.IO 的工作线程。
     */
    private class BlockingTaskExecutor : CloudTaskExecutor {
        private val releaseSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
        val executedTasks = mutableListOf<PendingTaskItem>()
        var released = false
        override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
            executedTasks.add(task)
            releaseSignal.await()
            released = true
            return CloudTaskExecutionResult.success("ok after release")
        }
        fun release() { releaseSignal.complete(Unit) }
        override fun getModelName(): String = "test-blocker"
    }

    /**
     * 全可控的 DeviceInfoProvider：测试用，绕过 Android framework。
     */
    private class FakeDeviceInfoProvider(
        private val deviceId: String = "pokeclaw-test-${UUID.randomUUID()}",
        private val model: String = "TestModel",
        private val android: String = "14",
        private val appVersion: String = "0.7.0-test",
    ) : DeviceInfoProvider {
        private var generatedId: String? = null
        override fun loadOrGenerateDeviceId(): String {
            val existing = KVUtils.getString(AndroidDeviceInfoProvider.KEY_DEVICE_ID)
            if (!existing.isNullOrBlank()) return existing
            // 第一次：返回构造时给定的 deviceId，并把它持久化
            KVUtils.putString(AndroidDeviceInfoProvider.KEY_DEVICE_ID, deviceId)
            generatedId = deviceId
            return deviceId
        }
        override fun readBatteryInfo(): Pair<Int?, Boolean?> = 80 to false
        override fun readNetworkType(): NetworkType = NetworkType.WIFI
        override fun deviceModel(): String = model
        override fun androidVersion(): String = android
        override fun appVersion(): String = appVersion
    }
}
