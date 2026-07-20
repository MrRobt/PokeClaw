package io.agents.pokeclaw.agent.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryGuardTest {

    @Test
    fun `low battery not charging is blocked`() {
        val d = BatteryGuard.evaluate(BatteryStatus(percent = 10, isCharging = false))
        assertFalse(d.allowed)
    }

    @Test
    fun `low battery but charging is allowed`() {
        val d = BatteryGuard.evaluate(BatteryStatus(percent = 5, isCharging = true))
        assertTrue(d.allowed)
        assertEquals("charging", d.reason)
    }

    @Test
    fun `at threshold is allowed`() {
        val d = BatteryGuard.evaluate(BatteryStatus(percent = 15, isCharging = false))
        assertTrue(d.allowed)
    }

    @Test
    fun `just below threshold is blocked`() {
        val d = BatteryGuard.evaluate(BatteryStatus(percent = 14, isCharging = false))
        assertFalse(d.allowed)
    }

    @Test
    fun `high battery is allowed`() {
        assertTrue(BatteryGuard.evaluate(BatteryStatus(percent = 80, isCharging = false)).allowed)
    }

    @Test
    fun `custom threshold respected`() {
        val d = BatteryGuard.evaluate(BatteryStatus(percent = 25, isCharging = false), minPercent = 30)
        assertFalse(d.allowed)
    }
}
