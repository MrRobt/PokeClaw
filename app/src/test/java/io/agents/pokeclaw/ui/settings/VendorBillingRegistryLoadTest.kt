// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// V1.0 VendorBillingRegistry.loadFromResp 单测（Fix code-review H4：merge 语义）

package io.agents.pokeclaw.ui.settings

import io.agents.pokeclaw.cloud.lobster.model.ClawAppBillingPricingRespVO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VendorBillingRegistryLoadTest {

    @Before
    fun setUp() {
        VendorBillingRegistry.resetForTests()
    }

    @After
    fun tearDown() {
        VendorBillingRegistry.resetForTests()
    }

    @Test
    fun `default live is SEED with 4 entries`() {
        assertEquals(4, VendorBillingRegistry.all().size)
    }

    // -----------------------------------------------------------------------
    // Fix H4 验证：merge 语义而非 replace
    //   - 云端返回的 key 覆盖 SEED 同 key 行
    //   - SEED 中没有的 key 追加到末尾
    //   - SEED 中有但云端没的 key 保留
    //   - 空响应 → no-op
    // -----------------------------------------------------------------------

    @Test
    fun `loadFromResp merges over SEED keeping unknown SEED entries`() {
        // SEED 4 条：cloudphone/*/duration, digital_human/live_virtual_human/duration,
        //          digital_human/live_virtual_human/call, cs_ai/*/token
        // 云端只返回 cloudphone 一条（覆盖），其他 SEED 保留
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "cloudphone",
                workflowType = "*",
                billingDimension = "duration",
                displayName = "云手机 · 按时长",
                creditCost = 10,
                currency = "credit",
                status = "CONFIGURED",
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        val live = VendorBillingRegistry.all()
        // 4 条全在（cloudphone 被覆盖，剩下 3 条 SEED 保留）
        assertEquals(4, live.size)
        // cloudphone 应该用云端的 creditCost=10
        val cloudphone = live.first { it.vendorCode == "cloudphone" }
        assertEquals(10, cloudphone.creditCost)
        // digital_human 两条保留（SEED 默认 creditCost=null）
        val digitalHumanEntries = live.filter { it.vendorCode == "digital_human" }
        assertEquals(2, digitalHumanEntries.size)
        // cs_ai 保留
        assertTrue(live.any { it.vendorCode == "cs_ai" })
    }

    @Test
    fun `loadFromResp appends new keys not in SEED`() {
        // 云端返回新 key：YOLO 不在 SEED
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "yolo_inference",
                workflowType = "*",
                billingDimension = "image",
                displayName = "YOLO 推理 · 按张",
                creditCost = 1,
                status = "CONFIGURED",
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        val live = VendorBillingRegistry.all()
        // 4 SEED + 1 新 = 5
        assertEquals(5, live.size)
        assertTrue(live.any { it.vendorCode == "yolo_inference" && it.creditCost == 1 })
    }

    @Test
    fun `loadFromResp null response is no-op`() {
        val sizeBefore = VendorBillingRegistry.all().size
        VendorBillingRegistry.loadFromResp(null)
        assertEquals(sizeBefore, VendorBillingRegistry.all().size)
    }

    @Test
    fun `loadFromResp empty list is no-op`() {
        val sizeBefore = VendorBillingRegistry.all().size
        VendorBillingRegistry.loadFromResp(emptyList())
        assertEquals(sizeBefore, VendorBillingRegistry.all().size)
    }

    @Test
    fun `loadFromResp with null displayName synthesizes from code`() {
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "cs_ai",
                workflowType = "*",
                billingDimension = "token",
                displayName = null,
                creditCost = 1,
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        // cs_ai 在 SEED 也有，merge 后 creditCost 应该是 1（云端值），不是 null
        val csAi = VendorBillingRegistry.find("cs_ai", "*", "token")
        assertNotNull(csAi)
        assertEquals("cs_ai · * · token", csAi!!.displayName)
        assertEquals(1, csAi.creditCost)
    }

    @Test
    fun `loadFromResp with null creditCost maps to UNKNOWN status`() {
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "cs_ai",
                workflowType = "*",
                billingDimension = "token",
                displayName = "CS-AI",
                creditCost = null,
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        val csAi = VendorBillingRegistry.find("cs_ai", "*", "token")
        assertNotNull(csAi)
        assertNull(csAi!!.creditCost)
        assertEquals(VendorBillingEntry.Status.UNKNOWN, csAi.status)
    }

    @Test
    fun `loadFromResp with positive creditCost maps to CONFIGURED status`() {
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "cs_ai",
                workflowType = "*",
                billingDimension = "token",
                displayName = "CS-AI",
                creditCost = 5,
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        val csAi = VendorBillingRegistry.find("cs_ai", "*", "token")
        assertNotNull(csAi)
        assertNotNull(csAi!!.creditCost)
        assertEquals(5, csAi.creditCost)
        assertEquals(VendorBillingEntry.Status.CONFIGURED, csAi.status)
    }

    @Test
    fun `find works after loadFromResp`() {
        val resp = listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "cloudphone",
                workflowType = "*",
                billingDimension = "duration",
                displayName = "云手机",
                creditCost = 10,
            ),
        )
        VendorBillingRegistry.loadFromResp(resp)
        val found = VendorBillingRegistry.find("cloudphone", "*", "duration")
        assertNotNull(found)
        assertEquals(10, found?.creditCost)
    }

    @Test
    fun `resetForTests restores SEED`() {
        // 即使 loadFromResp 后，resetForTests 也能回到 SEED
        VendorBillingRegistry.loadFromResp(listOf(
            ClawAppBillingPricingRespVO(
                vendorCode = "X", workflowType = "Y", billingDimension = "Z",
                displayName = "test", creditCost = 1,
            ),
        ))
        // SEED 没 X，所以 size=5（4 SEED + 1 新）
        assertEquals(5, VendorBillingRegistry.all().size)
        VendorBillingRegistry.resetForTests()
        assertEquals(4, VendorBillingRegistry.all().size)
    }
}
