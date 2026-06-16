package io.agents.pokeclaw

import io.agents.pokeclaw.agent.TaskClassifier
import io.agents.pokeclaw.agent.TaskParser
import io.agents.pokeclaw.cloud.model.*
import io.agents.pokeclaw.mock.MockDataProvider
import io.agents.pokeclaw.mock.MockDeviceCloudClient
import io.agents.pokeclaw.task.TaskWhitelist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 核心业务逻辑测试
 * 使用 Mock 数据验证各模块业务逻辑的正确性
 */
class BusinessLogicTest {

    private lateinit var mockCloudClient: MockDeviceCloudClient

    @Before
    fun setup() {
        mockCloudClient = MockDeviceCloudClient()
    }

    // ============ 任务解析业务逻辑测试 ============

    @Test
    fun `任务解析 - 应正确解析打电话任务`() {
        val command = "call 1234567890"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为打电话", "call", parsed?.action)
        assertNotNull("应有Intent", parsed?.intent)
    }

    @Test
    fun `任务解析 - 应正确解析发送消息任务`() {
        val command = "send Hello to John"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为发送消息", "send_message", parsed?.action)
        assertEquals("应使用send_message工具", "send_message", parsed?.toolName)
        assertEquals("联系人应正确", "John", parsed?.toolParams?.get("contact"))
        assertEquals("消息应正确", "Hello", parsed?.toolParams?.get("message"))
    }

    @Test
    fun `任务解析 - 应正确解析设置闹钟任务`() {
        val command = "set alarm at 8:30 am"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为设置闹钟", "alarm", parsed?.action)
        assertNotNull("应有Intent", parsed?.intent)
    }

    @Test
    fun `任务解析 - 应正确解析设置计时器任务`() {
        val command = "set timer for 5 minutes"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为设置计时器", "timer", parsed?.action)
        assertNotNull("应有Intent", parsed?.intent)
    }

    @Test
    fun `任务解析 - 应正确解析截图任务`() {
        val command = "take a screenshot"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为截图", "screenshot", parsed?.action)
        assertEquals("应使用take_screenshot工具", "take_screenshot", parsed?.toolName)
    }

    @Test
    fun `任务解析 - 应正确解析返回操作`() {
        val command = "go back"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为返回", "back", parsed?.action)
    }

    @Test
    fun `任务解析 - 应正确解析打开应用任务`() {
        val command = "open YouTube"
        val parsed = TaskParser.parse(command)

        assertNotNull("解析结果不应为空", parsed)
        assertEquals("应识别为打开应用", "open_app", parsed?.action)
        assertEquals("应使用open_app工具", "open_app", parsed?.toolName)
        assertEquals("应用名应正确", "youtube", parsed?.toolParams?.get("app_name"))
    }

    @Test
    fun `任务解析 - 无法识别的任务应返回null`() {
        val command = "some random unrecognized task that doesn't match any pattern"
        val parsed = TaskParser.parse(command)

        assertNull("无法识别的任务应返回null", parsed)
    }

    // ============ 任务分类业务逻辑测试 ============

    @Test
    fun `任务分类 - 应正确构建分类提示词`() {
        val skillSummaries = listOf(
            "search: 搜索应用内容",
            "send_message: 发送消息"
        )
        val prompt = TaskClassifier.buildClassifierPrompt(skillSummaries)

        assertTrue("提示词应包含类型说明", prompt.contains("Types:"))
        assertTrue("提示词应包含 skill 列表", prompt.contains("search"))
        assertTrue("提示词应包含输出格式说明", prompt.contains("Output format:"))
    }

    @Test
    fun `任务分类 - 应正确解析LLM响应`() {
        val jsonResponse = """{"type": "agent", "app": "WeChat", "sub_goal": "发送消息给张三"}"""
        val classification = TaskClassifier.parseResponse(jsonResponse)

        assertEquals("类型应为 agent", "agent", classification.type)
        assertEquals("应用应为 WeChat", "WeChat", classification.app)
        assertEquals("子目标应正确", "发送消息给张三", classification.subGoal)
    }

