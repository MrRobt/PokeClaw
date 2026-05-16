// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务执行器 — 将云端下发的任务指令转化为本地 Agent 执行。

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.cloud.model.PendingTaskItem
import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.cloudnode.CloudTaskExecutionResult

/**
 * 云端任务执行器接口。
 *
 * 职责：接收云端下发的任务指令，通过本地 Agent 能力执行，返回执行结果。
 * 实现类负责：
 * - 将 PendingTaskItem.command 转化为 AgentService 可理解的 prompt
 * - 调用 AgentService 或 ExternalAutomationEntrypoint 执行
 * - 将执行结果转化为 CloudTaskExecutionResult
 */
interface CloudTaskExecutor {

    /**
     * 执行云端任务。
     *
     * @param task 云端下发的任务
     * @return 执行结果
     */
    suspend fun execute(task: PendingTaskItem): CloudTaskExecutionResult

    /** 获取当前使用的模型名称（用于结果上报的 modelUsed 字段）。 */
    fun getModelName(): String
}

/**
 * 基于本地 AgentService 的任务执行器。
 *
 * 将云端 command 包装为 prompt，通过 DefaultAgentService 执行。
 * 此实现需要 Android 运行时环境（无障碍服务、UI 线程），不可在纯 JVM 测试中使用。
 */
class LocalAgentTaskExecutor(
    private val modelProvider: () -> String = { "local" },
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

        // TODO: 接入 DefaultAgentService 执行任务
        // 当前阶段：仅返回一个占位结果，标记为未实现
        // 后续实现路径：
        // 1. 通过 AgentServiceFactory 创建/复用 AgentService 实例
        // 2. 包装 command 为 Agent 可识别的 prompt
        // 3. 调用 executeTask(prompt, callback) 执行
        // 4. 收集 callback 结果转化为 CloudTaskExecutionResult
        return CloudTaskExecutionResult.failure(
            message = "本地执行器尚未接入 AgentService，任务指令：$command",
            errorCode = CloudTaskErrorCode.UNKNOWN,
            retryable = false,
        )
    }

    override fun getModelName(): String = modelProvider()
}

/**
 * 基于 ExternalAutomationEntrypoint 的任务执行器。
 *
 * 通过广播方式将任务注入 PokeClaw 的自动化入口点。
 * 此实现需要 Android 运行时环境，不可在纯 JVM 测试中使用。
 */
class ExternalAutomationTaskExecutor(
    private val modelProvider: () -> String = { "external" },
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

        // TODO: 通过 ExternalAutomationEntrypoint 注入任务
        // 当前阶段：仅返回占位结果
        return CloudTaskExecutionResult.failure(
            message = "外部自动化执行器尚未接入，任务指令：$command",
            errorCode = CloudTaskErrorCode.UNKNOWN,
            retryable = false,
        )
    }

    override fun getModelName(): String = modelProvider()
}
