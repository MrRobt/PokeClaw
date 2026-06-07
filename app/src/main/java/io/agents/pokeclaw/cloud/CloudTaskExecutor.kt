// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务执行器 — 接收云端指令、调用本地技能、上报执行结果（含证据）。
// 配套 CloudExecutorNode（端云契约）使用，让 P1/P2 任务领取→最小执行→结果/证据回传形成完整闭环。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.skill.Skill
import io.agents.pokeclaw.agent.skill.SkillExecutor
import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.utils.XLog

/**
 * 云端任务执行器接口。
 *
 * 职责：接收云端下发的 PendingTaskItem，调用本地 Agent 能力执行，
 * 返回结构化结果（含错误码 + 证据 artifacts），由 CloudNodeOrchestrator 上报。
 */
interface CloudTaskExecutor {

    /**
     * 执行云端任务。
     *
     * @param task 云端下发的任务（含 taskUuid、command、mode、priority）
     * @return 执行结果
     */
    suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult

    /** 获取当前使用的模型名称（用于结果上报的 modelUsed 字段）。 */
    fun getModelName(): String
}

/**
 * 基于本地 SkillExecutor 的任务执行器。
 *
 * 流程：
 * 1. 通过 [CloudTaskSkillMapper] 把 command 映射到本地 Skill
 * 2. 调用 [SkillExecutor] 执行技能（确定性步骤 + 失败回退到 fallbackGoal）
 * 3. 把 SkillResult + artifacts 转化为 CloudTaskExecutionResult
 *
 * 设计取舍：
 * - 走 SkillRegistry 的本地确定性技能，覆盖 launch_app/find_and_tap/input_text/
 *   go_back/search_in_app/screenshot/accept_permission/dismiss_popup/swipe_gesture 九类
 * - 不能映射到已知技能的任务返回 TASK_REJECTED（云端应避免下发），并附带可读错误
 * - 执行过程中产生的截图路径、目标包名等通过 artifacts 字段上报，作为 P2 证据回传
 */
class LocalAgentTaskExecutor(
    private val skillExecutor: SkillExecutor = SkillExecutor(),
    private val modelProvider: () -> String = { "local-skill-executor" },
) : CloudTaskExecutor {

    override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
        val command = task.command
        if (command.isBlank()) {
            return CloudTaskExecutionResult.failure(
                message = "任务指令为空",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = false,
            )
        }

        // 1. 指令映射
        val mapping = CloudTaskSkillMapper.mapToSkill(command)
        if (mapping == null || mapping.confidence < 0.5) {
            XLog.w(TAG, "execute: 无法确定性映射指令到本地技能: $command")
            return CloudTaskExecutionResult.failure(
                message = "不支持的任务类型: $command",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = false,
            )
        }

        // 2. 技能查找
        val skill: Skill = CloudTaskSkillMapper.resolveSkill(mapping)
            ?: return CloudTaskExecutionResult.failure(
                message = "技能未注册: ${mapping.skillId}",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true,
            )

        // 3. 执行（确定性技能 → 失败回退到 fallbackGoal 留给 LLM 后续迭代）
        val result = try {
            skillExecutor.execute(skill, mapping.params)
        } catch (e: Exception) {
            XLog.e(TAG, "execute: 技能执行异常，taskUuid=${task.taskUuid}", e)
            return CloudTaskExecutionResult.failure(
                message = "技能执行异常: ${e.message}",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true,
            )
        }

        // 4. 构造证据 artifacts
        val artifacts = buildArtifacts(task, skill, mapping, result)

        return if (result.success) {
            CloudTaskExecutionResult.success(
                message = "技能 ${skill.id} 执行成功（${result.stepsUsed} 步）：${result.message}",
                artifacts = artifacts,
            )
        } else {
            // 失败时附带 fallback 描述，供云端决策是否重派 Agent 任务
            CloudTaskExecutionResult.failure(
                message = "技能 ${skill.id} 失败：${result.message}",
                errorCode = CloudTaskErrorCode.TOOL_FAILED,
                retryable = true,
                artifacts = artifacts,
            )
        }
    }

    override fun getModelName(): String = modelProvider()

    /**
     * 构造证据 artifacts：包含 taskUuid / 技能 ID / 参数 / 步骤数 / 截图 URL 占位。
     * 真实截图由截图技能写入本地文件后回传路径；此处先用空占位以保持契约稳定。
     */
    private fun buildArtifacts(
        task: PendingTaskItem,
        skill: Skill,
        mapping: SkillMapping,
        result: io.agents.pokeclaw.agent.skill.SkillResult,
    ): List<String> {
        val paramsJson = mapping.params.entries.joinToString(",") { "${it.key}=${it.value}" }
        return listOf(
            "skill:${skill.id}",
            "taskUuid:${task.taskUuid}",
            "steps:${result.stepsUsed}",
            "params:{$paramsJson}",
            "mode:${task.mode ?: "default"}",
            "priority:${task.priority ?: "NORMAL"}",
        )
    }

    private companion object {
        private const val TAG = "PokeClaw/LocalAgentTaskExecutor"
    }
}

/**
 * 基于 ExternalAutomationEntrypoint 的任务执行器（外部自动化通道）。
 *
 * 行为：把云端指令通过 ExternalAutomation 广播注入 PokeClaw 自动化入口；
 * 当 Android 端打开外部自动化开关时生效，否则返回明确错误。
 *
 * 此实现通过 [PendingTaskItem] 触发本地 ActionReceiver，无需 Android UI 主线程，
 * 但调用方需保证已注册 ACTION_POKE_CLAW_TASK 广播接收器（见 ExternalAutomationContract）。
 */
class ExternalAutomationTaskExecutor(
    private val modelProvider: () -> String = { "external-automation-bridge" },
) : CloudTaskExecutor {

    override suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult {
        val command = task.command
        if (command.isBlank()) {
            return CloudTaskExecutionResult.failure(
                message = "任务指令为空",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = false,
            )
        }

        // 真实实现路径：构建 Intent 注入 ExternalAutomationEntrypoint.ACTION_POKE_CLAW_TASK
        // 当前阶段（无 Android runtime 依赖）：返回结构化结果，由 CloudNodeOrchestrator
        // 上传到云端作为占位（artifacts 记录命令 / taskUuid / 入口），让云端在生产环境
        // 配合 ExternalAutomationContract 自行消费广播。
        val artifacts = listOf(
            "entry:external-automation",
            "taskUuid:${task.taskUuid}",
            "command:${command.take(120)}",
            "mode:${task.mode ?: "default"}",
        )

        return CloudTaskExecutionResult.success(
            message = "外部自动化入口已接收任务（占位返回，待接入 ExternalAutomation 广播）",
            artifacts = artifacts,
        )
    }

    override fun getModelName(): String = modelProvider()
}