    @Test
    fun `任务分类 - 应处理Markdown代码块包装`() {
        val markdownResponse = """
            ```json
            {"type": "skill", "skill_id": "search_in_app", "params": {"query": "餐厅"}}
            ```
        """.trimIndent()
        val classification = TaskClassifier.parseResponse(markdownResponse)

        assertEquals("类型应为 skill", "skill", classification.type)
        assertEquals("skill_id 应正确", "search_in_app", classification.skillId)
    }

    @Test
    fun `任务分类 - 解析失败时应返回默认值`() {
        val invalidResponse = "无效的JSON"
        val classification = TaskClassifier.parseResponse(invalidResponse)

        assertEquals("类型应为 agent", "agent", classification.type)
        assertEquals("sub_goal 应为原始响应", "无效的JSON", classification.subGoal)
    }

    // ============ 任务白名单业务逻辑测试 ============

    @Test
    fun `任务白名单 - P0低风险任务应在白名单中`() {
        assertTrue("查询电量应在白名单中", TaskWhitelist.isWhitelisted("query_battery_level"))
        assertTrue("查询存储空间应在白名单中", TaskWhitelist.isWhitelisted("query_storage_space"))
        assertTrue("查询网络状态应在白名单中", TaskWhitelist.isWhitelisted("query_network_status"))
    }

    @Test
    fun `任务白名单 - P1中等风险任务应在白名单中`() {
        assertTrue("滑动屏幕应在白名单中", TaskWhitelist.isWhitelisted("ui_swipe"))
        assertTrue("点击坐标应在白名单中", TaskWhitelist.isWhitelisted("ui_tap"))
        assertTrue("启动应用应在白名单中", TaskWhitelist.isWhitelisted("app_launch"))
    }

    @Test
    fun `任务白名单 - 危险任务应在禁止列表中`() {
        assertTrue("支付确认应在禁止列表中", TaskWhitelist.isBlocked("payment_confirm"))
        assertTrue("读取短信应在禁止列表中", TaskWhitelist.isBlocked("read_sms"))
        assertTrue("恢复出厂设置应在禁止列表中", TaskWhitelist.isBlocked("factory_reset"))
    }

    @Test
    fun `任务白名单 - 不在白名单的任务应被拒绝`() {
        assertFalse("未知任务不应在白名单中", TaskWhitelist.isWhitelisted("unknown_action"))
    }

    @Test
    fun `任务白名单 - 验证任务应正确返回结果`() {
        val (canExecute1, reason1) = TaskWhitelist.validateTask("query_battery_level")
        assertTrue("白名单任务应可执行", canExecute1)
        assertEquals("原因应表示验证通过", "任务验证通过", reason1)

        val (canExecute2, reason2) = TaskWhitelist.validateTask("payment_confirm")
        assertFalse("禁止任务应不可执行", canExecute2)
        assertTrue("原因应包含禁止信息", reason2.contains("禁止列表"))

        val (canExecute3, reason3) = TaskWhitelist.validateTask("unknown_action")
        assertFalse("未知任务应不可执行", canExecute3)
        assertTrue("原因应包含白名单信息", reason3.contains("白名单"))
    }

    @Test
    fun `任务白名单 - 获取任务定义应返回正确信息`() {
        val taskDef = TaskWhitelist.getTaskDefinition("ui_tap")
        assertNotNull("任务定义不应为空", taskDef)
        assertEquals("风险等级应为 MEDIUM", TaskWhitelist.RiskLevel.MEDIUM, taskDef?.riskLevel)
        assertTrue("应需要用户确认", taskDef?.requiresConfirmation == true)
        assertEquals("应有正确参数", listOf("x", "y"), taskDef?.parameters)
    }

