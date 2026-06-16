// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for CronParser covering the everyday expressions the UI exposes.
 */
class CronParserTest {

    private fun dayAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun isValid_acceptsStarExpressions() {
        assertTrue(CronParser.isValid("* * * * *"))
        assertTrue(CronParser.isValid("0 * * * *"))
        assertTrue(CronParser.isValid("0 0 * * *"))
    }

    @Test
    fun isValid_acceptsLists() {
        assertTrue(CronParser.isValid("0,15,30,45 * * * *"))
    }

    @Test
    fun isValid_acceptsRanges() {
        assertTrue(CronParser.isValid("0 9-17 * * *"))
    }

    @Test
    fun isValid_rejectsWrongFieldCount() {
        assertFalse(CronParser.isValid("* * * *"))
        assertFalse(CronParser.isValid("* * * * * *"))
    }

    @Test
    fun isValid_rejectsOutOfRange() {
        assertFalse(CronParser.isValid("60 * * * *")) // minute > 59
        assertFalse(CronParser.isValid("* 24 * * *")) // hour > 23
        assertFalse(CronParser.isValid("* * 32 * *")) // day > 31
        assertFalse(CronParser.isValid("* * 0 * *"))  // day < 1
        assertFalse(CronParser.isValid("* * * 13 *")) // month > 12
        assertFalse(CronParser.isValid("* * * 0 *"))  // month < 1
    }

    @Test
    fun nextRunAfter_dailyAtMidnight_returnsNextMidnight() {
        // Every day at 00:00. From 2026-06-13 10:30 → next fire is 2026-06-14 00:00.
        val from = dayAt(2026, 6, 13, 10, 30)
        val next = CronParser.nextRunAfter("0 0 * * *", from)
        assertNotNull(next)
        val expected = dayAt(2026, 6, 14, 0, 0)
        assertEquals(expected, next!!)
    }

    @Test
    fun nextRunAfter_everyMonday_returnsNextMonday() {
        // 2026-06-13 is a Saturday. Next Monday is 2026-06-15.
        val from = dayAt(2026, 6, 13, 12, 0)
        val next = CronParser.nextRunAfter("0 9 * * 1", from)
        assertNotNull(next)
        val expected = dayAt(2026, 6, 15, 9, 0)
        assertEquals(expected, next!!)
    }

    @Test
    fun nextRunAfter_firstOfMonth_returnsNextFirstOfMonth() {
        // 2026-06-13 → next 1st of month is 2026-07-01.
        val from = dayAt(2026, 6, 13, 12, 0)
        val next = CronParser.nextRunAfter("0 0 1 * *", from)
        assertNotNull(next)
        val expected = dayAt(2026, 7, 1, 0, 0)
        assertEquals(expected, next!!)
    }

    @Test
    fun nextRunAfter_everyFiveMinutes_returnsWithinFiveMinutes() {
        val from = dayAt(2026, 6, 13, 12, 0) // minute=0
        val next = CronParser.nextRunAfter("*/5 * * * *", from)
        assertNotNull(next)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = next!!
        assertEquals(5, cal.get(Calendar.MINUTE))
    }

    @Test
    fun nextRunAfter_malformedCron_returnsNull() {
        val from = System.currentTimeMillis()
        assertNull(CronParser.nextRunAfter("not a cron", from))
        assertNull(CronParser.nextRunAfter("60 * * * *", from))
    }

    @Test
    fun nextRunAfter_isStrictlyAfterFrom() {
        // "every minute" should never return [from] itself.
        val from = System.currentTimeMillis()
        val next = CronParser.nextRunAfter("* * * * *", from)
        assertNotNull(next)
        assertTrue("next must be > from", next!! > from)
    }
}