// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-041 lobster profile DTOs: lobster info, stats, executions, skills, suggestions

package io.agents.pokeclaw.cloud.lobster.model

import com.google.gson.annotations.SerializedName

/**
 * Lobster 用户信息
 *
 * @param id Lobster 唯一 ID
 * @param nickname 昵称
 * @param level 当前等级
 * @param currentExp 当前经验值
 * @param nextLevelExp 下一级所需经验值
 * @param avatar 头像 URL
 * @param createdAt 创建时间戳（毫秒）
 */
data class ClawLobsterRespVO(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("level") val level: Int,
    @SerializedName("currentExp") val currentExp: Long,
    @SerializedName("nextLevelExp") val nextLevelExp: Long,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("createdAt") val createdAt: Long,
)

/**
 * Lobster 统计数据
 *
 * @param totalExecutions 总执行次数
 * @param successRate 成功率（0.0 ~ 1.0）
 * @param totalCreditConsumed 总消耗积分
 * @param last7DaysExecutions 近 7 天执行次数
 * @param last30DaysExecutions 近 30 天执行次数
 */
data class ClawLobsterStatsRespVO(
    @SerializedName("totalExecutions") val totalExecutions: Long,
    @SerializedName("successRate") val successRate: Double,
    @SerializedName("totalCreditConsumed") val totalCreditConsumed: Long,
    @SerializedName("last7DaysExecutions") val last7DaysExecutions: Long,
    @SerializedName("last30DaysExecutions") val last30DaysExecutions: Long,
)

/**
 * 执行记录
 *
 * @param executionId 执行 ID
 * @param skillId 技能 ID
 * @param skillName 技能名称
 * @param status 状态（PENDING / RUNNING / SUCCESS / FAILED / CANCELLED / TIMEOUT）
 * @param startedAt 开始时间戳（毫秒）
 * @param completedAt 完成时间戳（毫秒），可空
 * @param durationMs 执行时长（毫秒）
 * @param creditConsumed 消耗积分
 * @param resultSnippet 结果摘要
 */
data class ClawAppExecutionRespVO(
    @SerializedName("executionId") val executionId: String,
    @SerializedName("skillId") val skillId: String,
    @SerializedName("skillName") val skillName: String,
    @SerializedName("status") val status: String,
    @SerializedName("startedAt") val startedAt: Long,
    @SerializedName("completedAt") val completedAt: Long?,
    @SerializedName("durationMs") val durationMs: Long,
    @SerializedName("creditConsumed") val creditConsumed: Long,
    @SerializedName("resultSnippet") val resultSnippet: String?,
)

/**
 * 技能信息
 *
 * @param skillId 技能 ID
 * @param skillName 技能名称
 * @param version 版本号
 * @param installStatus 安装状态（INSTALLED / NOT_INSTALLED / INSTALLING）
 * @param lastUsedAt 最后使用时间戳（毫秒），可空
 */
data class ClawAppSkillRespVO(
    @SerializedName("skillId") val skillId: String,
    @SerializedName("skillName") val skillName: String,
    @SerializedName("version") val version: String,
    @SerializedName("installStatus") val installStatus: String,
    @SerializedName("lastUsedAt") val lastUsedAt: Long?,
)

/**
 * 建议结果
 *
 * @param suggestions 建议列表
 */
data class SuggestionResult(
    @SerializedName("suggestions") val suggestions: List<Suggestion>,
)

/**
 * 单条建议
 *
 * @param type 建议类型
 * @param title 标题
 * @param description 描述
 * @param actionUrl 操作链接，可空
 * @param priority 优先级（数值越大越高）
 */
data class Suggestion(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("actionUrl") val actionUrl: String?,
    @SerializedName("priority") val priority: Int,
)