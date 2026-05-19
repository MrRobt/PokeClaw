// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TaskPoller - 云端任务轮询器
 * 
 * 负责轮询Claw后端获取待执行的任务，实现设备端主动拉取任务模式。
 * 与CloudHeartbeatManager配合使用，提供双通道任务获取机制。
 *
 * @author Hermes Agent
 */
class TaskPoller private constructor(
    private val deviceId: String,
    private val serverUrl: String
) {
    companion object {
        private const val TAG = "TaskPoller"
        private const val DEFAULT_POLL_INTERVAL_MS = 5000L // 5秒轮询一次
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
        
        @Volatile
        private var instance: TaskPoller? = null
        
        fun getInstance(deviceId: String, serverUrl: String): TaskPoller {
            return instance ?: synchronized(this) {
                instance ?: TaskPoller(deviceId, serverUrl).also {
                    instance = it
                }
            }
        }
        
        fun destroy() {
            instance?.stop()
            instance = null
        }
    }

    // HTTP Client
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // 轮询配置
    private var pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    private var isRunning = AtomicBoolean(false)
    
    // 协程
    private val pollerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    
    // 回调
    interface TaskPollCallback {
        fun onTaskReceived(task: PolledTask)
        fun onError(error: Throwable)
    }
    
    private var callback: TaskPollCallback? = null
    
    /**
     * 轮询到的任务数据类
     */
    data class PolledTask(
        val taskId: String,
        val taskName: String,
        val taskType: TaskType,
        val priority: Int,
        val taskConfig: JSONObject,
        val createTime: String
    )
    
    /**
     * 任务类型
     */
    enum class TaskType {
        CHAT_REPLY, AUTO_LIKE, CONTENT_GENERATE, DATA_COLLECT, CUSTOM_ACTION
    }

    /**
     * 启动轮询
     */
    fun start(callback: TaskPollCallback, pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS) {
        if (isRunning.get()) {
            XLog.w(TAG, "轮询器已经在运行中")
            return
        }
        
        this.callback = callback
        this.pollIntervalMs = pollIntervalMs
        isRunning.set(true)
        
        pollJob = pollerScope.launch {
            XLog.i(TAG, "任务轮询已启动，间隔: ${pollIntervalMs}ms")
            
            while (isActive && isRunning.get()) {
                try {
                    pollTasks()
                    delay(pollIntervalMs)
                } catch (e: CancellationException) {
                    XLog.d(TAG, "轮询任务被取消")
                    break
                } catch (e: Exception) {
                    XLog.e(TAG, "轮询异常: ${e.message}")
                    callback.onError(e)
                    delay(pollIntervalMs)
                }
            }
        }
    }
    
    /**
     * 停止轮询
     */
    fun stop() {
        XLog.i(TAG, "停止任务轮询")
        isRunning.set(false)
        pollJob?.cancel()
        pollJob = null
        callback = null
    }
    
    /**
     * 执行轮询
     */
    private suspend fun pollTasks() {
        val url = "$serverUrl/hermes/agent-task/poll/$deviceId?limit=5"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .header("X-Device-ID", deviceId)
            .build()
        
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    body?.let { parseTasks(it) }
                } else {
                    XLog.w(TAG, "轮询失败: ${response.code}")
                }
            }
        }
    }
    
    /**
     * 解析任务响应
     */
    private fun parseTasks(responseBody: String) {
        try {
            val json = JSONObject(responseBody)
            
            if (!json.has("data")) {
                return
            }
            
            val data = json.get("data")
            if (data is JSONArray) {
                for (i in 0 until data.length()) {
                    val taskJson = data.getJSONObject(i)
                    val task = parseTask(taskJson)
                    task?.let {
                        callback?.onTaskReceived(it)
                    }
                }
            }
        } catch (e: Exception) {
            XLog.w(TAG, "解析任务响应失败: ${e.message}")
        }
    }
    
    /**
     * 解析单个任务
     */
    private fun parseTask(taskJson: JSONObject): PolledTask? {
        return try {
            PolledTask(
                taskId = taskJson.getString("taskId"),
                taskName = taskJson.optString("taskName", "Unnamed Task"),
                taskType = TaskType.valueOf(
                    taskJson.optString("taskType", "CUSTOM_ACTION")
                ),
                priority = taskJson.optInt("priority", 0),
                taskConfig = taskJson.optJSONObject("taskConfig") ?: JSONObject(),
                createTime = taskJson.optString("createTime", "")
            )
        } catch (e: Exception) {
            XLog.w(TAG, "解析任务失败: ${e.message}")
            null
        }
    }
    
    /**
     * 上报任务进度
     */
    fun reportProgress(taskId: String, progress: Int, message: String? = null) {
        pollerScope.launch {
            try {
                val url = "$serverUrl/hermes/agent-task/$taskId/progress"
                val json = JSONObject().apply {
                    put("progress", progress)
                    message?.let { put("message", it) }
                }
                
                val request = Request.Builder()
                    .url(url)
                    .post(okhttp3.RequestBody.create(
                        okhttp3.MediaType.parse("application/json"),
                        json.toString()
                    ))
                    .header("X-Device-ID", deviceId)
                    .build()
                
                withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().close()
                }
                
                XLog.d(TAG, "进度上报成功: taskId=$taskId, progress=$progress")
            } catch (e: Exception) {
                XLog.w(TAG, "进度上报失败: ${e.message}")
            }
        }
    }
    
    /**
     * 上报任务结果
     */
    fun reportResult(taskId: String, status: String, result: JSONObject? = null, errorMessage: String? = null) {
        pollerScope.launch {
            try {
                val url = "$serverUrl/hermes/agent-task/report"
                val json = JSONObject().apply {
                    put("taskId", taskId)
                    put("deviceId", deviceId)
                    put("status", status)
                    result?.let { put("result", it) }
                    errorMessage?.let { put("errorMessage", it) }
                    put("progress", if (status == "COMPLETED") 100 else 0)
                }
                
                val request = Request.Builder()
                    .url(url)
                    .post(okhttp3.RequestBody.create(
                        okhttp3.MediaType.parse("application/json"),
                        json.toString()
                    ))
                    .header("X-Device-ID", deviceId)
                    .build()
                
                withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().close()
                }
                
                XLog.i(TAG, "结果上报成功: taskId=$taskId, status=$status")
            } catch (e: Exception) {
                XLog.w(TAG, "结果上报失败: ${e.message}")
            }
        }
    }
    
    /**
     * 检查是否在运行
     */
    fun isRunning(): Boolean = isRunning.get()
}
