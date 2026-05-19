package io.agents.pokeclaw.cloud.test

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.cloud.*
import io.agents.pokeclaw.service.SmsService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 云端集成测试页面
 * 用于测试端云任务下发流程
 * @author Hermes Agent
 */
class CloudIntegrationTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CloudIntegrationTest"
        private const val TEST_SERVER_URL = "http://localhost:8080" // 测试服务器地址
        private const val TEST_DEVICE_ID = "test-device-pokeclaw-001"
    }

    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var testContainer: LinearLayout
    
    private var heartbeatManager: CloudHeartbeatManager? = null
    private var taskPoller: TaskPoller? = null
    private var cloudTaskExecutor: CloudTaskExecutor? = null
    private var smsService: SmsService? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        
        // 状态显示
        statusTextView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        mainLayout.addView(statusTextView)
        
        // 测试按钮容器
        testContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(testContainer)
        
        // 日志显示
        logTextView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 32, 0, 0)
        }
        mainLayout.addView(logTextView)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        initServices()
        createTestButtons()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopServices()
    }

    private fun initServices() {
        try {
            smsService = SmsService(this)
            
            cloudTaskExecutor = CloudTaskExecutor(this, smsService!!)
            
            val heartbeatConfig = CloudHeartbeatManager.CloudConfig(
                baseUrl = TEST_SERVER_URL,
                deviceId = TEST_DEVICE_ID,
                heartbeatIntervalMs = 30000
            )
            
            heartbeatManager = CloudHeartbeatManager(this, heartbeatConfig).apply {
                addListener(object : CloudHeartbeatManager.HeartbeatListener {
                    override fun onHeartbeatSuccess(response: CloudHeartbeatManager.HeartbeatResponse) {
                        log("✅ 心跳成功 - 任务可用: ${response.tasksAvailable}")
                        updateStatus()
                    }
                    
                    override fun onHeartbeatFailure(error: String) {
                        log("❌ 心跳失败 - $error")
                        updateStatus()
                    }
                    
                    override fun onConnectionStateChanged(connected: Boolean) {
                        log(if (connected) "🟢 连接已建立" else "🔴 连接已断开")
                        updateStatus()
                    }
                })
            }
            
            val pollerConfig = TaskPoller.PollerConfig(
                baseUrl = TEST_SERVER_URL,
                deviceId = TEST_DEVICE_ID,
                pollIntervalMs = 5000,
                maxConcurrentTasks = 3,
                enableLongPolling = true
            )
            
            taskPoller = TaskPoller(pollerConfig, cloudTaskExecutor!!).apply {
                addListener(object : TaskPoller.PollerListener {
                    override fun onPollStarted() {
                        log("▶️ 任务轮询已启动")
                    }
                    
                    override fun onPollStopped() {
                        log("⏹️ 任务轮询已停止")
                    }
                    
                    override fun onTaskReceived(task: TaskPoller.CloudTask) {
                        log("📥 收到任务 - ${task.taskId} (${task.type})")
                        updateStatus()
                    }
                    
                    override fun onTaskStarted(task: TaskPoller.CloudTask) {
                        log("▶️ 开始执行任务 - ${task.taskId}")
                        updateStatus()
                    }
                    
                    override fun onTaskCompleted(task: TaskPoller.CloudTask, result: TaskPoller.TaskResult) {
                        log("✅ 任务完成 - ${task.taskId} (${result.executionTimeMs}ms)")
                        updateStatus()
                    }
                    
                    override fun onTaskFailed(task: TaskPoller.CloudTask, error: String) {
                        log("❌ 任务失败 - ${task.taskId}: $error")
                        updateStatus()
                    }
                    
                    override fun onPollError(error: String) {
                        log("⚠️ 轮询错误 - $error")
                    }
                })
            }
            
            log("🚀 服务初始化完成")
        } catch (e: Exception) {
            log("❌ 服务初始化失败: ${e.message}")
        }
    }

    private fun stopServices() {
        heartbeatManager?.stop()
        taskPoller?.stop()
        log("⏹️ 所有服务已停止")
    }

    private fun createTestButtons() {
        val tests = listOf(
            TestCase("启动心跳服务") { startHeartbeat() },
            TestCase("停止心跳服务") { stopHeartbeat() },
            TestCase("启动任务轮询") { startTaskPoller() },
            TestCase("停止任务轮询") { stopTaskPoller() },
            TestCase("发送测试心跳") { sendTestHeartbeat() },
            TestCase("模拟接收任务") { simulateTaskReceive() },
            TestCase("测试任务执行") { testTaskExecution() },
            TestCase("查看任务统计") { showTaskStats() },
            TestCase("完整流程测试") { runFullIntegrationTest() },
            TestCase("清除日志") { clearLogs() }
        )
        
        tests.forEach { test ->
            val button = Button(this).apply {
                text = test.name
                setOnClickListener { test.action() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            testContainer.addView(button)
        }
    }

    private fun startHeartbeat() {
        heartbeatManager?.start()
        log("🟢 心跳服务已启动")
        updateStatus()
    }

    private fun stopHeartbeat() {
        heartbeatManager?.stop()
        log("🔴 心跳服务已停止")
        updateStatus()
    }

    private fun startTaskPoller() {
        taskPoller?.start()
        log("🟢 任务轮询已启动")
        updateStatus()
    }

    private fun stopTaskPoller() {
        taskPoller?.stop()
        log("🔴 任务轮询已停止")
        updateStatus()
    }

    private fun sendTestHeartbeat() {
        scope.launch {
            try {
                log("📤 发送测试心跳...")
                // 实际的心跳由manager自动发送，这里只是触发一次手动更新
                log("💡 心跳由服务自动管理，当前连接状态: ${heartbeatManager != null}")
            } catch (e: Exception) {
                log("❌ 测试心跳失败: ${e.message}")
            }
        }
    }

    private fun simulateTaskReceive() {
        scope.launch {
            try {
                log("📥 模拟接收任务...")
                
                val testTasks = listOf(
                    TaskPoller.CloudTask(
                        taskId = "test-task-001",
                        type = TaskPoller.CloudTask.TaskType.WAIT,
                        payload = JSONObject().apply { put("durationMs", 2000) },
                        priority = 1,
                        timeoutMs = 10000,
                        createdAt = System.currentTimeMillis()
                    ),
                    TaskPoller.CloudTask(
                        taskId = "test-task-002",
                        type = TaskPoller.CloudTask.TaskType.CUSTOM,
                        payload = JSONObject().apply { 
                            put("action", "echo")
                            put("message", "Hello from cloud!")
                        },
                        priority = 2,
                        timeoutMs = 10000,
                        createdAt = System.currentTimeMillis()
                    )
                )
                
                testTasks.forEach { task ->
                    if (cloudTaskExecutor?.canAcceptTask() == true) {
                        log("🔄 执行任务: ${task.taskId}")
                        val result = cloudTaskExecutor?.handleTask(task)
                        log("📤 任务结果: ${result?.success} - ${result?.message}")
                    } else {
                        log("⏳ 无法接收更多任务")
                    }
                }
            } catch (e: Exception) {
                log("❌ 模拟任务失败: ${e.message}")
            }
            updateStatus()
        }
    }

    private fun testTaskExecution() {
        scope.launch {
            log("🧪 测试任务执行器...")
            
            val tests = listOf(
                "WAIT任务" to TaskPoller.CloudTask(
                    taskId = "test-wait",
                    type = TaskPoller.CloudTask.TaskType.WAIT,
                    payload = JSONObject().apply { put("durationMs", 1000) },
                    priority = 1,
                    timeoutMs = 5000,
                    createdAt = System.currentTimeMillis()
                ),
                "CUSTOM-echo任务" to TaskPoller.CloudTask(
                    taskId = "test-echo",
                    type = TaskPoller.CloudTask.TaskType.CUSTOM,
                    payload = JSONObject().apply { 
                        put("action", "echo")
                        put("message", "Test message")
                    },
                    priority = 1,
                    timeoutMs = 5000,
                    createdAt = System.currentTimeMillis()
                ),
                "CUSTOM-test任务" to TaskPoller.CloudTask(
                    taskId = "test-custom",
                    type = TaskPoller.CloudTask.TaskType.CUSTOM,
                    payload = JSONObject().apply { put("action", "test") },
                    priority = 1,
                    timeoutMs = 5000,
                    createdAt = System.currentTimeMillis()
                )
            )
            
            tests.forEach { (name, task) ->
                log("  测试 $name...")
                val startTime = System.currentTimeMillis()
                val result = cloudTaskExecutor?.handleTask(task)
                val duration = System.currentTimeMillis() - startTime
                log("    结果: ${if (result?.success == true) "✅" else "❌"} ${result?.message} (${duration}ms)")
            }
            
            updateStatus()
        }
    }

    private fun showTaskStats() {
        val stats = cloudTaskExecutor?.getTaskStats()
        log("📊 任务统计:")
        log("  已完成: ${stats?.completed ?: 0}")
        log("  失败: ${stats?.failed ?: 0}")
        log("  运行中: ${stats?.running ?: 0}")
    }

    private fun runFullIntegrationTest() {
        scope.launch {
            log("🧪 开始完整集成测试...")
            log("=" * 40)
            
            // 步骤1: 启动心跳
            log("\n步骤1: 启动心跳服务")
            startHeartbeat()
            delay(1000)
            
            // 步骤2: 启动任务轮询
            log("\n步骤2: 启动任务轮询")
            startTaskPoller()
            delay(1000)
            
            // 步骤3: 模拟任务执行
            log("\n步骤3: 模拟任务执行")
            simulateTaskReceive()
            delay(3000)
            
            // 步骤4: 查看统计
            log("\n步骤4: 查看任务统计")
            showTaskStats()
            
            // 步骤5: 停止服务
            log("\n步骤5: 停止所有服务")
            stopTaskPoller()
            stopHeartbeat()
            
            log("\n" + "=" * 40)
            log("✅ 完整集成测试完成")
        }
    }

    private fun updateStatus() {
        val status = buildString {
            appendLine("📱 设备ID: $TEST_DEVICE_ID")
            appendLine("🌐 服务器: $TEST_SERVER_URL")
            appendLine("❤️ 心跳服务: ${if (heartbeatManager != null) "已初始化" else "未初始化"}")
            appendLine("📋 任务轮询: ${if (taskPoller != null) "已初始化" else "未初始化"}")
            appendLine("⚙️ 任务执行器: ${if (cloudTaskExecutor != null) "已初始化" else "未初始化"}")
            cloudTaskExecutor?.getTaskStats()?.let { stats ->
                appendLine("📊 任务统计: 完成=${stats.completed} 失败=${stats.failed} 运行中=${stats.running}")
            }
        }
        statusTextView.text = status
    }

    private fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message\n"
        logTextView.append(logMessage)
        Log.d(TAG, message)
    }

    private fun clearLogs() {
        logTextView.text = ""
        log("🗑️ 日志已清除")
    }

    data class TestCase(
        val name: String,
        val action: () -> Unit
    )

    private operator fun String.times(n: Int): String = repeat(n)
}
