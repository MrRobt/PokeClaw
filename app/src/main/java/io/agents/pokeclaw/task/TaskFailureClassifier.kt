// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.task

import io.agents.pokeclaw.cloudnode.CloudTaskErrorCode
import io.agents.pokeclaw.utils.XLog

/**
 * 任务失败分类器：把 errorCode / errorMessage 归类到 3 大类，便于 UI 渲染差异化的失败提示。
 *
 * 分类：
 *  - ENVIRONMENT（环境异常）：网络/权限/服务未启用
 *  - BUSINESS（业务异常）：任务被拒绝 / 指令格式错 / 目标 app 状态错
 *  - SYSTEM（系统异常）：超时 / 内存 / 未知错误
 */
object TaskFailureClassifier {

    private const val TAG = "PokeClaw/FailureClass"

    enum class Category(val displayName: String) {
        ENVIRONMENT("Environment"),
        BUSINESS("Business"),
        SYSTEM("System"),
    }

    data class Classification(
        val category: Category,
        val humanMessage: String,
        val suggestedActions: List<String>,
    )

    /**
     * 分类一个错误。
     */
    fun classify(errorCode: String?, errorMessage: String?): Classification {
        val code = errorCode?.uppercase()
        val msg = errorMessage?.lowercase() ?: ""
        val (category, reason) = when {
            code == CloudTaskErrorCode.NETWORK_UNAVAILABLE.name -> Category.ENVIRONMENT to "network_unavailable"
            code == CloudTaskErrorCode.PERMISSION_MISSING.name -> Category.ENVIRONMENT to "permission_missing"
            code == CloudTaskErrorCode.TASK_REJECTED.name -> Category.BUSINESS to "task_rejected"
            code == CloudTaskErrorCode.TOOL_FAILED.name -> Category.BUSINESS to "tool_failed"
            code == CloudTaskErrorCode.EXECUTION_TIMEOUT.name -> Category.SYSTEM to "execution_timeout"
            msg.contains("timeout") -> Category.SYSTEM to "timeout"
            msg.contains("network") || msg.contains("unreachable") -> Category.ENVIRONMENT to "network"
            msg.contains("permission") || msg.contains("denied") -> Category.ENVIRONMENT to "permission"
            else -> Category.SYSTEM to "unknown"
        }
        val (human, actions) = humanize(category, reason, errorMessage)
        return Classification(category, human, actions)
    }

    private fun humanize(category: Category, reason: String, rawMessage: String?): Pair<String, List<String>> {
        return when (reason) {
            "network_unavailable", "network" -> Pair(
                "Network unavailable. Check Wi-Fi or cellular connection.",
                listOf("Retry", "Switch to local Gemma", "Check network")
            )
            "permission_missing" -> Pair(
                "Required permission is missing. PokeClaw can't operate without it.",
                listOf("Open Accessibility Settings", "Contact Support")
            )
            "task_rejected" -> Pair(
                "Task instruction was rejected. Format may be invalid.",
                listOf("Resend with clearer wording", "Cancel")
            )
            "tool_failed" -> Pair(
                "A tool failed during execution. Target app may not be ready.",
                listOf("Retry", "Switch to another app", "Human Takeover")
            )
            "timeout", "execution_timeout" -> Pair(
                "Task took too long and was timed out.",
                listOf("Retry", "Simplify task", "Increase timeout")
            )
            else -> Pair(
                "Unknown error: ${rawMessage ?: "no detail"}",
                listOf("Retry", "Human Takeover", "Contact Support")
            )
        }.let {
            XLog.d(TAG, "classify: category=$category reason=$reason human=${it.first.take(40)}")
            it
        }
    }

    /**
     * 检查是否连续失败超过 [threshold] 次。
     */
    fun isConsecutiveFailure(recentFailures: List<String?>, threshold: Int = 3): Boolean {
        return recentFailures.takeLast(threshold).all { it != null }
    }
}
