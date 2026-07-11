// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// V1.0 Vendor Billing Pricing DTOs（对接 dyq /claw/app/billing/pricing/list）
// 契约: api-contracts/skill-market-api.md §2.1

package io.agents.pokeclaw.cloud.lobster.model

import com.google.gson.annotations.SerializedName

/**
 * 供应商定价 DTO（与 dyq ClawAppBillingPricingRespVO 字段一致，snake_case）。
 */
data class ClawAppBillingPricingRespVO(
    @SerializedName("vendorCode") val vendorCode: String,
    @SerializedName("workflowType") val workflowType: String,
    @SerializedName("billingDimension") val billingDimension: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("creditCost") val creditCost: Int? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("status") val status: String? = null,
)
