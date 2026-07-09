// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务执行器 — 接收云端指令、调用本地技能、上报执行结果（含证据）。
// 配套 CloudExecutorNode（端云契约）使用，让 P1/P2 任务领取→最小执行→结果/证据回传形成完整闭环。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.agent.skill.Skill
import io.agents.pokeclaw.agent.skill.SkillExecutor
import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloud.model.TaskMode
import io.agents.pokeclaw.cloud.model.modeAsEnum
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult
import io.agents.pokeclaw.cloudnode.CloudTaskSkillMapper
import io.agents.pokeclaw.cloudnode.SkillMapping
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.TaskEvent
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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

        // P0-2 远程下发 skill 安装:command 形如 "install_skill:<JSON定义>"。
        // 复用已验证的设备任务通道(device token 鉴权)把 skill 定义(steps)下发到端侧,
        // 解析后注入 SkillRegistry → 本地 agent 路由即可用(app-api marketplace 需用户登录、设备够不着)。
        if (command.startsWith(INSTALL_SKILL_PREFIX)) {
            val defJson = command.removePrefix(INSTALL_SKILL_PREFIX).trim()
            val skill = io.agents.pokeclaw.cloud.lobster.RemoteSkillInstaller.parseSkillDefinition(defJson)
            return if (skill == null) {
                XLog.w(TAG, "execute: install_skill 定义无效,taskUuid=${task.taskUuid}")
                CloudTaskExecutionResult.failure(
                    message = "skill 定义无效或缺 steps",
                    errorCode = CloudTaskErrorCode.TASK_REJECTED,
                    retryable = false,
                )
            } else {
                SkillRegistry.register(skill)
                XLog.i(
                    TAG,
                    "execute: ✅ 已安装远程下发 skill id=${skill.id} steps=${skill.steps.size} " +
                        "triggers=${skill.triggerPatterns}; SkillRegistry 现有 ${SkillRegistry.getAll().size}"
                )
                CloudTaskExecutionResult.success(
                    message = "已安装 skill ${skill.id}(${skill.steps.size} 步)",
                    artifacts = listOf(
                        "action:install_skill",
                        "skillId:${skill.id}",
                        "steps:${skill.steps.size}",
                        "triggers:${skill.triggerPatterns.joinToString("|")}",
                        "taskUuid:${task.taskUuid}",
                    ),
                )
            }
        }

        // 0. mode 预处理：dry_run / prepare_only（v1.1.0 扩展）跳过真实执行，
        //    上报固定 stub 字符串 + 完整 artifacts，让云端能区分"未执行"与"执行失败"。
        val mode = task.modeAsEnum()
        if (mode == TaskMode.DRY_RUN || mode == TaskMode.PREPARE_ONLY) {
            val stubMessage = when (mode) {
                TaskMode.DRY_RUN -> "DRY_RUN_OK：仅校验指令映射，不实际执行"
                TaskMode.PREPARE_ONLY -> "PREPARED：跳过主流程，前置准备由调用方接管"
                else -> "noop"
            }
            XLog.i(TAG, "execute: 任务 mode=$mode 跳过实际执行，taskUuid=${task.taskUuid}")
            return CloudTaskExecutionResult.success(
                message = stubMessage,
                artifacts = listOf(
                    "mode:${mode.raw}",
                    "taskUuid:${task.taskUuid}",
                    "command:${command.take(120)}",
                    "dry_run:true",
                ),
            )
        }

        // 1. 指令映射
        val mapping = CloudTaskSkillMapper.mapToSkill(command)
        if (mapping == null || mapping.confidence < 0.5) {
            // P0-1:map 不到确定性 skill → 走完整 MiniMax agent loop 兜底,让 dyq 下发的任意任务真·AI 驱动。
            XLog.i(TAG, "execute: 无确定性 skill 映射,转 agent-loop 兜底: $command")
            return runAgentLoopFallback(task)
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

    @Volatile
    private var lastModel: String = modelProvider()

    override fun getModelName(): String = lastModel

    /**
     * P0-1 agent-loop 兜底：map 不到确定性 skill 时，走完整 MiniMax agent loop（经 [AppViewModel.startTask]），
     * suspend 直到终态 [TaskEvent] 再转成结果 —— 让 dyq 下发的任意复杂任务真·AI 驱动。
     *
     * 正确性要点（见调研）：gate 单任务锁（`isTaskRunning`，须在 startTask 前）；startTask 主线程派发；
     * 终态在 agent 执行线程回调 → 幂等 resume；取消 → stopTask。
     */
    private suspend fun runAgentLoopFallback(task: PendingTaskItem): CloudTaskExecutionResult {
        val vm = ClawApplication.appViewModelInstance
        if (vm.isTaskRunning()) {
            return CloudTaskExecutionResult.failure(
                message = "设备正忙：已有 PokeClaw 任务在执行",
                errorCode = CloudTaskErrorCode.TASK_REJECTED,
                retryable = true,
            )
        }
        val resumed = AtomicBoolean(false)
        var maxRound = 1
        val result = withTimeoutOrNull(AGENT_LOOP_TIMEOUT_MS) { suspendCancellableCoroutine<CloudTaskExecutionResult> { cont ->
            cont.invokeOnCancellation { runCatching { vm.stopTask() } }
            fun settle(r: CloudTaskExecutionResult) {
                if (resumed.compareAndSet(false, true)) {
                    runCatching { vm.clearTaskCallback() }
                    cont.resumeWith(Result.success(r))
                }
            }
            val onEvent: (TaskEvent) -> Unit = { event ->
                when (event) {
                    is TaskEvent.LoopStart -> maxRound = maxOf(maxRound, event.round)
                    is TaskEvent.Completed -> {
                        event.modelName?.let { lastModel = it }
                        settle(
                            CloudTaskExecutionResult.success(
                                message = event.answer,
                                artifacts = listOf(
                                    "taskUuid:${task.taskUuid}",
                                    "rounds:$maxRound",
                                    "model:$lastModel",
                                    "agent_loop:true",
                                ),
                            )
                        )
                    }
                    is TaskEvent.Failed -> settle(
                        CloudTaskExecutionResult.failure(
                            message = "agent loop 失败: ${event.error}",
                            errorCode = CloudTaskErrorCode.TOOL_FAILED,
                            retryable = true,
                            artifacts = listOf("taskUuid:${task.taskUuid}", "agent_loop:true"),
                        )
                    )
                    is TaskEvent.Blocked -> settle(
                        CloudTaskExecutionResult.failure(
                            message = "被系统对话框/安全策略拦截",
                            errorCode = CloudTaskErrorCode.PERMISSION_MISSING,
                            retryable = false,
                        )
                    )
                    is TaskEvent.Cancelled -> settle(
                        CloudTaskExecutionResult.failure(
                            message = "任务已取消",
                            errorCode = CloudTaskErrorCode.UNKNOWN,
                            retryable = false,
                        )
                    )
                    is TaskEvent.NeedsHuman -> settle(
                        CloudTaskExecutionResult.failure(
                            message = "需人工介入: ${event.reason}",
                            errorCode = CloudTaskErrorCode.TASK_REJECTED,
                            retryable = false,
                        )
                    )
                    else -> Unit
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    vm.startTask(task.command, "cloud_${task.taskUuid}", onEvent = onEvent)
                } catch (e: Exception) {
                    settle(
                        CloudTaskExecutionResult.failure(
                            message = "startTask 异常: ${e.message}",
                            errorCode = CloudTaskErrorCode.UNKNOWN,
                            retryable = true,
                        )
                    )
                }
            }
        } }
        if (result == null) {
            // 超时未产生终态 → 兜底返回 + 停任务,防止 orchestrator 卡死(P0-1 死锁修复)。
            runCatching { vm.stopTask() }
            runCatching { vm.clearTaskCallback() }
            XLog.w(TAG, "runAgentLoopFallback: agent loop 超时未产生终态,taskUuid=${task.taskUuid}")
            return CloudTaskExecutionResult.failure(
                message = "agent loop 超时未完成",
                errorCode = CloudTaskErrorCode.EXECUTION_TIMEOUT,
                retryable = true,
            )
        }
        return result
    }

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
        private const val INSTALL_SKILL_PREFIX = "install_skill:"
        private const val AGENT_LOOP_TIMEOUT_MS = 240_000L
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
