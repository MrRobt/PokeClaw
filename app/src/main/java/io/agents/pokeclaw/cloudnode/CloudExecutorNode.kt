// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端执行节点引擎：整合接收、执行、上报的完整闭环。

package io.agents.pokeclaw.cloudnode

import io.agents.pokeclaw.utils.XLog

/**
 * 云端执行节点引擎。
 * 整合任务接收、状态上报、执行桥接，提供端到端最小闭环。
 */
class CloudExecutorNode(
    private val deviceId: String,
    private val clock: CloudExecutorClock = SystemCloudExecutorClock,
    private val executorBridge: CloudTaskExecutorBridge = CloudTaskExecutorBridge(),
) {
    private val TAG = "CloudExecutorNode"
    private val simulator = CloudExecutorNodeSimulator(clock)

    /**
     * 任务执行监听器。
     */
    interface TaskExecutionListener {
        fun onStatusReport(report: CloudTaskStatusReport)
    }

    private val listeners = mutableListOf<TaskExecutionListener>()

    fun addListener(listener: TaskExecutionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: TaskExecutionListener) {
        listeners.remove(listener)
    }

    /**
     * 接收并执行云端任务。
     * 返回完整的状态上报序列。
     */
    fun receiveAndExecute(
        taskId: String,
        instruction: String,
        traceId: String? = null,
        timeoutMillis: Long = 30000L,
    ): List<CloudTaskStatusReport> {
        val task = CloudExecutorTask(
            taskId = taskId,
            deviceId = deviceId,
            instruction = instruction,
            traceId = traceId,
            issuedAtMillis = clock.nowMillis(),
            timeoutMillis = timeoutMillis,
        )

        XLog.i(TAG, "接收任务: $taskId, 指令: $instruction")

        // 使用模拟器驱动状态流，桥接层执行实际逻辑
        val reports = simulator.simulate(task) { cloudTask ->
            executorBridge.execute(cloudTask)
        }

        // 通知监听器
        reports.forEach { report ->
            listeners.forEach { it.onStatusReport(report) }
        }

        val finalReport = reports.last()
        XLog.i(TAG, "任务完成: $taskId, 状态: ${finalReport.status}")

        return reports
    }

    /**
     * 检查指令是否可本地执行。
     */
    fun canHandle(instruction: String): Boolean {
        val mapping = CloudTaskSkillMapper.mapToSkill(instruction)
        return mapping != null && mapping.confidence >= 0.7
    }

    /**
     * 获取支持的动作类型列表（用于能力上报）。
     */
    fun getSupportedActions(): List<String> {
        return listOf(
            "launch_app",
            "find_and_tap",
            "input_text",
            "go_back",
            "search_in_app",
            "screenshot",
            "accept_permission",
            "dismiss_popup",
            "swipe_gesture"
        )
    }
}
