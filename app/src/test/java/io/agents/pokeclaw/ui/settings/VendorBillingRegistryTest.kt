// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [VendorBillingRegistry] + [VendorBillingEntry]
 * (US-D-031-SETTINGS-BILLING-SECTION). Pure-Kotlin: no Android
 * Views or org.json.
 */
class VendorBillingRegistryTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
        VendorBillingRegistry.resetForTests()
    }

    @After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
        VendorBillingRegistry.resetForTests()
    }

    // ---- VendorBillingEntry -------------------------------------------------

    @Test
    fun entry_status_null_isUnknown() {
        val e = VendorBillingEntry(
            vendorCode = "x", workflowType = "y", billingDimension = "z",
            displayName = "X", creditCost = null,
        )
        assertSame(VendorBillingEntry.Status.UNKNOWN, e.status)
        assertEquals("未配置", e.statusLabel())
    }

    @Test
    fun entry_status_zero_isPlaceholder() {
        val e = VendorBillingEntry(
            vendorCode = "x", workflowType = "y", billingDimension = "z",
            displayName = "X", creditCost = 0,
        )
        assertSame(VendorBillingEntry.Status.PLACEHOLDER, e.status)
        assertEquals("占位（未定价）", e.statusLabel())
    }

    @Test
    fun entry_status_positive_isConfigured() {
        val e = VendorBillingEntry(
            vendorCode = "x", workflowType = "y", billingDimension = "z",
            displayName = "X", creditCost = 7,
        )
        assertSame(VendorBillingEntry.Status.CONFIGURED, e.status)
        assertEquals("7 积分/单位", e.statusLabel())
    }

    @Test
    fun entry_statusLabel_formatForLargeCosts() {
        val e = VendorBillingEntry(
            vendorCode = "x", workflowType = "y", billingDimension = "z",
            displayName = "X", creditCost = 1234,
        )
        assertEquals("1234 积分/单位", e.statusLabel())
    }

    // ---- Registry: SEED shape ------------------------------------------------

    @Test
    fun seed_hasFourEntries() {
        assertEquals(4, VendorBillingRegistry.SEED.size)
    }

    @Test
    fun all_returnsSeed_whenNoRemote() {
        val all = VendorBillingRegistry.all()
        assertEquals(4, all.size)
    }

    @Test
    fun seed_coversAllBundleVendorRows() {
        val codes = VendorBillingRegistry.SEED.map { it.vendorCode to it.billingDimension }
        assertTrue(
            "cloudphone / duration missing",
            ("cloudphone" to "duration") in codes,
        )
        assertTrue(
            "digital_human / duration missing",
            ("digital_human" to "duration") in codes,
        )
        assertTrue(
            "digital_human / call missing",
            ("digital_human" to "call") in codes,
        )
        assertTrue(
            "cs_ai / token missing",
            ("cs_ai" to "token") in codes,
        )
    }

    @Test
    fun seed_digitalHuman_workflowTypeIsLiveVirtualHuman() {
        val dh = VendorBillingRegistry.SEED.filter { it.vendorCode == "digital_human" }
        assertEquals(2, dh.size)
        assertTrue(dh.all { it.workflowType == "live_virtual_human" })
        val dims = dh.map { it.billingDimension }.toSet()
        assertEquals(setOf("duration", "call"), dims)
    }

    @Test
    fun seed_cloudphone_workflowTypeIsWildcard() {
        val cp = VendorBillingRegistry.SEED.first {
            it.vendorCode == "cloudphone" && it.billingDimension == "duration"
        }
        assertEquals("*", cp.workflowType)
    }

    @Test
    fun seed_csai_workflowTypeIsWildcard() {
        val cs = VendorBillingRegistry.SEED.first {
            it.vendorCode == "cs_ai" && it.billingDimension == "token"
        }
        assertEquals("*", cs.workflowType)
    }

    @Test
    fun seed_displayNameNonBlank() {
        for (e in VendorBillingRegistry.SEED) {
            assertTrue("${e.vendorCode}: blank displayName", e.displayName.isNotBlank())
        }
    }

    @Test
    fun seed_creditCostIsNullByDefault() {
        // We deliberately keep the seeds as placeholders; the cloud
        // is the source of truth for live cost.
        for (e in VendorBillingRegistry.SEED) {
            assertNull("${e.vendorCode}: cost should be null in seed", e.creditCost)
            assertSame(
                "${e.vendorCode}: should be UNKNOWN when seed has no cost",
                VendorBillingEntry.Status.UNKNOWN, e.status,
            )
        }
    }

    // ---- find() --------------------------------------------------------------

    @Test
    fun find_knownKey_returnsRow() {
        val e = VendorBillingRegistry.find("cloudphone", "*", "duration")
        assertNotNull(e)
        assertEquals("云手机 · 按时长", e!!.displayName)
    }

    @Test
    fun find_unknownKey_returnsNull() {
        assertNull(VendorBillingRegistry.find("nope", "nope", "nope"))
    }

    @Test
    fun find_caseSensitive() {
        assertNull(VendorBillingRegistry.find("CloudPhone", "*", "duration"))
    }

    // ---- withRemote() overlay ------------------------------------------------

    @Test
    fun withRemote_null_leavesRegistryUnchanged() {
        val before = VendorBillingRegistry.all()
        VendorBillingRegistry.withRemote(null)
        assertEquals(before, VendorBillingRegistry.all())
    }

    @Test
    fun withRemote_empty_leavesRegistryUnchanged() {
        val before = VendorBillingRegistry.all()
        VendorBillingRegistry.withRemote(emptyList())
        assertEquals(before, VendorBillingRegistry.all())
    }

    @Test
    fun withRemote_knownKey_patchesCreditCost() {
        val patch = listOf(
            VendorBillingEntry(
                vendorCode = "cloudphone", workflowType = "*", billingDimension = "duration",
                displayName = "云手机 · 按时长", creditCost = 12,
            ),
        )
        VendorBillingRegistry.withRemote(patch)
        val all = VendorBillingRegistry.all()
        assertEquals(4, all.size)
        val cp = all.first { it.vendorCode == "cloudphone" && it.billingDimension == "duration" }
        assertEquals(12, cp.creditCost)
        assertSame(VendorBillingEntry.Status.CONFIGURED, cp.status)
    }

    @Test
    fun withRemote_unknownKey_doesNotAdd() {
        val patch = listOf(
            VendorBillingEntry(
                vendorCode = "mystery", workflowType = "x", billingDimension = "y",
                displayName = "?", creditCost = 1,
            ),
        )
        VendorBillingRegistry.withRemote(patch)
        // Unknown rows from the cloud should not pollute the seed list.
        assertEquals(4, VendorBillingRegistry.all().size)
    }

    @Test
    fun withRemote_partialOverlay_keepsOtherSeedsUnchanged() {
        val patch = listOf(
            VendorBillingEntry(
                vendorCode = "cs_ai", workflowType = "*", billingDimension = "token",
                displayName = "CS-AI · 按 token", creditCost = 5,
            ),
        )
        VendorBillingRegistry.withRemote(patch)
        val cs = VendorBillingRegistry.find("cs_ai", "*", "token")
        assertEquals(5, cs!!.creditCost)
        // digital_human rows still null
        val dh = VendorBillingRegistry.find("digital_human", "live_virtual_human", "duration")
        assertNull(dh!!.creditCost)
    }

    @Test
    fun withRemote_canClearCostByOverlayingNull() {
        // If the cloud sends a row with null cost (e.g. row deleted),
        // we patch it back to null — never accidentally "fix" the
        // status to CONFIGURED.
        val patch = listOf(
            VendorBillingEntry(
                vendorCode = "cloudphone", workflowType = "*", billingDimension = "duration",
                displayName = "云手机 · 按时长", creditCost = null,
            ),
        )
        VendorBillingRegistry.withRemote(patch)
        val cp = VendorBillingRegistry.find("cloudphone", "*", "duration")!!
        assertNull(cp.creditCost)
        assertSame(VendorBillingEntry.Status.UNKNOWN, cp.status)
    }

    // ---- resetForTests -------------------------------------------------------

    @Test
    fun resetForTests_dropsRemoteOverlay() {
        VendorBillingRegistry.withRemote(
            listOf(
                VendorBillingEntry(
                    vendorCode = "cloudphone", workflowType = "*", billingDimension = "duration",
                    displayName = "云手机 · 按时长", creditCost = 99,
                ),
            ),
        )
        assertEquals(99, VendorBillingRegistry.find("cloudphone", "*", "duration")!!.creditCost)
        VendorBillingRegistry.resetForTests()
        assertNull(VendorBillingRegistry.find("cloudphone", "*", "duration")!!.creditCost)
    }

    // ---- Tag stability -------------------------------------------------------

    @Test
    fun tag_isStable() {
        assertEquals(VendorBillingRegistry.tag(), VendorBillingRegistry.tag())
        assertTrue(VendorBillingRegistry.tag().isNotBlank())
    }
}
