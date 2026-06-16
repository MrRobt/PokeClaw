// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

/**
 * A single row in the Settings "积分与计费" (Credits & Billing) list
 * (US-D-031-SETTINGS-BILLING-SECTION). Mirrors the
 * `billing_vendor_pricing_config` shape from the dyq bundle, with
 * `creditCost` nullable so we can still show placeholders / unknown
 * entries when the cloud returns null.
 *
 * The status enum is *derived* from [creditCost] at construction
 * time but stored so the UI can switch on it without re-deriving:
 *  - `CONFIGURED` — creditCost > 0
 *  - `PLACEHOLDER` — creditCost == 0 (backend has a row but rate
 *    is 0; treat as "set, but free")
 *  - `UNKNOWN` — creditCost == null (API failure / offline)
 */
data class VendorBillingEntry(
    val vendorCode: String,
    val workflowType: String,
    val billingDimension: String,
    val displayName: String,
    val creditCost: Int?,
) {
    val status: Status
        get() = when {
            creditCost == null -> Status.UNKNOWN
            creditCost > 0 -> Status.CONFIGURED
            else -> Status.PLACEHOLDER
        }

    /** Human-friendly status label (used in the activity row). */
    fun statusLabel(): String = when (status) {
        Status.CONFIGURED -> "$creditCost 积分/单位"
        Status.PLACEHOLDER -> "占位（未定价）"
        Status.UNKNOWN -> "未配置"
    }

    enum class Status { CONFIGURED, PLACEHOLDER, UNKNOWN }
}
