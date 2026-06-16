// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务执行器桥接：连接 cloudnode 契约与本地 Skill 执行。

package io.agents.pokeclaw.cloudnode

import io.agents.pokeclaw.agent.learning.TaskLearningManager
import io.agents.pokeclaw.agent.skill.SkillExecutor
import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.utils.XLog

/**
 * 云端任务执行器桥接。
 * 职责：
 * 1. 接收 CloudExecutorTask
 * 2. 映射为本地 Skill（或使用 AgentLoop）
 * 3. 执行并收集结果
 * 4. 返回标准化的 CloudTaskExecutionResult
 */
class CloudTaskExecutorBridge(
    private val skillExecutor: SkillExecutor? = null,
    private val learningManager: TaskLearningManager? = null,
) {
    private val TAG = "CloudTaskExecutorBridge"

    /**
     * 执行云端下发的任务。
     * 优先尝试确定性技能映射，失败时返回明确错误。
     */
    fun execute(task: CloudExecutorTask): CloudTaskExecutionResult {
        XLog.i(TAG, "执行任务: ${task.taskId}, 指令: ${task.instruction}")

        // 1. 指令映射
        val mapping = CloudTaskSkillMapper.mapToSkill(task.instruction)
        if (mapping == null) {
            XLog.w(TAG, "无法映射指令到技能: ${task.instruction}")
            learningManager?.recordFailure(
                taskId = task.taskId,
                taskText = task.instruction,
                errorCategory = "CLOUD_SKILL",
                errorCode = "TASK_REJECTED",
                recoveryHint = "No deterministic skill mapping is available.",
            )
            return CloudTaskExecutionResult.failure(
                message = "不支持的任务类型: ${task.instruction}",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = false
            )
        }

        XLog.d(TAG, "指令映射结果: skillId=${mapping.skillId}, confidence=${mapping.confidence}")

        
        val skill = CloudTaskSkillMapper.resolveSkill(mapping)
        if (skill == null) {
            XLog.w(TAG, "Skill not registered: ${mapping.skillId}")
            learningManager?.recordFailure(
                taskId = task.taskId,
                taskText = task.instruction,
                errorCategory = "CLOUD_SKILL",
                errorCode = "SKILL_NOT_READY",
                recoveryHint = mapping.skillId,
            )
            return CloudTaskExecutionResult.failure(
                message = "Skill not ready: ${mapping.skillId}",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true
            )
        }

        SkillRegistry.onSelection(skill)

        val result = simulateExecute(task, skill, mapping)
        SkillRegistry.updateRuntimeStats(
            skillId = skill.id,
            success = result.success,
            roundSuccess = result.success,
            isFallback = false,
        )
        if (result.success) {
            learningManager?.recordSuccess(task.taskId, task.instruction, result.message)
        } else {
            learningManager?.recordFailure(
                taskId = task.taskId,
                taskText = task.instruction,
                errorCategory = "CLOUD_SKILL",
                errorCode = result.errorCode.name,
                recoveryHint = result.message,
            )
        }
        return result
    }

    /**
     * 模拟执行：验证流程闭环，不真操作手机。
     * 后续可替换为真实 SkillExecutor 调用。
     */
    private fun simulateExecute(
        task: CloudExecutorTask,
        skill: io.agents.pokeclaw.agent.skill.Skill,
        mapping: SkillMapping
    ): CloudTaskExecutionResult {
        XLog.i(TAG, "模拟执行技能: ${skill.id}")

        return when (skill.id) {
            "launch_app" -> {
                val appName = mapping.params["app_name"] ?: "未知应用"
                CloudTaskExecutionResult.success(
                    message = "模拟打开应用: $appName",
                    artifacts = listOf("mock://launch_app/$appName")
                )
            }
            "find_and_tap" -> {
                val target = mapping.params["text"] ?: "未知目标"
                CloudTaskExecutionResult.success(
                    message = "模拟点击: $target",
                    artifacts = listOf("mock://find_and_tap/$target")
                )
            }
            "input_text" -> {
                val text = mapping.params["text"] ?: ""
                CloudTaskExecutionResult.success(
                    message = "模拟输入: $text",
                    artifacts = listOf("mock://input_text/${text.take(20)}")
                )
            }
            "go_back" -> CloudTaskExecutionResult.success(
                message = "模拟返回",
                artifacts = listOf("mock://go_back")
            )
            "search_in_app" -> {
                val query = mapping.params["query"] ?: ""
                CloudTaskExecutionResult.success(
                    message = "模拟搜索: $query",
                    artifacts = listOf("mock://search/$query")
                )
            }
            "copy_screen_text", "screenshot" -> CloudTaskExecutionResult.success(
                message = "模拟截图/获取屏幕文本",
                artifacts = listOf("screenshot://mock_001.png")
            )
            "accept_permission" -> CloudTaskExecutionResult.success(
                message = "模拟允许权限",
                artifacts = listOf("mock://accept_permission")
            )
            "dismiss_popup" -> CloudTaskExecutionResult.success(
                message = "模拟关闭弹窗",
                artifacts = listOf("mock://dismiss_popup")
            )
            "swipe_gesture" -> {
                val direction = mapping.params["direction"] ?: "up"
                CloudTaskExecutionResult.success(
                    message = "模拟滑动: $direction",
                    artifacts = listOf("mock://swipe/$direction")
                )
            }
            else -> CloudTaskExecutionResult.failure(
                message = "未实现的技能: ${skill.id}",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = false
            )
        }
    }

    /**
     * 检查任务是否可在本地确定性执行。
     */
    fun canExecuteLocally(task: CloudExecutorTask): Boolean {
        val mapping = CloudTaskSkillMapper.mapToSkill(task.instruction)
        if (mapping == null) return false
        val skill = CloudTaskSkillMapper.resolveSkill(mapping)
        return skill != null && mapping.confidence >= 0.7
    }
}
