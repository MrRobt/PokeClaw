// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import android.content.Context
import io.agents.pokeclaw.agent.AgentService
import io.agents.pokeclaw.agent.DefaultAgentService
import io.agents.pokeclaw.agent.TaskResult
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CloudAgentService - 云端Agent服务
 * 
 * 整合本地Agent能力和云端心跳管理，实现远程任务执行和状态上报。
 * 作为PokeClaw与Claw后端服务的桥梁。
 *
 * @author Hermes Agent
 */
class CloudAgentService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CloudAgentService"
        
        @Volatile
        private var instance: CloudAgentService? = null

        fun getInstance(context: Context): CloudAgentService {
            return instance ?: synchronized(this) {
                instance ?: CloudAgentService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // 本地Agent服务
    private val localAgent: AgentService = DefaultAgentService()
    
    // 云端心跳管理器
    private val heartbeatManager: CloudHeartbeatManager = CloudHeartbeatManager.getInstance(context)
    
    // 服务状态
    private val isInitialized = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 正在执行的任务
    private val activeTasks = ConcurrentHashMap<String, Job>()
    
    // 配置
    data class Config(
        val serverUrl: String,
        val deviceId: String = "",
        val deviceName: String = "PokeClaw Device",
        val authToken: String = "",
        val heartbeatIntervalMs: Long = 30000L,
        val agentConfig: AgentService.AgentConfig = AgentService.AgentConfig()
    )

    private var config: Config? = null

    /**
     * Agent状态回调
     */
    interface CloudAgentCallback {
        fun onCloudConnected(deviceId: String)
        fun onCloudDisconnected(reason: String)
        fun onTaskReceived(taskId: String, instruction: String)
        fun onTaskCompleted(taskId: String, result: TaskResult)
        fun onError(error: Throwable)
    }

    private var callback: CloudAgentCallback? = null

    /**
     * 初始化服务
     */
    fun initialize(config: Config, callback: CloudAgentCallback? = null) {
        if (isInitialized.get()) {
            XLog.w(TAG, "服务已经初始化")
            return
        }

        this.config = config
        this.callback = callback

        // 初始化本地Agent
        localAgent.initialize(context, config.agentConfig)

        // 初始化云端心跳
        val heartbeatConfig = CloudHeartbeatManager.Config(
            serverUrl = config.serverUrl,
            deviceId = config.deviceId,
            deviceName = config.deviceName,
            deviceType = "ANDROID_PHONE",
            authToken = config.authToken,
            heartbeatIntervalMs = config.heartbeatIntervalMs
        )

        heartbeatManager.initialize(heartbeatConfig, object : CloudHeartbeatManager.HeartbeatCallback {
            override fun onConnected(deviceId: String) {
                XLog.i(TAG, "云端连接成功: $deviceId")
                this@CloudAgentService.callback?.onCloudConnected(deviceId)
            }

            override fun onDisconnected(reason: String) {
                XLog.w(TAG, "云端连接断开: $reason")
                this@CloudAgentService.callback?.onCloudDisconnected(reason)
            }

            override fun onCommandReceived(command: CloudHeartbeatManager.CloudCommand) {
                handleCloudCommand(command)
            }

            override fun onError(error: Throwable) {
                XLog.e(TAG, "云端错误: ${error.message}")
                this@CloudAgentService.callback?.onError(error)
            }
        })

        isInitialized.set(true)
        XLog.i(TAG, "CloudAgentService初始化完成")
    }

    /**
     * 启动服务
     */
    fun start(): Boolean {
        if (!isInitialized.get()) {
            XLog.e(TAG, "服务未初始化，无法启动")
            return false
        }

        if (isRunning.get()) {
            XLog.w(TAG, "服务已经在运行中")
            return true
        }

        // 启动本地Agent
        localAgent.start()

        // 启动云端心跳
        val started = heartbeatManager.start()
        if (!started) {
            XLog.e(TAG, "启动云端心跳失败")
            localAgent.stop()
            return false
        }

        isRunning.set(true)
        XLog.i(TAG, "CloudAgentService已启动")
        return true
    }

    /**
     * 停止服务
     */
    fun stop() {
        XLog.i(TAG, "停止CloudAgentService")
        
        isRunning.set(false)
        
        // 取消所有正在执行的任务
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()

        // 停止本地Agent
        localAgent.stop()

        // 停止云端心跳
        heartbeatManager.stop()
    }

    /**
     * 处理云端命令
     */
    private fun handleCloudCommand(command: CloudHeartbeatManager.CloudCommand) {
        XLog.i(TAG, "收到云端命令: ${command.type}, commandId=${command.commandId}")

        when (command.type) {
            CloudHeartbeatManager.CommandType.EXECUTE_TASK -> {
                val taskId = command.payload.optString("taskId", command.commandId)
                val instruction = command.payload.optString("instruction", "")
                
                if (instruction.isNotBlank()) {
                    executeCloudTask(taskId, instruction)
                } else {
                    XLog.w(TAG, "任务指令为空: $taskId")
                    reportTaskResult(taskId, false, "任务指令为空")
                }
            }
            
            CloudHeartbeatManager.CommandType.QUERY_STATUS -> {
                reportDeviceStatus()
            }
            
            CloudHeartbeatManager.CommandType.UPDATE_CONFIG -> {
                updateAgentConfig(command.payload)
            }
            
            CloudHeartbeatManager.CommandType.REBOOT -> {
                // 实际重启逻辑由上层处理
                XLog.i(TAG, "收到重启命令")
            }
            
            else -> {
                XLog.w(TAG, "未知命令类型: ${command.type}")
            }
        }
    }

    /**
     * 执行云端任务
     */
    private fun executeCloudTask(taskId: String, instruction: String) {
        // 更新状态为忙碌
        heartbeatManager.updateStatus(CloudHeartbeatManager.DeviceStatus.BUSY)
        
        callback?.onTaskReceived(taskId, instruction)

        val job = serviceScope.launch {
            try {
                XLog.i(TAG, "开始执行任务: $taskId")
                
                // 调用本地Agent执行任务
                val result = withContext(Dispatchers.IO) {
                    localAgent.executeTask(instruction)
                }

                XLog.i(TAG, "任务执行完成: $taskId, success=${result.success}")
                
                // 上报任务结果
                val resultMessage = buildString {
                    appendLine("任务执行${if (result.success) "成功" else "失败"}")
                    if (result.output.isNotBlank()) {
                        appendLine("输出: ${result.output}")
                    }
                    if (result.error.isNotBlank()) {
                        appendLine("错误: ${result.error}")
                    }
                }
                
                reportTaskResult(taskId, result.success, resultMessage.trim())
                callback?.onTaskCompleted(taskId, result)

            } catch (e: CancellationException) {
                XLog.w(TAG, "任务被取消: $taskId")
                reportTaskResult(taskId, false, "任务被取消")
            } catch (e: Exception) {
                XLog.e(TAG, "任务执行异常: $taskId, error=${e.message}")
                reportTaskResult(taskId, false, "执行异常: ${e.message}")
                callback?.onError(e)
            } finally {
                // 恢复状态为在线
                heartbeatManager.updateStatus(CloudHeartbeatManager.DeviceStatus.ONLINE)
                activeTasks.remove(taskId)
            }
        }

        activeTasks[taskId] = job
    }

    /**
     * 上报任务结果
     */
    private fun reportTaskResult(taskId: String, success: Boolean, result: String) {
        heartbeatManager.reportTaskResult(taskId, success, result)
    }

    /**
     * 上报设备状态
     */
    private fun reportDeviceStatus() {
        val status = JSONObject().apply {
            put("agentRunning", isRunning.get())
            put("heartbeatConnected", heartbeatManager.isConnected())
            put("activeTasks", activeTasks.size)
            put("currentStatus", heartbeatManager.getCurrentStatus().name)
        }
        XLog.d(TAG, "设备状态: $status")
    }

    /**
     * 更新Agent配置
     */
    private fun updateAgentConfig(payload: JSONObject) {
        try {
            // 解析新配置
            val newConfig = payload.optJSONObject("agentConfig")
            newConfig?.let {
                // 应用新配置
                XLog.i(TAG, "更新Agent配置: $it")
                // 实际配置更新逻辑
            }
        } catch (e: Exception) {
            XLog.e(TAG, "更新配置失败: ${e.message}")
        }
    }

    /**
     * 取消指定任务
     */
    fun cancelTask(taskId: String): Boolean {
        val job = activeTasks[taskId]
        return if (job != null) {
            job.cancel()
            activeTasks.remove(taskId)
            XLog.i(TAG, "任务已取消: $taskId")
            true
        } else {
            false
        }
    }

    /**
     * 获取活跃任务数量
     */
    fun getActiveTaskCount(): Int = activeTasks.size

    /**
     * 是否已连接云端
     */
    fun isCloudConnected(): Boolean = heartbeatManager.isConnected()

    /**
     * 获取设备ID
     */
    fun getDeviceId(): String = heartbeatManager.getDeviceId()

    /**
     * 销毁服务
     */
    fun destroy() {
        stop()
        serviceScope.cancel()
        heartbeatManager.destroy()
        instance = null
    }
}
