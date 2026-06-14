// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 lobster command channel DTOs: execute, result, feedback

package io.agents.pokeclaw.cloud.lobster.model

import com.google.gson.annotations.SerializedName

/**
 * 指令提交请求
 *
 * @param command 指令文本（如"打开微信"）
 * @param skillId 可选，指定技能 ID
 * @param context 可选，扩展上下文 map
 */
data class CommandExecuteReq(
    @SerializedName("command") val command: String,
    @SerializedName("skillId") val skillId: String? = null,
    @SerializedName("context") val context: Map<String, Any?>? = null,
)

/**
 * 指令提交响应
 *
 * @param executionId 本次执行的唯一 ID，用于后续查询结果
 * @param status 初始状态，通常为 PENDING
 */
data class CommandExecuteResp(
    @SerializedName("executionId") val executionId: String,
    @SerializedName("status") val status: String,
)

/**
 * 命令执行结果详情
 *
 * @param status 终态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED / TIMEOUT
 * @param result 可选，成功时的结果内容
 * @param errorMessage 可选，失败时的错误信息
 * @param progressPercent 可选，0-100 进度百分比
 */
data class CommandDetailResult(
    @SerializedName("status") val status: String,
    @SerializedName("result") val result: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("progressPercent") val progressPercent: Int? = null,
)

/**
 * Hermes 反馈提交请求
 *
 * @param feedbackType 反馈类型（如"task_complete" / "error"）
 * @param payload 可选，扩展数据 map
 * @param taskUuid 可选，关联的任务 ID
 */
data class HermesFeedbackReq(
    @SerializedName("feedbackType") val feedbackType: String,
    @SerializedName("payload") val payload: Map<String, Any?>? = null,
    @SerializedName("taskUuid") val taskUuid: String? = null,
)