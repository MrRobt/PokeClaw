// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import io.agents.pokeclaw.cloud.lobster.model.ClawAppBillingPricingRespVO
import java.util.concurrent.atomic.AtomicReference

/**
 * Hardcoded list of AIGC billing vendors shown in the Settings
 * "积分与计费" section (US-D-031-SETTINGS-BILLING-SECTION).
 *
 * V1.0 改造（Fix code-review H4 + M1）：
 *  - 保留 4 行 SEED 作为「云端拉不到时的兜底」+ 测试基线
 *  - 新增 [loadFromResp] 用 **merge** 语义：云端返回的 entry 按 (vendor, workflow, dimension) 覆盖 SEED 的同 key 行；
 *    SEED 中没有的 key 也保留（不被清空）；SEED 中有但云端没有的也保留（不被删除）
 *  - 使用 [AtomicReference] 替代 `@Volatile + 重新赋值` 的 read-modify-write 模式，
 *    避免 Main 线程与 IO 线程并发更新时的丢失
 *  - [withRemote] 保留 API，与 [loadFromResp] 合并为同一代码路径
 *
 * 契约：api-contracts/skill-market-api.md §2.1
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

    /**
     * 当前可见条目（Fix M1：AtomicReference 替代 @Volatile + 不可变列表）。
     * 每次 [loadFromResp] / [withRemote] 都生成新 List 整体替换，避免 read-modify-write 竞态。
     */
    private val liveRef = AtomicReference<List<VendorBillingEntry>>(SEED)

    /** Reset to seed values (used in tests). */
    fun resetForTests() {
        liveRef.set(SEED)
    }

    fun all(): List<VendorBillingEntry> = liveRef.get()

    /**
     * Optional remote overlay (legacy API). The cloud may return a partial /
     * sparse list keyed by (vendorCode, workflowType, dimension);
     * we patch matching rows in [liveRef] in place, leaving unknown
     * seeds untouched so the UI never shrinks.
     */
    fun withRemote(remote: List<VendorBillingEntry>?) {
        if (remote.isNullOrEmpty()) return
        liveRef.updateAndGet { current -> mergeInto(current, remote.map { it.toPair() }) }
    }

    /**
     * V1.0：从 dyq ClawAppBillingPricingRespVO 列表 merge 到 [liveRef]。
     *
     * <p><b>Fix H4：merge 语义，不是 replace。</b>
     * <ul>
     *   <li>云端返回的 entry 按 (vendor, workflow, dimension) 覆盖 SEED 同 key 行</li>
     *   <li>SEED 中没有的 key（云端新增）：追加到末尾</li>
     *   <li>SEED 中有但云端没的 key（云端删除）：保留，不删</li>
     *   <li>空响应：no-op（保留当前 liveRef）</li>
     * </ul>
     */
    fun loadFromResp(resp: List<ClawAppBillingPricingRespVO>?) {
        if (resp.isNullOrEmpty()) return
        val converted = resp.map { it.toEntry() }
        liveRef.updateAndGet { current -> mergeInto(current, converted) }
    }

    /**
     * Merge 工具：把 [updates] 按 key 合并到 [current]。
     * - 重复 key：updates 覆盖 current
     * - 新 key：追加到 current 末尾
     * - 旧 key（current 独有）：保留
     *
     * 返回新 List（不修改入参）。
     */
    private fun mergeInto(
        current: List<VendorBillingEntry>,
        updates: List<VendorBillingEntry>,
    ): List<VendorBillingEntry> {
        if (updates.isEmpty()) return current
        val byKey = updates.associateBy { keyOf(it) }
        val merged = LinkedHashMap<String, VendorBillingEntry>(current.size + updates.size)
        // 先放 current（SEED），被 updates 覆盖
        for (e in current) {
            val key = keyOf(e)
            merged[key] = byKey[key] ?: e
        }
        // 再放 updates 中 current 没有的（云端新增）
        for (e in updates) {
            val key = keyOf(e)
            if (key !in merged) {
                merged[key] = e
            }
        }
        return merged.values.toList()
    }

    private fun keyOf(e: VendorBillingEntry): String =
        "${e.vendorCode}|${e.workflowType}|${e.billingDimension}"

    /**
     * Look up a single vendor by (vendorCode, workflowType,
     * billingDimension). Returns null if no matching row exists.
     */
    fun find(
        vendorCode: String,
        workflowType: String,
        billingDimension: String,
    ): VendorBillingEntry? = liveRef.get().firstOrNull {
        it.vendorCode == vendorCode &&
            it.workflowType == workflowType &&
            it.billingDimension == billingDimension
    }

    /** DTO → domain 转换 */
    private fun ClawAppBillingPricingRespVO.toEntry(): VendorBillingEntry = VendorBillingEntry(
        vendorCode = vendorCode,
        workflowType = workflowType,
        billingDimension = billingDimension,
        displayName = displayName ?: "$vendorCode · $workflowType · $billingDimension",
        creditCost = creditCost,
    )

    /** DTO → domain 转换（[withRemote] 用） */
    private fun VendorBillingEntry.toPair(): VendorBillingEntry = this

    fun tag(): String = TAG
}
