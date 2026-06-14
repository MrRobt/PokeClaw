// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 lobster command channel Retrofit 接口

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.lobster.model.CommandDetailResult
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteReq
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteResp
import io.agents.pokeclaw.cloud.lobster.model.HermesFeedbackReq
import io.agents.pokeclaw.cloud.model.CommonResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Lobster command channel API
 *
 * 3 endpoints:
 * - executeCommand: POST /app-api/claw/app/lobster/command
 * - getCommandResult: GET /app-api/claw/app/lobster/command/{executionId}/result
 * - submitHermesFeedback: POST /app-api/claw/hermes/feedback
 */
interface LobsterCommandApi {

    /**
     * 提交指令
     * POST /app-api/claw/app/lobster/command
     */
    @POST("app-api/claw/app/lobster/command")
    suspend fun executeCommand(@Body req: CommandExecuteReq): Response<CommonResult>

    /**
     * 查询指令执行结果
     * GET /app-api/claw/app/lobster/command/{executionId}/result
     */
    @GET("app-api/claw/app/lobster/command/{executionId}/result")
    suspend fun getCommandResult(@Path("executionId") executionId: String): Response<CommonResult>

    /**
     * 提交 Hermes 反馈
     * POST /app-api/claw/hermes/feedback
     */
    @POST("app-api/claw/hermes/feedback")
    suspend fun submitHermesFeedback(@Body req: HermesFeedbackReq): Response<CommonResult>
}