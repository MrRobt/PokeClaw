// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 Lobster Memory API — 4 endpoints

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryCreateReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryRespVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lobster memory API
 *
 * 4 endpoints:
 * - listMemories: GET /app-api/claw/app/lobster/my/memories
 * - createMemory: POST /app-api/claw/app/lobster/memory
 * - deleteMemory: DELETE /app-api/claw/app/lobster/memory/{id}
 * - clearAllMemories: DELETE /app-api/claw/app/lobster/memory/all
 */
interface LobsterMemoryApi {

    /**
     * 分页查询记忆列表
     * GET /app-api/claw/app/lobster/my/memories
     */
    @GET("app-api/claw/app/lobster/my/memories")
    suspend fun listMemories(
        @Query("memoryType") memoryType: String? = null,
        @Query("pageNo") pageNo: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<CommonResult>

    /**
     * 创建记忆
     * POST /app-api/claw/app/lobster/memory
     */
    @POST("app-api/claw/app/lobster/memory")
    suspend fun createMemory(@Body req: ClawMemoryCreateReqVO): Response<CommonResult>

    /**
     * 删除单条记忆
     * DELETE /app-api/claw/app/lobster/memory/{id}
     */
    @DELETE("app-api/claw/app/lobster/memory/{id}")
    suspend fun deleteMemory(@Path("id") id: String): Response<CommonResult>

    /**
     * 清空所有记忆
     * DELETE /app-api/claw/app/lobster/memory/all
     */
    @DELETE("app-api/claw/app/lobster/memory/all")
    suspend fun clearAllMemories(): Response<CommonResult>
}