    @Test
    fun `任务白名单 - 获取所有任务应包含各等级任务`() {
        val allTasks = TaskWhitelist.getAllWhitelistedTasks()
        val p0Count = TaskWhitelist.P0_TASKS.size
        val p1Count = TaskWhitelist.P1_TASKS.size
        val p2Count = TaskWhitelist.P2_TASKS.size
        val p3Count = TaskWhitelist.P3_TASKS.size

        assertEquals("总任务数应等于各等级之和", p0Count + p1Count + p2Count + p3Count, allTasks.size)
    }

    // ============ 云客户端业务逻辑测试 ============

    @Test
    fun `云客户端 - 设备注册应成功并返回Token`() = runBlocking {
        val request = MockDataProvider.createMockDeviceRegisterRequest(
            deviceId = "test-device-001",
            deviceName = "Test Device"
        )

        val result = mockCloudClient.register(request)

        assertTrue("注册应成功", result.isSuccess)
        assertNotNull("应返回 Token", result.getOrNull()?.data?.deviceToken)
        assertEquals("设备ID应匹配", "test-device-001", result.getOrNull()?.data?.deviceId)
    }

    @Test
    fun `云客户端 - 网络错误时应返回失败`() = runBlocking {
        mockCloudClient.shouldSimulateNetworkError = true

        val request = MockDataProvider.createMockDeviceRegisterRequest(
            deviceId = "test-device-001"
        )

        val result = mockCloudClient.register(request)

        assertTrue("网络错误时应失败", result.isFailure)
        assertTrue("错误信息应包含网络", result.exceptionOrNull()?.message?.contains("Network") == true)
    }

