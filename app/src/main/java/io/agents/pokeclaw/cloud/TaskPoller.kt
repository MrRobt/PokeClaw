package io.agents.pokeclaw.cloud

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 任务轮询器
 * 负责从Claw后端拉取待执行任务
 * @author Hermes Agent
 */
class TaskPoller(
    private val config: PollerConfig,
    private val taskHandler: TaskHandler
) {
    companion object {
        private const val TAG = "TaskPoller"
        private const val DEFAULT_POLL_INTERVAL_MS = 5000L // 5秒
        private const val MIN_POLL_INTERVAL_MS = 1000L // 最小1秒
        private const val MAX_POLL_INTERVAL_MS = 60000L // 最大1分钟
    }

    data class PollerConfig(
        val baseUrl: String,
        val deviceId: String,
        val apiKey: String? = null,
        val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        val maxConcurrentTasks: Int = 3,
        val enableLongPolling: Boolean = true,
        val longPollingTimeoutMs: Long = 30000L
    )

    interface TaskHandler {
        /**
         * 处理任务
         * @return 任务执行结果
         */
        suspend fun handleTask(task: CloudTask): TaskResult
        
        /**
         * 判断是否接受任务
         */
        fun canAcceptTask(): Boolean
        
        /**
         * 获取当前正在执行的任务数
         */
        fun getRunningTaskCount(): Int
    }

    data class CloudTask(
        val taskId: String,
        val type: TaskType,
        val payload: JSONObject,
        val priority: Int,
        val timeoutMs: Long,
        val createdAt: Long,
        val retryCount: Int = 0
    ) {
        enum class TaskType {
            SEND_SMS,
            MAKE_CALL,
            LAUNCH_APP,
            CLICK_ELEMENT,
            INPUT_TEXT,
            SWIPE_GESTURE,
            WAIT,
            CUSTOM
        }
    }

    data class TaskResult(
        val success: Boolean,
        val message: String? = null,
        val data: JSONObject? = null,
        val executionTimeMs: Long = 0
    )

    interface PollerListener {
        fun onPollStarted()
        fun onPollStopped()
        fun onTaskReceived(task: CloudTask)
        fun onTaskStarted(task: CloudTask)
        fun onTaskCompleted(task: CloudTask, result: TaskResult)
        fun onTaskFailed(task: CloudTask, error: String)
        fun onPollError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(if (config.enableLongPolling) config.longPollingTimeoutMs + 5000 else 30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var currentPollInterval = config.pollIntervalMs.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
    
    private val listeners = mutableListOf<PollerListener>()
    private val runningTasks = mutableMapOf<String, Job>()

    /**
     * 启动轮询服务
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Poller already running")
            return
        }

        Log.i(TAG, "Starting task poller, interval: ${currentPollInterval}ms")
        listeners.forEach { it.onPollStarted() }

        pollJob = scope.launch {
            while (isActive && isRunning.get()) {
                try {
                    pollTasks()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}", e)
                    listeners.forEach { it.onPollError(e.message ?: "Unknown error") }
                }
                delay(currentPollInterval)
            }
        }
    }

    /**
     * 停止轮询服务
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.i(TAG, "Stopping task poller")
        pollJob?.cancel()
        pollJob = null

        // 取消所有正在执行的任务
        runningTasks.values.forEach { it.cancel() }
        runningTasks.clear()

        listeners.forEach { it.onPollStopped() }
    }

    /**
     * 添加监听器
     */
    fun addListener(listener: PollerListener) {
        listeners.add(listener)
    }

    /**
     * 移除监听器
     */
    fun removeListener(listener: PollerListener) {
        listeners.remove(listener)
    }

    /**
     * 轮询任务
     */
    private suspend fun pollTasks() {
        if (!taskHandler.canAcceptTask()) {
            Log.d(TAG, "Cannot accept more tasks, skipping poll")
            return
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/hermes/tasks/poll?deviceId=${config.deviceId}&limit=${config.maxConcurrentTasks - taskHandler.getRunningTaskCount()}")
            .get()
            .apply {
                config.apiKey?.let { addHeader("X-API-Key", it) }
                if (config.enableLongPolling) {
                    addHeader("X-Long-Polling", "true")
                    addHeader("X-Timeout", config.longPollingTimeoutMs.toString())
                }
            }
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }

        val body = response.body?.string()
        val json = body?.let { JSONObject(it) }

        if (json?.optInt("code", -1) != 0) {
            throw IOException("API error: ${json?.optString("msg")}")
        }

        val tasksArray = json.optJSONArray("data") ?: JSONArray()
        
        if (tasksArray.length() > 0) {
            Log.i(TAG, "Received ${tasksArray.length()} tasks")
            
            // 有任务时缩短轮询间隔
            currentPollInterval = MIN_POLL_INTERVAL_MS
            
            for (i in 0 until tasksArray.length()) {
                val taskJson = tasksArray.getJSONObject(i)
                val task = parseTask(taskJson)
                
                listeners.forEach { it.onTaskReceived(task) }
                
                // 启动任务执行
                if (taskHandler.canAcceptTask()) {
                    executeTask(task)
                } else {
                    Log.w(TAG, "Cannot accept task ${task.taskId}, skipping")
                }
            }
        } else {
            // 无任务时逐渐增加轮询间隔
            if (currentPollInterval < MAX_POLL_INTERVAL_MS) {
                currentPollInterval = (currentPollInterval * 1.5).toLong().coerceAtMost(MAX_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 解析任务
     */
    private fun parseTask(json: JSONObject): CloudTask {
        return CloudTask(
            taskId = json.getString("taskId"),
            type = CloudTask.TaskType.valueOf(json.optString("type", "CUSTOM")),
            payload = json.optJSONObject("payload") ?: JSONObject(),
            priority = json.optInt("priority", 0),
            timeoutMs = json.optLong("timeoutMs", 60000),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            retryCount = json.optInt("retryCount", 0)
        )
    }

    /**
     * 执行任务
     */
    private fun executeTask(task: CloudTask) {
        val taskJob = scope.launch {
            val startTime = System.currentTimeMillis()
            
            listeners.forEach { it.onTaskStarted(task) }
            
            try {
                withTimeout(task.timeoutMs) {
                    val result = taskHandler.handleTask(task)
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    val resultWithTime = result.copy(executionTimeMs = executionTime)
                    
                    if (result.success) {
                        listeners.forEach { it.onTaskCompleted(task, resultWithTime) }
                        reportTaskResult(task, resultWithTime)
                    } else {
                        listeners.forEach { it.onTaskFailed(task, result.message ?: "Unknown error") }
                        reportTaskFailure(task, result.message ?: "Task execution failed")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val error = "Task timeout after ${task.timeoutMs}ms"
                listeners.forEach { it.onTaskFailed(task, error) }
                reportTaskFailure(task, error)
            } catch (e: Exception) {
                val error = e.message ?: "Task execution error"
                listeners.forEach { it.onTaskFailed(task, error) }
                reportTaskFailure(task, error)
            } finally {
                runningTasks.remove(task.taskId)
            }
        }
        
        runningTasks[task.taskId] = taskJob
    }

    /**
     * 上报任务结果
     */
    private suspend fun reportTaskResult(task: CloudTask, result: TaskResult) {
        try {
            val requestBody = JSONObject().apply {
                put("taskId", task.taskId)
                put("deviceId", config.deviceId)
                put("success", true)
                put("executionTimeMs", result.executionTimeMs)
                put("data", result.data)
                put("completedAt", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url("${config.baseUrl}/hermes/tasks/result")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .apply {
                    config.apiKey?.let { addHeader("X-API-Key", it) }
                }
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report task result: ${e.message}")
        }
    }

    /**
     * 上报任务失败
     */
    private suspend fun reportTaskFailure(task: CloudTask, error: String) {
        try {
            val requestBody = JSONObject().apply {
                put("taskId", task.taskId)
                put("deviceId", config.deviceId)
                put("success", false)
                put("error", error)
                put("completedAt", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url("${config.baseUrl}/hermes/tasks/result")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .apply {
                    config.apiKey?.let { addHeader("X-API-Key", it) }
                }
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report task failure: ${e.message}")
        }
    }

    /**
     * 获取正在运行的任务数
     */
    fun getRunningTaskCount(): Int = runningTasks.size

    /**
     * 获取当前轮询间隔
     */
    fun getCurrentPollInterval(): Long = currentPollInterval
}
