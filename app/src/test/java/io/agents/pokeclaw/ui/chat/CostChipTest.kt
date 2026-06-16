// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CostChip]. Verifies:
 *  - null/zero/negative credits suppress the chip
 *  - low/medium/high thresholds map to the right level
 *  - top-level and nested `billing.creditConsumed` both parse
 *  - malformed / blank JSON does not throw and returns null
 *  - numeric coercion (Number + String) both work
 */
class CostChipTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    @Test
    fun forCredits_nullReturnsNull() {
        assertNull(CostChip.forCredits(null))
    }

    @Test
    fun forCredits_zeroReturnsNull() {
        assertNull(CostChip.forCredits(0L))
    }

    @Test
    fun forCredits_negativeReturnsNull() {
        assertNull(CostChip.forCredits(-1L))
        assertNull(CostChip.forCredits(-100L))
    }

    @Test
    fun forCredits_oneIsLow() {
        val chip = CostChip.forCredits(1L)
        assertNotNull(chip)
        assertEquals(CostChip.Level.LOW, chip!!.level)
        assertEquals(1L, chip.credits)
        assertEquals("花费 1 积分", chip.text)
    }

    @Test
    fun forCredits_nineIsLow() {
        assertEquals(CostChip.Level.LOW, CostChip.forCredits(9L)!!.level)
    }

    @Test
    fun forCredits_tenIsMedium() {
        assertEquals(CostChip.Level.MEDIUM, CostChip.forCredits(10L)!!.level)
    }

    @Test
    fun forCredits_ninetyNineIsMedium() {
        assertEquals(CostChip.Level.MEDIUM, CostChip.forCredits(99L)!!.level)
    }

    @Test
    fun forCredits_oneHundredIsHigh() {
        assertEquals(CostChip.Level.HIGH, CostChip.forCredits(100L)!!.level)
    }

    @Test
    fun forCredits_thousandIsHigh() {
        val chip = CostChip.forCredits(1000L)
        assertNotNull(chip)
        assertEquals(CostChip.Level.HIGH, chip!!.level)
        assertEquals("花费 1000 积分", chip.text)
    }

    @Test
    fun parseFromResultJson_nullReturnsNull() {
        assertNull(CostChip.parseFromResultJson(null))
    }

    @Test
    fun parseFromResultJson_blankReturnsNull() {
        assertNull(CostChip.parseFromResultJson(""))
        assertNull(CostChip.parseFromResultJson("   "))
    }

    @Test
    fun parseFromResultJson_invalidJsonReturnsNull() {
        assertNull(CostChip.parseFromResultJson("{not-json"))
        assertNull(CostChip.parseFromResultJson("just-a-string"))
    }

    @Test
    fun parseFromResultJson_missingFieldReturnsNull() {
        assertNull(CostChip.parseFromResultJson("""{"status":"SUCCESS"}"""))
    }

    @Test
    fun parseFromResultJson_explicitNullReturnsNull() {
        assertNull(CostChip.parseFromResultJson("""{"creditConsumed":null}"""))
    }

    @Test
    fun parseFromResultJson_zeroReturnsNull() {
        assertNull(CostChip.parseFromResultJson("""{"creditConsumed":0}"""))
    }

    @Test
    fun parseFromResultJson_topLevelFieldParses() {
        val chip = CostChip.parseFromResultJson("""{"status":"SUCCESS","creditConsumed":42}""")
        assertNotNull(chip)
        assertEquals(42L, chip!!.credits)
        assertEquals(CostChip.Level.MEDIUM, chip.level)
        assertEquals("花费 42 积分", chip.text)
    }

    @Test
    fun parseFromResultJson_nestedBillingFieldParses() {
        val chip = CostChip.parseFromResultJson(
            """{"status":"SUCCESS","billing":{"creditConsumed":7}}"""
        )
        assertNotNull(chip)
        assertEquals(7L, chip!!.credits)
        assertEquals(CostChip.Level.LOW, chip.level)
    }

    @Test
    fun parseFromResultJson_topLevelTakesPrecedenceOverNested() {
        val chip = CostChip.parseFromResultJson(
            """{"creditConsumed":5,"billing":{"creditConsumed":500}}"""
        )
        assertNotNull(chip)
        assertEquals(5L, chip!!.credits)
    }

    @Test
    fun parseFromResultJson_acceptsStringNumber() {
        // Quoted-number tolerance: "12" should still parse as 12.
        val chip = CostChip.parseFromResultJson("""{"creditConsumed":"12"}""")
        assertNotNull(chip)
        assertEquals(12L, chip!!.credits)
        assertEquals(CostChip.Level.MEDIUM, chip.level)
    }

    @Test
    fun parseFromResultJson_stringNumberNonNumericReturnsNull() {
        assertNull(CostChip.parseFromResultJson("""{"creditConsumed":"abc"}"""))
    }

    @Test
    fun parseFromResultJson_highValueParses() {
        val chip = CostChip.parseFromResultJson("""{"creditConsumed":2500}""")
        assertNotNull(chip)
        assertEquals(CostChip.Level.HIGH, chip!!.level)
        assertTrue(chip.text.contains("2500"))
    }

    @Test
    fun parseFromResultJson_emptyObjectReturnsNull() {
        assertNull(CostChip.parseFromResultJson("{}"))
    }
}
