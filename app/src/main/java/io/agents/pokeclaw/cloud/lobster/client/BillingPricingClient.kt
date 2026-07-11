// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// V1.0 Vendor Billing Pricing client
// 契约: api-contracts/skill-market-api.md §2.1

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterBillingApi
import io.agents.pokeclaw.cloud.lobster.model.ClawAppBillingPricingRespVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog
import retrofit2.Response

/**
 * Vendor Billing Pricing client
 *
 * 把 Retrofit 返回的 [Response] 解封成 sealed [Result]，UI 层只关心业务结果。
 *
 * @param api The underlying Retrofit API
 */
class BillingPricingClient(
    private val api: LobsterBillingApi,
) {
    private val tag = "BillingPricingClient"

    sealed class Result {
        data class OkList(val entries: List<ClawAppBillingPricingRespVO>) : Result()
        data class Rejected(val message: String) : Result()
    }

    /**
     * 获取供应商定价列表
     */
    suspend fun listPricing(): Result {
        XLog.d(tag, "listPricing: fetching pricing list")
        val resp: Response<CommonResult> = try {
            api.listPricing()
        } catch (e: Exception) {
            XLog.e(tag, "listPricing: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        // data 是运行时 Any；按 List<DTO> 转换
        @Suppress("UNCHECKED_CAST")
        val rawList = body.data as? List<*>
        // Fix code-review M2：转换失败时 XLog.w 警告，避免静默丢错
        val list: List<ClawAppBillingPricingRespVO> = rawList?.mapNotNull { item ->
            (item as? ClawAppBillingPricingRespVO) ?: run {
                XLog.w(
                    tag,
                    "listPricing: dropped item with unexpected type=${item?.javaClass?.simpleName ?: "null"}"
                )
                null
            }
        } ?: emptyList()
        XLog.i(tag, "listPricing: fetched ${list.size} entries")
        return Result.OkList(list)
    }
}
