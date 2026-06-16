// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-041 lobster profile API: my + stats + executions + skills + suggestions

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.lobster.model.ClawAppExecutionRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterStatsRespVO
import io.agents.pokeclaw.cloud.lobster.model.SuggestionResult
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lobster profile API
 *
 * 5 endpoints:
 * - getMy: GET /app-api/claw/app/lobster/my
 * - getStats: GET /app-api/claw/app/lobster/my/stats
 * - getExecutions: GET /app-api/claw/app/lobster/my/executions
 * - getMySkills: GET /app-api/claw/app/lobster/my/skills
 * - getMySuggestions: GET /app-api/claw/app/lobster/my/suggestions
 */
interface LobsterProfileApi {

    /**
     * 获取当前用户 Lobster 信息（含 next level experience）
     * GET /app-api/claw/app/lobster/my
     */
    @GET("app-api/claw/app/lobster/my")
    suspend fun getMy(): Response<CommonResult>

    /**
     * 获取 Lobster 统计数据
     * GET /app-api/claw/app/lobster/my/stats
     */
    @GET("app-api/claw/app/lobster/my/stats")
    suspend fun getStats(): Response<CommonResult>

    /**
     * 获取执行记录列表
     * GET /app-api/claw/app/lobster/my/executions
     *
     * @param skillId 可选，技能 ID 过滤
     * @param pageNo 页码（从 1 开始）
     * @param pageSize 每页条数
     */
    @GET("app-api/claw/app/lobster/my/executions")
    suspend fun getExecutions(
        @Query("skillId") skillId: String?,
        @Query("pageNo") pageNo: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<CommonResult>

    /**
     * 获取已安装技能列表
     * GET /app-api/claw/app/lobster/my/skills
     */
    @GET("app-api/claw/app/lobster/my/skills")
    suspend fun getMySkills(): Response<CommonResult>

    /**
     * 获取建议列表
     * GET /app-api/claw/app/lobster/my/suggestions
     */
    @GET("app-api/claw/app/lobster/my/suggestions")
    suspend fun getMySuggestions(): Response<CommonResult>
}