// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.scheduler

import java.util.Calendar
import java.util.TimeZone

/**
 * Minimal 5-field cron parser for US-D-021-TASK-SCHEDULER.
 *
 * Supported syntax (each field):
 *  - "*" matches every value
 *  - "N" matches exactly N (0..23 for hour, etc.)
 *  - "N,M,..." matches any of the listed values
 *  - "A-B" matches the inclusive range
 *
 * Fields: minute(0-59) hour(0-23) day-of-month(1-31) month(1-12) day-of-week(0-6, 0=Sunday)
 *
 * [nextRunAfter] returns the next epoch-millis that matches the cron, strictly
 * after [from]. Returns null when the cron is malformed.
 */
object CronParser {

    /**
     * Compute the next epoch-millis at which [cron] matches, strictly after [fromMillis].
     * Uses the device's default time zone. Returns null on parse error.
     */
    fun nextRunAfter(cron: String, fromMillis: Long): Long? {
        val parts = cron.trim().split(Regex("\\s+"))
        if (parts.size != 5) return null
        val minuteField = parseField(parts[0], 0, 59) ?: return null
        val hourField   = parseField(parts[1], 0, 23) ?: return null
        val domField    = parseField(parts[2], 1, 31) ?: return null
        val monthField  = parseField(parts[3], 1, 12) ?: return null
        val dowField    = parseField(parts[4], 0, 6)  ?: return null

        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = fromMillis
        // Advance to next minute so we never match [fromMillis] itself.
        cal.add(Calendar.MINUTE, 1)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Cap iteration to avoid infinite loops on degenerate expressions.
        val maxIterations = 366 * 24 * 60 // one year of minutes, safe upper bound
        for (i in 0 until maxIterations) {
            val month = cal.get(Calendar.MONTH) + 1
            val dom   = cal.get(Calendar.DAY_OF_MONTH)
            val dow   = cal.get(Calendar.DAY_OF_WEEK) - 1  // Calendar.SUNDAY=1 → 0
            val hour  = cal.get(Calendar.HOUR_OF_DAY)
            val min   = cal.get(Calendar.MINUTE)

            if (monthField.contains(month) &&
                domField.contains(dom) &&
                dowField.contains(dow) &&
                hourField.contains(hour) &&
                minuteField.contains(min)
            ) {
                return cal.timeInMillis
            }
            cal.add(Calendar.MINUTE, 1)
        }
        return null
    }

    /** Returns true when the cron string parses successfully (length + ranges). */
    fun isValid(cron: String): Boolean {
        val parts = cron.trim().split(Regex("\\s+"))
        if (parts.size != 5) return false
        return parseField(parts[0], 0, 59) != null &&
               parseField(parts[1], 0, 23) != null &&
               parseField(parts[2], 1, 31) != null &&
               parseField(parts[3], 1, 12) != null &&
               parseField(parts[4], 0, 6) != null
    }

    /**
     * Parse one cron field into a predicate set. Returns a `Set<Int>` containing
     * every matching value, or null when the field is malformed or out of range.
     *
     * Supported tokens (per element of the comma list):
     *  - "N"        exact value
     *  - "A-B"      inclusive range
     *  - "*" or "*\/N"  full range, optionally stepped by N
     *  - "A-B/N"    inclusive range stepped by N
     */
    private fun parseField(field: String, min: Int, max: Int): Set<Int>? {
        if (field == "*") return (min..max).toSet()
        val result = HashSet<Int>()
        for (token in field.split(',')) {
            val stepped = token.split('/')
            if (stepped.size > 2) return null
            val step = if (stepped.size == 2) {
                stepped[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            } else null
            val base = stepped[0]
            if (base == "*") {
                val s = step ?: 1
                for (n in min..max step s) result += n
                continue
            }
            val range = base.split('-')
            if (range.size == 1) {
                // Plain N — no step expansion (preserve "exact" semantics).
                if (step != null) return null // "N/N" is not standard cron
                val n = range[0].toIntOrNull() ?: return null
                if (n !in min..max) return null
                result += n
            } else if (range.size == 2) {
                val lo = range[0].toIntOrNull() ?: return null
                val hi = range[1].toIntOrNull() ?: return null
                if (lo !in min..max || hi !in min..max || lo > hi) return null
                val s = step ?: 1
                for (n in lo..hi step s) result += n
            } else {
                return null
            }
        }
        return result
    }
}