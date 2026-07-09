// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 Skill Marketplace DTOs

package io.agents.pokeclaw.cloud.lobster.model

import com.google.gson.annotations.SerializedName

/**
 * 技能市场响应
 *
 * @param skillId 技能 ID
 * @param skillName 技能名称
 * @param description 技能描述
 * @param vendor 供应商
 * @param installStatus 安装状态
 * @param version 版本号
 * @param iconUrl 图标 URL
 * @param channelCode 渠道码
 */
data class ClawAppSkillMarketRespVO(
    @SerializedName("skillId") val skillId: String,
    @SerializedName("skillName") val skillName: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("vendor") val vendor: String? = null,
    @SerializedName("installStatus") val installStatus: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("channelCode") val channelCode: String? = null,
    // P0-2 远程 skill 安装：可执行定义(steps JSON)。后端可放独立 definition 字段；
    // 若后端仅返回元数据，安装器会回退用 description 承载定义(mock 约定)。
    @SerializedName("definition") val definition: String? = null,
)

/**
 * 技能保存请求（新建或更新）
 *
 * @param id 可选，更新时传入
 * @param skillName 技能名称
 * @param vendor 供应商
 * @param paramsSchema 参数 schema
 * @param channelCode 渠道码
 */
data class ClawAppSkillSaveReqVO(
    @SerializedName("id") val id: String? = null,
    @SerializedName("skillName") val skillName: String,
    @SerializedName("vendor") val vendor: String? = null,
    @SerializedName("paramsSchema") val paramsSchema: String? = null,
    @SerializedName("channelCode") val channelCode: String? = null,
)

/**
 * 批量更新技能状态请求
 *
 * @param ids 技能 ID 列表
 * @param enable 是否启用
 */
data class BatchSkillStatusReqVO(
    @SerializedName("ids") val ids: List<String>,
    @SerializedName("enable") val enable: Boolean,
)