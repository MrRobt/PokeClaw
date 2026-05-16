package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenStore
import io.agents.pokeclaw.cloud.auth.CloudDeviceTokenSnapshot
import io.agents.pokeclaw.cloud.model.DeviceHeartbeatRequest
import io.agents.pokeclaw.cloud.model.DeviceRegisterRequest
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskResultRequest
import io.agents.pokeclaw.cloud.model.TaskStatus
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.cloudnode.CloudExecutorClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CloudNodeOrchestrator 单元测试。
 *
 * 验证编排器的注册、心跳、任务拉取、执行、结果上报闭环。
 * 使用 TestScope 和 StandardTestDispatcher 控制协程时间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudNodeOrchestratorTest {

    private lateinit var mockCloudClient: MockCloudClient
    private lateinit var mockTokenStore: MockTokenStore
    private lateinit var mockExecutor: MockCloudTaskExecutor
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        mockCloudClient = MockCloudClient()
        mockTokenStore = MockTokenStore()
        mockExecutor = MockCloudTaskExecutor()
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
    }

    @Test
    fun `注册成功后编排器进入运行状态`() = testScope.runTest {
        mockCloudClient.registerResult = true

        val orchestrator = createOrchestrator(autoRegister = true)
        orchestrator.start()
        advanceUntilIdle()

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertNotNull(orchestrator.getDeviceId())
    }

    @Test
    fun `注册失败后编排器进入错误状态`() = testScope.runTest {
        mockCloudClient.registerResult = false

        val orchestrator = createOrchestrator(autoRegister = true)
        orchestrator.start()
        advanceUntilIdle()

        assertEquals(CloudNodeOrchestrator.State.ERROR, orchestrator.state)
    }

    @Test
    fun `已有令牌时跳过注册直接进入运行状态`() = testScope.runTest {
        mockTokenStore.hasToken = true

        val orchestrator = createOrchestrator(autoRegister = true)
        orchestrator.start()
        advanceUntilIdle()

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertEquals(0, mockCloudClient.registerCallCount)
    }

    @Test
    fun `心跳成功时编排器保持运行状态`() = testScope.runTest {
        mockTokenStore.hasToken = true
        mockCloudClient.heartbeatResult = true

        val orchestrator = createOrchestrator(autoRegister = false)
        orchestrator.start()
        advanceUntilIdle()

        assertEquals(CloudNodeOrchestrator.State.RUNNING, orchestrator.state)
        assertTrue(mockCloudClient.heartbeatCallCount >= 1)
    }

    @Test
    fun `连续心跳失败达到上限时进入错误状态`() = testScope.runTest {
        mockTokenStore.hasToken = true
        mockCloudClient.heartbeatResult = false

        val config = CloudNodeOrchestrator.OrchestratorConfig(
            heartbeatIntervalMs = 100L,
            maxConsecutiveHeartbeatFailures = 2,
            autoRegisterOnStart = false,
        )
        val orchestrator = createOrchestrator(config = config)
        orchestrator.start()
        advanceUntilIdle()

        assertEquals(CloudNodeOrchestrator.State.ERROR, orchestrator.state)
    }

    @Test
    fun `收到待处理任务时执行并上报结果`() = testScope.runTest {
        mockTokenStore.hasToken = true
        mockCloudClient.heartbeatResult = true
        mockExecutor.result = CloudTaskExecutionResult.success("已打开设置")

        val task = PendingTaskItem(
            taskUuid = "task-001",
            command = "打开设置",
            mode = "TASK",
            createdAt = 1000L,
            priority = "normal",
        )
        mockCloudClient.pendingTasks = listOf(task)

        val orchestrator = createOrchestrator(autoRegister = false)
        orchestrator.start()
        advanceUntilIdle()

        assertTrue(mockExecutor.executeCallCount >= 1)
        assertEquals("task-001", mockExecutor.lastExecutedTask?.taskUuid)
        assertTrue(mockCloudClient.submitResultCallCount >= 1)
        assertEquals(TaskStatus.SUCCESS.value, mockCloudClient.lastSubmittedResult?.status)
    }

    @Test
    fun `执行失败的任务会上报失败状态和错误信息`() = testScope.runTest {
        mockTokenStore.hasToken = true
        mockCloudClient.heartbeatResult = true
        mockExecutor.result = CloudTaskExecutionResult.failure(
            message = "无障碍服务未启用",
            errorCode = CloudTaskErrorCode.PERMISSION_MISSING,
            retryable = true,
        )

        val task = PendingTaskItem(
            taskUuid = "task-002",
            command = "点击按钮",
            mode = "TASK",
            createdAt = 2000L,
            priority = "normal",
        )
        mockCloudClient.pendingTasks = listOf(task)

        val orchestrator = createOrchestrator(autoRegister = false)
        orchestrator.start()
        advanceUntilIdle()

        assertTrue(mockCloudClient.submitResultCallCount >= 1)
        assertEquals(TaskStatus.FAILED.value, mockCloudClient.lastSubmittedResult?.status)
        assertEquals("无障碍服务未启用", mockCloudClient.lastSubmittedResult?.errorMessage)
    }

    @Test
    fun `停止编排器后状态为已停止`() = testScope.runTest {
        mockTokenStore.hasToken = true
        mockCloudClient.heartbeatResult = true

        val orchestrator = createOrchestrator(autoRegister = false)
        orchestrator.start()
        advanceUntilIdle()

        orchestrator.stop()
        assertEquals(CloudNodeOrchestrator.State.STOPPED, orchestrator.state)
    }

    // ── 辅助方法 ──

    private fun createOrchestrator(
        autoRegister: Boolean = true,
        config: CloudNodeOrchestrator.OrchestratorConfig? = null,
    ): CloudNodeOrchestrator {
        val actualConfig = config ?: CloudNodeOrchestrator.OrchestratorConfig(
            heartbeatIntervalMs = 100L,
            maxConsecutiveHeartbeatFailures = 3,
            flushQueueOnHeartbeat = false,
            autoRegisterOnStart = autoRegister,
        )
        return CloudNodeOrchestrator(
            context = MockContext(),
            cloudClient = mockCloudClient,
            tokenStore = mockTokenStore,
            offlineQueue = MockOfflineQueue(),
            taskExecutor = mockExecutor,
            config = actualConfig,
            clock = TestClock(),
            scope = testScope,
        )
    }

    // ── Mock 实现 ──

    private class MockCloudClient : DeviceCloudClient {
        var registerResult = false
        var registerCallCount = 0
        var heartbeatResult = false
        var heartbeatCallCount = 0
        var pendingTasks: List<PendingTaskItem> = emptyList()
        var submitResultCallCount = 0
        var lastSubmittedResult: TaskResultRequest? = null

        override suspend fun register(request: DeviceRegisterRequest): Boolean {
            registerCallCount++
            return registerResult
        }

        override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): Boolean {
            heartbeatCallCount++
            return heartbeatResult
        }

        override suspend fun getPendingTasks(deviceId: String): List<PendingTaskItem> {
            return pendingTasks
        }

        override suspend fun submitTaskResult(taskUuid: String, request: TaskResultRequest): Boolean {
            submitResultCallCount++
            lastSubmittedResult = request
            return true
        }

        override suspend fun refreshTokenIfNeeded(nowMillis: Long): Boolean = true
        override suspend fun flushOfflineQueue(nowMillis: Long) {}
    }

    private class MockTokenStore : CloudDeviceTokenStore {
        var hasToken = false
        override fun saveTokens(deviceToken: String, refreshToken: String, expiresInSeconds: Int, nowMillis: Long) {
            hasToken = true
        }
        override fun updateDeviceToken(deviceToken: String, expiresInSeconds: Int, nowMillis: Long) {}
        override fun snapshot(): CloudDeviceTokenSnapshot? = if (hasToken) {
            CloudDeviceTokenSnapshot("mock-device-token", "mock-refresh-token", System.currentTimeMillis() + 3600_000)
        } else null
        override fun clear() { hasToken = false }
    }

    private class MockOfflineQueue : CloudEventQueue(
        context = MockContext(),
    ) {
        override fun size(): Int = 0
    }

    private class MockCloudTaskExecutor : CloudTaskExecutor {
        var result: CloudTaskExecutionResult = CloudTaskExecutionResult.success("默认成功")
        var executeCallCount = 0
        var lastExecutedTask: PendingTaskItem? = null

        override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
            executeCallCount++
            lastExecutedTask = task
            return result
        }

        override fun getModelName(): String = "mock-model"
    }

    /** 最小化 Android Context 子类，仅提供 registerReceiver 等方法需要的空壳。 */
    private class MockContext : android.test.mock.MockContext() {
        override fun getApplicationContext(): android.content.Context = this
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
            return MockSharedPreferences()
        }
    }

    private class MockSharedPreferences : android.content.SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun edit(): android.content.SharedPreferences.Editor = MockEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    }

    private class MockEditor(private val data: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun remove(key: String?): android.content.SharedPreferences.Editor { data.remove(key); return this }
        override fun clear(): android.content.SharedPreferences.Editor { data.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { return this }
        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { return this }
    }

    private class TestClock : CloudExecutorClock {
        private var current = 1000L
        override fun nowMillis(): Long = current++
    }
}
