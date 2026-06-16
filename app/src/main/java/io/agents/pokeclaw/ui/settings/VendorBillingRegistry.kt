// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

/**
 * Hardcoded list of AIGC billing vendors shown in the Settings
 * "积分与计费" section (US-D-031-SETTINGS-BILLING-SECTION).
 *
 * The 4 rows mirror the placeholder seeds the dyq backend ships
 * with in `billing_vendor_pricing_config` (bundle 2026-06-10,
 * A3-10/11/12):
 *  - `cloudphone / * / duration`
 *  - `digital_human / live_virtual_human / duration`
 *  - `digital_human / live_virtual_human / call`
 *  - `cs_ai / * / token`
 *
 * All `creditCost` values are intentionally `null` here so the UI
 * falls into the "未配置" state until the cloud wires up the real
 * pricing. The remote fetcher is opt-in via [withRemote] for
 * callers that want to overlay cloud values when available.
 */
object VendorBillingRegistry {

    private const val TAG = "VendorBillingRegistry"

    /** The 4 hardcoded seed rows. The order is stable (UI relies on it). */
    val SEED: List<VendorBillingEntry> = listOf(
        VendorBillingEntry(
            vendorCode = "cloudphone",
            workflowType = "*",
            billingDimension = "duration",
            displayName = "云手机 · 按时长",
            creditCost = null,
        ),
        VendorBillingEntry(
            vendorCode = "digital_human",
            workflowType = "live_virtual_human",
            billingDimension = "duration",
            displayName = "数字人 · 直播按时长",
            creditCost = null,
        ),
        VendorBillingEntry(
            vendorCode = "digital_human",
            workflowType = "live_virtual_human",
            billingDimension = "call",
            displayName = "数字人 · 通话按次数",
            creditCost = null,
        ),
        VendorBillingEntry(
            vendorCode = "cs_ai",
            workflowType = "*",
            billingDimension = "token",
            displayName = "CS-AI · 按 token",
            creditCost = null,
        ),
    )

    /** All currently-known entries, after applying any remote overlay. */
    @Volatile
    private var live: List<VendorBillingEntry> = SEED

    /** Reset to seed values (used in tests). */
    fun resetForTests() {
        live = SEED
    }

    fun all(): List<VendorBillingEntry> = live

    /**
     * Optional remote overlay. The cloud may return a partial /
     * sparse list keyed by (vendorCode, workflowType, dimension);
     * we patch matching rows in [live] in place, leaving unknown
     * seeds untouched so the UI never shrinks.
     */
    fun withRemote(remote: List<VendorBillingEntry>?) {
        if (remote.isNullOrEmpty()) return
        val byKey = remote.associateBy { keyOf(it) }
        val merged = live.map { local ->
            byKey[keyOf(local)] ?: local
        }
        live = merged
    }

    /**
     * Look up a single vendor by (vendorCode, workflowType,
     * billingDimension). Returns null if no matching row exists.
     */
    fun find(
        vendorCode: String,
        workflowType: String,
        billingDimension: String,
    ): VendorBillingEntry? = live.firstOrNull {
        it.vendorCode == vendorCode &&
            it.workflowType == workflowType &&
            it.billingDimension == billingDimension
    }

    private fun keyOf(e: VendorBillingEntry): String =
        "${e.vendorCode}|${e.workflowType}|${e.billingDimension}"

    fun tag(): String = TAG
}