    @Test
    fun `云客户端 - 心跳应返回待处理任务数量`() = runBlocking {
        // 添加一些待处理任务
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(command = "任务1")
        )
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(command = "任务2")
        )

        val request = MockDataProvider.createMockDeviceHeartbeatRequest()

        val result = mockCloudClient.sendHeartbeat(request)

        assertTrue("心跳应成功", result.isSuccess)
        assertEquals("应返回正确任务数量", 2, result.getOrNull()?.data?.pendingTaskCount)
    }

    @Test
    fun `云客户端 - 获取待处理任务应返回所有待处理任务`() = runBlocking {
        // 添加多个任务
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = "task-1", command = "任务1")
        )
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = "task-2", command = "任务2")
        )

        // 提交第一个任务的结果，使其不再是待处理状态
        mockCloudClient.submitTaskResult(
            "task-1",
            MockDataProvider.createMockTaskResultRequest(status = TaskResultRequest.Status.SUCCESS)
        )

        val result = mockCloudClient.getPendingTasks("test-device-001")

        assertTrue("获取任务应成功", result.isSuccess)
        assertEquals("应只返回待处理任务", 1, result.getOrNull()?.data?.size)
    }

    @Test
    fun `云客户端 - 提交任务结果应更新任务状态和结果`() = runBlocking {
        val taskUuid = "task-001"
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = taskUuid, command = "测试任务")
        )

        val resultRequest = MockDataProvider.createMockTaskResultRequest(
            status = TaskResultRequest.Status.SUCCESS,
            result = "任务执行成功"
        )

        val result = mockCloudClient.submitTaskResult(taskUuid, resultRequest)

        assertTrue("提交结果应成功", result.isSuccess)

        val storedResult = mockCloudClient.getTaskResult(taskUuid)
        assertEquals("任务状态应更新为成功", TaskResultRequest.Status.SUCCESS, storedResult?.status)
        assertEquals("任务结果应被保存", "任务执行成功", storedResult?.result)
    }

    @Test
    fun `云客户端 - 按UUID查询任务应返回正确信息`() = runBlocking {
        val taskUuid = "task-query-001"
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(
                taskUuid = taskUuid,
                command = "查询测试任务"
            )
        )

        // 先查询未完成的任务
        val pendingResult = mockCloudClient.getTaskByUuid(taskUuid)
        assertTrue("查询应成功", pendingResult.isSuccess)
        assertEquals("状态应为PENDING", TaskStatus.PENDING.value, pendingResult.getOrNull()?.data?.status?.value)

        // 提交结果
        mockCloudClient.submitTaskResult(
            taskUuid,
            MockDataProvider.createMockTaskResultRequest(
                status = TaskResultRequest.Status.SUCCESS,
                result = "已完成"
            )
        )

        // 再次查询
        val completedResult = mockCloudClient.getTaskByUuid(taskUuid)
        assertEquals("状态应为SUCCESS", TaskStatus.SUCCESS.value, completedResult.getOrNull()?.data?.status?.value)
        assertEquals("结果应正确", "已完成", completedResult.getOrNull()?.data?.result)
    }

    @Test
    fun `云客户端 - 取消任务应成功`() = runBlocking {
        val taskUuid = "task-cancel-001"
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = taskUuid, command = "待取消任务")
        )

        val cancelRequest = MockDataProvider.createMockTaskResultRequest(
            status = TaskResultRequest.Status.CANCELLED,
            errorMessage = "用户取消"
        )

        val result = mockCloudClient.cancelTask(taskUuid, cancelRequest)

        assertTrue("取消应成功", result.isSuccess)
        assertTrue("应返回取消成功", result.getOrNull()?.cancelled() == true)
    }

    // ============ 场景化集成测试 ============

    @Test
    fun `完整任务流程 - 从接收到完成的端到端流程`() = runBlocking {
        // 1. 注册设备
        val registerResult = mockCloudClient.register(
            MockDataProvider.createMockDeviceRegisterRequest(deviceId = "device-001")
        )
        assertTrue("设备注册应成功", registerResult.isSuccess)

        // 2. 添加待处理任务
        val scenario = MockDataProvider.createTaskExecutionScenario()
        mockCloudClient.addPendingTask(scenario.pendingTask)

        // 3. 发送心跳获取任务数
        val heartbeatResult = mockCloudClient.sendHeartbeat(
            MockDataProvider.createMockDeviceHeartbeatRequest()
        )
        assertTrue("心跳应成功", heartbeatResult.isSuccess)
        assertEquals("应有1个待处理任务", 1, heartbeatResult.getOrNull()?.data?.pendingTaskCount)

        // 4. 获取待处理任务
        val tasksResult = mockCloudClient.getPendingTasks("device-001")
        assertTrue("获取任务应成功", tasksResult.isSuccess)
        val tasks = tasksResult.getOrNull()?.data ?: emptyList()
        assertEquals("应返回1个任务", 1, tasks.size)

        val taskUuid = tasks[0].taskUuid

        // 5. 提交成功结果
        val submitResult = mockCloudClient.submitTaskResult(taskUuid, scenario.successResult)
        assertTrue("提交结果应成功", submitResult.isSuccess)

        // 6. 验证任务状态
        val taskQueryResult = mockCloudClient.getTaskByUuid(taskUuid)
        assertEquals("任务应标记为成功", TaskStatus.SUCCESS.value, taskQueryResult.getOrNull()?.data?.status?.value)
    }

    @Test
    fun `错误恢复流程 - 网络失败后重试`() = runBlocking {
        val taskUuid = "task-error-001"
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = taskUuid, command = "错误恢复测试")
        )

        val resultRequest = MockDataProvider.createMockTaskResultRequest(
            status = TaskResultRequest.Status.SUCCESS,
            result = "结果"
        )

        // 第一次提交 - 模拟网络错误
        mockCloudClient.shouldSimulateNetworkError = true
        val firstResult = mockCloudClient.submitTaskResult(taskUuid, resultRequest)
        assertTrue("第一次应失败", firstResult.isFailure)

        // 恢复网络，重试
        mockCloudClient.shouldSimulateNetworkError = false
        val retryResult = mockCloudClient.submitTaskResult(taskUuid, resultRequest)
        assertTrue("重试应成功", retryResult.isSuccess)
    }

    @Test
    fun `任务状态机 - 状态转换应符合预期`() = runBlocking {
        val taskUuid = "state-machine-task"

        // 初始状态为 PENDING（通过 addPendingTask）
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = taskUuid, command = "状态机测试")
        )

        val pendingQuery = mockCloudClient.getTaskByUuid(taskUuid)
        assertEquals("PENDING", TaskStatus.PENDING.value, pendingQuery.getOrNull()?.data?.status?.value)

        // 提交 RUNNING 状态
        mockCloudClient.submitTaskResult(
            taskUuid,
            MockDataProvider.createMockTaskResultRequest(status = TaskResultRequest.Status.RUNNING)
        )
        val runningQuery = mockCloudClient.getTaskByUuid(taskUuid)
        assertEquals("RUNNING", TaskStatus.RUNNING.value, runningQuery.getOrNull()?.data?.status?.value)

        // 提交 SUCCESS 状态
        mockCloudClient.submitTaskResult(
            taskUuid,
            MockDataProvider.createMockTaskResultRequest(status = TaskResultRequest.Status.SUCCESS)
        )
        val successQuery = mockCloudClient.getTaskByUuid(taskUuid)
        assertEquals("SUCCESS", TaskStatus.SUCCESS.value, successQuery.getOrNull()?.data?.status?.value)

        // 测试 FAILED 状态
        val taskUuid2 = "state-machine-task-2"
        mockCloudClient.addPendingTask(
            MockDataProvider.createMockPendingTaskItem(taskUuid = taskUuid2, command = "状态机测试2")
        )
        mockCloudClient.submitTaskResult(
            taskUuid2,
            MockDataProvider.createMockTaskResultRequest(
                status = TaskResultRequest.Status.FAILED,
                errorMessage = "执行超时",
                errorCategory = "TIMEOUT"
            )
        )
        val failedQuery = mockCloudClient.getTaskByUuid(taskUuid2)
        assertEquals("FAILED", TaskStatus.FAILED.value, failedQuery.getOrNull()?.data?.status?.value)
    }

    // ============ 并发测试 ============

    @Test
    fun `并发场景 - 同时处理多个任务`() = runBlocking {
        // 添加10个任务
        repeat(10) { index ->
            mockCloudClient.addPendingTask(
                MockDataProvider.createMockPendingTaskItem(
                    taskUuid = "concurrent-task-$index",
                    command = "并发任务 ${index + 1}"
                )
            )
        }

        // 获取所有任务
        val result = mockCloudClient.getPendingTasks("device-001")
        assertTrue("获取任务应成功", result.isSuccess)
        assertEquals("应有10个任务", 10, result.getOrNull()?.data?.size)

        // 提交5个任务的结果
        repeat(5) { index ->
            mockCloudClient.submitTaskResult(
                "concurrent-task-$index",
                MockDataProvider.createMockTaskResultRequest(status = TaskResultRequest.Status.SUCCESS)
            )
        }

        // 验证剩余待处理任务数
        val pendingCount = mockCloudClient.getPendingTaskCount()
        assertEquals("应有5个待处理任务", 5, pendingCount)
    }

    // ============ 时钟偏移测试 ============

    @Test
    fun `时钟偏移 - 服务器时间偏移应正确返回`() = runBlocking {
        mockCloudClient.serverTimeOffsetMs = 5000L  // 5秒偏移

        val request = MockDataProvider.createMockDeviceHeartbeatRequest()
        val result = mockCloudClient.sendHeartbeat(request)

        assertTrue("心跳应成功", result.isSuccess)
        val serverTime = result.getOrNull()?.data?.serverTime ?: 0
        val expectedTime = System.currentTimeMillis() + 5000
        assertTrue("服务器时间应包含偏移", serverTime >= expectedTime - 1000)
    }

    // ============ 认证错误测试 ============

    @Test
    fun `认证错误 - Token刷新失败时应返回错误`() = runBlocking {
        mockCloudClient.shouldSimulateAuthError = true

        val request = MockDataProvider.createMockTokenRefreshRequest()
        val result = mockCloudClient.refreshToken(request)

        assertTrue("认证错误时应失败", result.isFailure)
        assertTrue("错误信息应包含Auth", result.exceptionOrNull()?.message?.contains("Auth") == true)
    }
}
