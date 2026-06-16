// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 Memory + Personality DTOs

package io.agents.pokeclaw.cloud.lobster.model

import com.google.gson.annotations.SerializedName

/**
 * 记忆响应
 *
 * @param id 记忆唯一 ID
 * @param content 记忆内容文本
 * @param memoryType 记忆类型（如 CHAT/HABIT/PREFERENCE/INTERACTION）
 * @param createdAt 创建时间戳（毫秒）
 * @param updatedAt 更新时间戳（毫秒）
 * @param tags 标签列表
 */
data class ClawMemoryRespVO(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("memoryType") val memoryType: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("tags") val tags: List<String>? = null,
)

/**
 * 创建记忆请求
 *
 * @param content 记忆内容文本
 * @param memoryType 记忆类型
 * @param tags 标签列表
 */
data class ClawMemoryCreateReqVO(
    @SerializedName("content") val content: String,
    @SerializedName("memoryType") val memoryType: String,
    @SerializedName("tags") val tags: List<String>? = null,
)

/**
 * 人格/心情响应
 *
 * @param mood 当前心情（如 HAPPY/CALM/ANXIOUS/SAD/EXCITED）
 * @param intensity 心情强度 1-10
 * @param traits 各维度分值 map（dimension → value）
 * @param updatedAt 更新时间戳（毫秒）
 */
data class ClawMoodRespVO(
    @SerializedName("mood") val mood: String,
    @SerializedName("intensity") val intensity: Int,
    @SerializedName("traits") val traits: Map<String, Int>? = null,
    @SerializedName("updatedAt") val updatedAt: Long,
)

/**
 * 更新心情请求
 *
 * @param mood 心情
 * @param intensity 心情强度 1-10
 * @param traits 各维度分值 map
 */
data class ClawMoodUpdateReqVO(
    @SerializedName("mood") val mood: String,
    @SerializedName("intensity") val intensity: Int,
    @SerializedName("traits") val traits: Map<String, Int>? = null,
)

/**
 * 人格类型定义
 *
 * @param dimensions 各维度列表（warmth/formality/humor/empathy/verbosity）
 */
data class PersonalityTypes(
    @SerializedName("dimensions") val dimensions: List<PersonalityDimension>
)

/**
 * 单个人格维度
 *
 * @param id 维度 ID
 * @param label 维度标签名称
 * @param rangeMin 最小值
 * @param rangeMax 最大值
 */
data class PersonalityDimension(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("rangeMin") val rangeMin: Int,
    @SerializedName("rangeMax") val rangeMax: Int,
)
