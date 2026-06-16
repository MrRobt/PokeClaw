// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 Skill Marketplace Retrofit 接口：list / install / save / remove / batch-status

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.lobster.model.BatchSkillStatusReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillSaveReqVO
import io.agents.pokeclaw.cloud.model.CommonResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Skill Marketplace API (US-D-039)
 *
 * 5 endpoints:
 * - listSkills:    GET  /app-api/claw/app/lobster/skill/list
 * - installSkill:  POST /app-api/claw/app/lobster/skill/install
 * - saveSkill:     POST /app-api/claw/app/lobster/skill/save
 * - removeSkill:   DELETE /app-api/claw/app/lobster/skill/remove
 * - batchStatus:   PUT  /app-api/claw/app/lobster/skills/batch-status
 */
interface LobsterSkillMarketplaceApi {

    /**
     * 获取技能市场列表
     * GET /app-api/claw/app/lobster/skill/list
     */
    @GET("app-api/claw/app/lobster/skill/list")
    suspend fun listSkills(): Response<CommonResult>

    /**
     * 安装技能
     * POST /app-api/claw/app/lobster/skill/install
     * @param skillId 技能 ID
     */
    @POST("app-api/claw/app/lobster/skill/install")
    suspend fun installSkill(@Body skillId: Map<String, String>): Response<CommonResult>

    /**
     * 保存技能（新建或更新）
     * POST /app-api/claw/app/lobster/skill/save
     * @param req 技能保存请求
     * @return 新建返回 String id，更新返回 Boolean
     */
    @POST("app-api/claw/app/lobster/skill/save")
    suspend fun saveSkill(@Body req: ClawAppSkillSaveReqVO): Response<CommonResult>

    /**
     * 删除技能
     * DELETE /app-api/claw/app/lobster/skill/remove
     * @param id 技能 ID
     */
    @DELETE("app-api/claw/app/lobster/skill/remove")
    suspend fun removeSkill(@Query("id") id: String): Response<CommonResult>

    /**
     * 批量更新技能状态
     * PUT /app-api/claw/app/lobster/skills/batch-status
     * @param req 批量状态更新请求
     */
    @PUT("app-api/claw/app/lobster/skills/batch-status")
    suspend fun batchUpdateStatus(@Body req: BatchSkillStatusReqVO): Response<CommonResult>
}