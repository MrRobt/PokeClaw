package io.agents.pokeclaw.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.agents.pokeclaw.service.SmsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 云任务执行器
 * 负责执行从云端下发的各类任务
 * @author Hermes Agent
 */
class CloudTaskExecutor(
    private val context: Context,
    private val smsService: SmsService
) : TaskPoller.TaskHandler {

    companion object {
        private const val TAG = "CloudTaskExecutor"
    }

    // 任务执行统计
    private var tasksCompleted = 0
    private var tasksFailed = 0
    private var currentRunningTasks = 0
    private val maxConcurrentTasks = 3

    /**
     * 处理任务
     */
    override suspend fun handleTask(task: TaskPoller.CloudTask): TaskPoller.TaskResult {
        Log.i(TAG, "Executing task ${task.taskId}, type: ${task.type}")
        
        currentRunningTasks++
        
        return try {
            val result = when (task.type) {
                TaskPoller.CloudTask.TaskType.SEND_SMS -> handleSendSms(task.payload)
                TaskPoller.CloudTask.TaskType.MAKE_CALL -> handleMakeCall(task.payload)
                TaskPoller.CloudTask.TaskType.LAUNCH_APP -> handleLaunchApp(task.payload)
                TaskPoller.CloudTask.TaskType.CLICK_ELEMENT -> handleClickElement(task.payload)
                TaskPoller.CloudTask.TaskType.INPUT_TEXT -> handleInputText(task.payload)
                TaskPoller.CloudTask.TaskType.SWIPE_GESTURE -> handleSwipeGesture(task.payload)
                TaskPoller.CloudTask.TaskType.WAIT -> handleWait(task.payload)
                TaskPoller.CloudTask.TaskType.CUSTOM -> handleCustomTask(task.payload)
            }
            
            tasksCompleted++
            result
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${e.message}", e)
            tasksFailed++
            TaskPoller.TaskResult(
                success = false,
                message = e.message ?: "Task execution failed"
            )
        } finally {
            currentRunningTasks--
        }
    }

    /**
     * 判断是否接受任务
     */
    override fun canAcceptTask(): Boolean {
        return currentRunningTasks < maxConcurrentTasks
    }

    /**
     * 获取当前正在执行的任务数
     */
    override fun getRunningTaskCount(): Int = currentRunningTasks

    /**
     * 获取任务统计
     */
    fun getTaskStats(): TaskStats {
        return TaskStats(
            completed = tasksCompleted,
            failed = tasksFailed,
            running = currentRunningTasks
        )
    }

    data class TaskStats(
        val completed: Int,
        val failed: Int,
        val running: Int
    )

    // ========== 任务处理器 ==========

    /**
     * 处理发送短信任务
     */
    private suspend fun handleSendSms(payload: JSONObject): TaskPoller.TaskResult {
        val phoneNumber = payload.optString("phoneNumber")
        val message = payload.optString("message")
        
        if (phoneNumber.isEmpty() || message.isEmpty()) {
            return TaskPoller.TaskResult(
                success = false,
                message = "Missing phoneNumber or message"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                // 使用SmsService发送短信
                val smsId = smsService.sendSms(phoneNumber, message)
                
                // 等待发送结果
                var attempts = 0
                var status: SmsService.SmsStatus? = null
                
                while (attempts < 30) { // 最多等待30秒
                    status = smsService.getSmsStatus(smsId)
                    if (status?.state == SmsService.SmsState.SENT || 
                        status?.state == SmsService.SmsState.FAILED) {
                        break
                    }
                    Thread.sleep(1000)
                    attempts++
                }
                
                when (status?.state) {
                    SmsService.SmsState.SENT -> {
                        TaskPoller.TaskResult(
                            success = true,
                            message = "SMS sent successfully",
                            data = JSONObject().apply {
                                put("smsId", smsId)
                                put("phoneNumber", phoneNumber)
                                put("messageLength", message.length)
                            }
                        )
                    }
                    SmsService.SmsState.FAILED -> {
                        TaskPoller.TaskResult(
                            success = false,
                            message = "SMS sending failed: ${status.errorMessage}"
                        )
                    }
                    else -> {
                        TaskPoller.TaskResult(
                            success = false,
                            message = "SMS sending timeout"
                        )
                    }
                }
            } catch (e: Exception) {
                TaskPoller.TaskResult(
                    success = false,
                    message = "Failed to send SMS: ${e.message}"
                )
            }
        }
    }

    /**
     * 处理拨打电话任务
     */
    private fun handleMakeCall(payload: JSONObject): TaskPoller.TaskResult {
        val phoneNumber = payload.optString("phoneNumber")
        
        if (phoneNumber.isEmpty()) {
            return TaskPoller.TaskResult(
                success = false,
                message = "Missing phoneNumber"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 注意: 需要android.permission.CALL_PHONE权限
            context.startActivity(intent)
            
            TaskPoller.TaskResult(
                success = true,
                message = "Dialing $phoneNumber",
                data = JSONObject().apply {
                    put("phoneNumber", phoneNumber)
                }
            )
        } catch (e: SecurityException) {
            TaskPoller.TaskResult(
                success = false,
                message = "Permission denied: CALL_PHONE"
            )
        } catch (e: Exception) {
            TaskPoller.TaskResult(
                success = false,
                message = "Failed to make call: ${e.message}"
            )
        }
    }

    /**
     * 处理启动应用任务
     */
    private fun handleLaunchApp(payload: JSONObject): TaskPoller.TaskResult {
        val packageName = payload.optString("packageName")
        val activityName = payload.optString("activityName")
        
        if (packageName.isEmpty()) {
            return TaskPoller.TaskResult(
                success = false,
                message = "Missing packageName"
            )
        }

        return try {
            val intent = if (activityName.isNotEmpty()) {
                Intent().apply {
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (intent != null) {
                context.startActivity(intent)
                TaskPoller.TaskResult(
                    success = true,
                    message = "App launched: $packageName",
                    data = JSONObject().apply {
                        put("packageName", packageName)
                        put("activityName", activityName)
                    }
                )
            } else {
                TaskPoller.TaskResult(
                    success = false,
                    message = "Cannot launch app: $packageName"
                )
            }
        } catch (e: Exception) {
            TaskPoller.TaskResult(
                success = false,
                message = "Failed to launch app: ${e.message}"
            )
        }
    }

    /**
     * 处理点击元素任务
     * 注意: 需要辅助功能服务支持
     */
    private fun handleClickElement(payload: JSONObject): TaskPoller.TaskResult {
        // 这里需要集成AgentService的UI自动化能力
        val elementId = payload.optString("elementId")
        val elementText = payload.optString("elementText")
        val x = payload.optInt("x", -1)
        val y = payload.optInt("y", -1)
        
        return TaskPoller.TaskResult(
            success = false,
            message = "Click element requires AgentService integration (not implemented)"
        )
    }

    /**
     * 处理输入文本任务
     * 注意: 需要辅助功能服务支持
     */
    private fun handleInputText(payload: JSONObject): TaskPoller.TaskResult {
        val text = payload.optString("text")
        val elementId = payload.optString("elementId")
        
        return TaskPoller.TaskResult(
            success = false,
            message = "Input text requires AgentService integration (not implemented)"
        )
    }

    /**
     * 处理滑动手势任务
     * 注意: 需要辅助功能服务支持
     */
    private fun handleSwipeGesture(payload: JSONObject): TaskPoller.TaskResult {
        val direction = payload.optString("direction") // up, down, left, right
        val duration = payload.optInt("duration", 300)
        
        return TaskPoller.TaskResult(
            success = false,
            message = "Swipe gesture requires AgentService integration (not implemented)"
        )
    }

    /**
     * 处理等待任务
     */
    private fun handleWait(payload: JSONObject): TaskPoller.TaskResult {
        val durationMs = payload.optLong("durationMs", 1000)
        
        return try {
            Thread.sleep(durationMs)
            TaskPoller.TaskResult(
                success = true,
                message = "Waited for ${durationMs}ms",
                data = JSONObject().apply {
                    put("waitedMs", durationMs)
                }
            )
        } catch (e: InterruptedException) {
            TaskPoller.TaskResult(
                success = false,
                message = "Wait interrupted"
            )
        }
    }

    /**
     * 处理自定义任务
     */
    private fun handleCustomTask(payload: JSONObject): TaskPoller.TaskResult {
        val action = payload.optString("action")
        
        return when (action) {
            "test" -> TaskPoller.TaskResult(
                success = true,
                message = "Test task executed successfully",
                data = JSONObject().apply {
                    put("test", true)
                    put("timestamp", System.currentTimeMillis())
                }
            )
            "echo" -> TaskPoller.TaskResult(
                success = true,
                message = "Echo: ${payload.optString("message", "")}",
                data = payload
            )
            else -> TaskPoller.TaskResult(
                success = false,
                message = "Unknown custom action: $action"
            )
        }
    }
}
