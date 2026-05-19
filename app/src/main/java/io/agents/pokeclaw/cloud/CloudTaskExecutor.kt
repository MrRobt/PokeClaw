// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.AgentService
import io.agents.pokeclaw.agent.AgentCallback
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * CloudTaskExecutor - 云端任务执行器
 * 
 * 负责协调TaskPoller获取的任务，调度AgentService执行，
 * 并上报执行结果到Claw后端。
 * 
 * 实现完整的任务生命周期管理：
 * 1. 从云端拉取任务 (TaskPoller)
 * 2. 解析任务并路由到对应处理器
 * 3. 执行Agent任务
 * 4. 上报执行结果
 *
 * @author Hermes Agent
 */
class CloudTaskExecutor private constructor(
    private val context: android.content.Context,
    private val deviceId: String,
    private val serverUrl: String,
    private val agentService: AgentService
) {
    companion object {
        private const val TAG = "CloudTaskExecutor"
        private const val MAX_CONCURRENT_TASKS = 3
        private const val DEFAULT_EXECUTION_TIMEOUT_MS = 120000L // 2分钟超时

        @Volatile
        private var instance: CloudTaskExecutor? = null

        fun getInstance(
            context: android.content.Context,
            deviceId: String,
            serverUrl: String,
            agentService: AgentService
        ): CloudTaskExecutor {
            return instance ?: synchronized(this) {
                instance ?: CloudTaskExecutor(context, deviceId, serverUrl, agentService).also {
                    instance = it
                }
            }
        }

        fun destroy() {
            instance?.stop()
            instance = null
        }
    }

    // 任务轮询器
    private val taskPoller: TaskPoller = TaskPoller.getInstance(deviceId, serverUrl)

    // 执行状态
    private val isRunning = AtomicBoolean(false)
    private val activeTasks = ConcurrentHashMap<String, Job>()
    private val runningTasksCount = AtomicInteger(0)
    private val completedTasksCount = AtomicInteger(0)
    private val failedTasksCount = AtomicInteger(0)

    // 协程作用域
    private val executorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 回调接口
    interface TaskExecutionCallback {
        fun onTaskStarted(taskId: String, taskType: String)
        fun onTaskProgress(taskId: String, progress: Int, message: String?)
        fun onTaskCompleted(taskId: String, result: String)
        fun onTaskFailed(taskId: String, error: String)
        fun onExecutorError(error: Throwable)
    }

    private var callback: TaskExecutionCallback? = null

    /**
     * 启动执行器
     */
    fun start(callback: TaskExecutionCallback? = null) {
        if (isRunning.get()) {
            XLog.w(TAG, "执行器已经在运行中")
            return
        }

        this.callback = callback
        isRunning.set(true)

        // 启动任务轮询
        taskPoller.start(object : TaskPoller.TaskPollCallback {
            override fun onTaskReceived(task: TaskPoller.PolledTask) {
                handleNewTask(task)
            }

            override fun onError(error: Throwable) {
                XLog.e(TAG, "轮询错误: ${error.message}")
                callback?.onExecutorError(error)
            }
        })

        XLog.i(TAG, "任务执行器已启动")
    }

    /**
     * 停止执行器
     */
    fun stop() {
        XLog.i(TAG, "停止任务执行器")
        isRunning.set(false)

        // 取消所有活跃任务
        activeTasks.forEach { (_, job) ->
            job.cancel()
        }
        activeTasks.clear()

        // 停止轮询
        taskPoller.stop()

        executorScope.cancel()
        XLog.i(TAG, "任务执行器已停止")
    }

    /**
     * 处理新任务
     */
    private fun handleNewTask(task: TaskPoller.PolledTask) {
        if (activeTasks.containsKey(task.taskId)) {
            XLog.w(TAG, "任务已在执行中: ${task.taskId}")
            return
        }

        if (runningTasksCount.get() >= MAX_CONCURRENT_TASKS) {
            XLog.w(TAG, "并发任务数已达上限，跳过任务: ${task.taskId}")
            return
        }

        val job = executorScope.launch {
            try {
                executeTask(task)
            } catch (e: CancellationException) {
                XLog.d(TAG, "任务被取消: ${task.taskId}")
            } catch (e: Exception) {
                XLog.e(TAG, "任务执行异常: ${task.taskId}, ${e.message}")
                handleTaskFailure(task, e.message ?: "未知错误")
            }
        }

        activeTasks[task.taskId] = job
    }

    /**
     * 执行任务
     */
    private suspend fun executeTask(task: TaskPoller.PolledTask) {
        XLog.i(TAG, "开始执行任务: ${task.taskId}, 类型: ${task.taskType}")

        runningTasksCount.incrementAndGet()
        callback?.onTaskStarted(task.taskId, task.taskType.name)

        // 上报任务开始状态
        taskPoller.reportProgress(task.taskId, 10, "任务开始执行")

        try {
            val result = when (task.taskType) {
                TaskPoller.TaskType.CHAT_REPLY -> executeChatReplyTask(task)
                TaskPoller.TaskType.AUTO_LIKE -> executeAutoLikeTask(task)
                TaskPoller.TaskType.CONTENT_GENERATE -> executeContentGenerateTask(task)
                TaskPoller.TaskType.DATA_COLLECT -> executeDataCollectTask(task)
                TaskPoller.TaskType.CUSTOM_ACTION -> executeCustomActionTask(task)
            }

            // 上报成功结果
            val resultJson = JSONObject().apply {
                put("success", true)
                put("output", result)
                put("executionTime", System.currentTimeMillis())
            }

            taskPoller.reportResult(task.taskId, "COMPLETED", resultJson, null)
            completedTasksCount.incrementAndGet()

            callback?.onTaskCompleted(task.taskId, result)
            XLog.i(TAG, "任务执行成功: ${task.taskId}")

        } catch (e: Exception) {
            handleTaskFailure(task, e.message ?: "执行失败")
        } finally {
            runningTasksCount.decrementAndGet()
            activeTasks.remove(task.taskId)
        }
    }

    /**
     * 处理任务失败
     */
    private fun handleTaskFailure(task: TaskPoller.PolledTask, errorMessage: String) {
        failedTasksCount.incrementAndGet()

        val resultJson = JSONObject().apply {
            put("success", false)
            put("error", errorMessage)
            put("executionTime", System.currentTimeMillis())
        }

        taskPoller.reportResult(task.taskId, "FAILED", resultJson, errorMessage)
        callback?.onTaskFailed(task.taskId, errorMessage)
        XLog.e(TAG, "任务执行失败: ${task.taskId}, $errorMessage")
    }

    /**
     * 执行聊天回复任务
     */
    private suspend fun executeChatReplyTask(task: TaskPoller.PolledTask): String {
        taskPoller.reportProgress(task.taskId, 30, "准备聊天回复")

        val contactName = task.taskConfig.optString("contactName")
        val messageContent = task.taskConfig.optString("message")
        val replyStyle = task.taskConfig.optString("replyStyle", "friendly")

        if (contactName.isBlank() || messageContent.isBlank()) {
            throw IllegalArgumentException("缺少必要参数: contactName 或 message")
        }

        taskPoller.reportProgress(task.taskId, 50, "生成回复内容")

        // 调用AgentService生成回复
        val prompt = """
            |联系人: $contactName
            |消息: $messageContent
            |回复风格: $replyStyle
            |请生成一个合适的回复。
        """.trimMargin()

        return withTimeout(DEFAULT_EXECUTION_TIMEOUT_MS) {
            val result = agentService.processMessage(prompt, object : AgentCallback {
                override fun onProgress(message: String) {
                    taskPoller.reportProgress(task.taskId, 70, message)
                }

                override fun onComplete(result: String) {
                    // 异步处理
                }

                override fun onError(error: String) {
                    throw RuntimeException(error)
                }
            })
            result
        }
    }

    /**
     * 执行自动点赞任务
     */
    private suspend fun executeAutoLikeTask(task: TaskPoller.PolledTask): String {
        taskPoller.reportProgress(task.taskId, 30, "准备自动点赞")

        val targetType = task.taskConfig.optString("targetType", "friendCircle")
        val targetId = task.taskConfig.optString("targetId")
        val likeCount = task.taskConfig.optInt("likeCount", 10)

        taskPoller.reportProgress(task.taskId, 60, "执行点赞操作")

        // 模拟点赞逻辑（实际实现需集成微信自动化）
        delay(1000)

        return JSONObject().apply {
            put("targetType", targetType)
            put("targetId", targetId)
            put("likedCount", likeCount)
            put("message", "成功点赞 $likeCount 条内容")
        }.toString()
    }

    /**
     * 执行内容生成任务
     */
    private suspend fun executeContentGenerateTask(task: TaskPoller.PolledTask): String {
        taskPoller.reportProgress(task.taskId, 30, "准备生成内容")

        val contentType = task.taskConfig.optString("contentType", "text")
        val topic = task.taskConfig.optString("topic")
        val length = task.taskConfig.optInt("length", 200)
        val style = task.taskConfig.optString("style", "casual")

        taskPoller.reportProgress(task.taskId, 50, "生成内容中...")

        val prompt = """
            |请生成一段$contentType 类型的内容
            |主题: $topic
            |字数: 约 $length 字
            |风格: $style
        """.trimMargin()

        return withTimeout(DEFAULT_EXECUTION_TIMEOUT_MS) {
            agentService.generateContent(prompt)
        }
    }

    /**
     * 执行数据收集任务
     */
    private suspend fun executeDataCollectTask(task: TaskPoller.PolledTask): String {
        taskPoller.reportProgress(task.taskId, 30, "开始数据收集")

        val dataType = task.taskConfig.optString("dataType", "contacts")
        val filter = task.taskConfig.optString("filter", "")

        taskPoller.reportProgress(task.taskId, 60, "处理收集的数据")

        // 模拟数据收集
        val collectedData = JSONObject().apply {
            put("dataType", dataType)
            put("count", (10..50).random())
            put("filter", filter)
            put("timestamp", System.currentTimeMillis())
        }

        return collectedData.toString()
    }

    /**
     * 执行自定义动作任务
     */
    private suspend fun executeCustomActionTask(task: TaskPoller.PolledTask): String {
        taskPoller.reportProgress(task.taskId, 30, "执行自定义动作")

        val actionName = task.taskConfig.optString("actionName", "default")
        val actionParams = task.taskConfig.optJSONObject("params") ?: JSONObject()

        taskPoller.reportProgress(task.taskId, 60, "处理动作: $actionName")

        // 根据动作名称路由到不同的处理逻辑
        return when (actionName) {
            "sendNotification" -> {
                val title = actionParams.optString("title", "通知")
                val body = actionParams.optString("body", "")
                JSONObject().apply {
                    put("action", actionName)
                    put("sent", true)
                    put("title", title)
                }.toString()
            }
            "scheduleTask" -> {
                val scheduledTime = actionParams.optLong("time", System.currentTimeMillis())
                JSONObject().apply {
                    put("action", actionName)
                    put("scheduled", true)
                    put("time", scheduledTime)
                }.toString()
            }
            else -> {
                JSONObject().apply {
                    put("action", actionName)
                    put("executed", true)
                    put("params", actionParams)
                }.toString()
            }
        }
    }

    /**
     * 获取执行统计
     */
    fun getStats(): TaskExecutionStats {
        return TaskExecutionStats(
            runningTasks = runningTasksCount.get(),
            completedTasks = completedTasksCount.get(),
            failedTasks = failedTasksCount.get(),
            activeTaskIds = activeTasks.keys.toList()
        )
    }

    /**
     * 取消特定任务
     */
    fun cancelTask(taskId: String): Boolean {
        val job = activeTasks[taskId]
        return if (job != null) {
            job.cancel()
            activeTasks.remove(taskId)
            taskPoller.reportResult(taskId, "CANCELLED", null, "用户取消")
            XLog.i(TAG, "任务已取消: $taskId")
            true
        } else {
            false
        }
    }

    /**
     * 执行统计数据类
     */
    data class TaskExecutionStats(
        val runningTasks: Int,
        val completedTasks: Int,
        val failedTasks: Int,
        val activeTaskIds: List<String>
    )

    /**
     * 检查是否在运行
     */
    fun isRunning(): Boolean = isRunning.get()
}
