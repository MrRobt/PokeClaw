// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 Lobster Personality API — 3 endpoints

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.lobster.model.ClawMoodRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodUpdateReqVO
import io.agents.pokeclaw.cloud.lobster.model.PersonalityTypes
import io.agents.pokeclaw.cloud.model.CommonResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Lobster personality API
 *
 * 3 endpoints:
 * - getPersonality: GET /app-api/claw/app/lobster/my/personality
 * - updatePersonality: PUT /app-api/claw/app/lobster/personality
 * - getPersonalityTypes: GET /app-api/claw/app/lobster/personality/types
 */
interface LobsterPersonalityApi {

    /**
     * 获取当前人格画像
     * GET /app-api/claw/app/lobster/my/personality
     */
    @GET("app-api/claw/app/lobster/my/personality")
    suspend fun getPersonality(): Response<CommonResult>

    /**
     * 更新人格画像
     * PUT /app-api/claw/app/lobster/personality
     */
    @PUT("app-api/claw/app/lobster/personality")
    suspend fun updatePersonality(@Body req: ClawMoodUpdateReqVO): Response<CommonResult>

    /**
     * 获取人格类型列表
     * GET /app-api/claw/app/lobster/personality/types
     */
    @GET("app-api/claw/app/lobster/personality/types")
    suspend fun getPersonalityTypes(): Response<CommonResult>
}
