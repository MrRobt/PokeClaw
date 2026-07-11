// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// V1.0 Vendor Billing Pricing Retrofit 接口
// 契约: api-contracts/skill-market-api.md §2.1

package io.agents.pokeclaw.cloud.lobster.api

import io.agents.pokeclaw.cloud.model.CommonResult
import retrofit2.Response
import retrofit2.http.GET

/**
 * Vendor Billing Pricing API (V1.0)
 *
 *  - GET /app-api/claw/app/billing/pricing/list → 返回 [CommonResult.data] 为 List<DTO>
 *
 * <p>注意：data 字段是运行时 [Any]（CommonResult 不带类型参数），客户端在 [io.agents.pokeclaw.cloud.lobster.client.BillingPricingClient]
 * 里做实际类型转换。沿用 [io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient] 的模式。
 *
 * <p>⚠️ dyq `WebProperties` 对 `**.controller.app.**` 包**自动**加 `/app-api` 前缀，
 * 所以 `@GET` 路径必须含 `/app-api/`。不要按「手填裸路径」写。
 */
interface LobsterBillingApi {

    /**
     * 获取供应商定价列表
     * GET /app-api/claw/app/billing/pricing/list
     */
    @GET("app-api/claw/app/billing/pricing/list")
    suspend fun listPricing(): Response<CommonResult>
}
