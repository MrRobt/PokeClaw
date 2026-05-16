// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.skill

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlin.math.ln
import kotlin.math.max

/**
 * Skill 运行时指标存储（轻量版 Skill1 先导）。
 */
object SkillStatsStore {

    private const val TAG = "SkillStatsStore"
    private const val STORAGE_KEY = "KEY_SKILL_RUNTIME_STATS"
    private const val ALPHA = 0.05
    private const val DEFAULT_UTILITY = 0.5
    private const val DEFAULT_MAX_SIZE = 5000

    data class SkillRuntimeMetrics(
        val skillId: String,
        val utility: Double = DEFAULT_UTILITY,
        val selectCount: Int = 0,
        val successCount: Int = 0,
        val failCount: Int = 0,
        val lastSelectionAt: Long = 0L,
        val lastUpdatedAt: Long = 0L,
        val isDynamic: Boolean = false
    )

    data class RankedCandidate(
        val skill: Skill,
        val textHit: Double,
        val utility: Double,
        val selectCount: Int,
        val selectionScore: Double,
        val isBuiltinMatch: Boolean
    )

    private val gson = Gson()
    private val stats: MutableMap<String, SkillRuntimeMetrics> = mutableMapOf()
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")

    private val allWords = listOf(
        "close",
        "open",
        "send",
        "message",
        "search",
        "submit",
        "find",
        "popup",
        "scroll",
        "read",
        "copy"
    )

    /**
     * 以任务文本为核心进行轻量排序：0.7*文本相似 + 0.3*utility。
     */
    fun rankedCandidates(taskText: String, allSkills: Collection<Skill>, k: Int = 5): List<RankedCandidate> {
        val normalizedK = max(1, k)
        val snapshot = synchronized(this) {
            stats.values.toList().associateBy { it.skillId }
        }

        val result = mutableListOf<RankedCandidate>()
        for (skill in allSkills) {
            val metric = snapshot[skill.id] ?: SkillRuntimeMetrics(skillId = skill.id)
            val textHit = calcTextHit(skill, taskText)
            val score = 0.7 * textHit + 0.3 * metric.utility
            val isBuiltinMatch = skill.triggerPatterns.isNotEmpty() && matchByTrigger(skill, taskText)
            result.add(
                RankedCandidate(
                    skill = skill,
                    textHit = textHit,
                    utility = metric.utility,
                    selectCount = metric.selectCount,
                    selectionScore = score,
                    isBuiltinMatch = isBuiltinMatch
                )
            )
        }

        return result
            .filter { it.textHit > 0.0 || it.utility >= 0.5 }
            .sortedByDescending { it.selectionScore }
            .take(normalizedK)
    }

    fun getRuntimeStats(skillId: String): SkillRuntimeMetrics? {
        return synchronized(this) { stats[skillId] }
    }

    fun onSelection(skill: Skill): SkillRuntimeMetrics {
        return synchronized(this) {
            val existing = stats[skill.id] ?: SkillRuntimeMetrics(
                skillId = skill.id,
                utility = DEFAULT_UTILITY,
                isDynamic = isDynamicCandidate(skill)
            )
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                selectCount = existing.selectCount + 1,
                lastSelectionAt = now,
                lastUpdatedAt = now,
            )
            stats[skill.id] = updated
            persistLocked()
            updated
        }
    }

    fun onOutcome(skillId: String, success: Boolean, roundSuccess: Boolean, isFallback: Boolean): SkillRuntimeMetrics {
        return synchronized(this) {
            val existing = stats[skillId] ?: SkillRuntimeMetrics(skillId = skillId, isDynamic = true)
            val reward = when {
                success -> 1.0
                roundSuccess -> 0.5
                isFallback -> 0.2
                else -> 0.0
            }
            val updatedUtility = updateUtility(existing.utility, reward)
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                utility = updatedUtility,
                successCount = existing.successCount + if (success) 1 else 0,
                failCount = existing.failCount + if (success) 0 else 1,
                lastUpdatedAt = now,
                isDynamic = existing.isDynamic || isDynamicCandidate(skillId)
            )
            stats[skillId] = updated
            persistLocked()
            updated
        }
    }

    fun estimateUtilityAfter(currentUtility: Double, success: Boolean, roundSuccess: Boolean, isFallback: Boolean): Double {
        val reward = when {
            success -> 1.0
            roundSuccess -> 0.5
            isFallback -> 0.2
            else -> 0.0
        }
        return updateUtility(currentUtility, reward)
    }

    fun evictIfNeeded(max: Int = DEFAULT_MAX_SIZE) {
        synchronized(this) {
            val limit = max(1, max)
            if (stats.size <= limit) {
                return
            }
            val removable = stats.values
                .filter { it.isDynamic }
                .sortedBy { it.utility * ln(max(2.0, max(1.0, it.selectCount.toDouble()))) }
            val overflow = stats.size - limit
            if (overflow <= 0) return
            val removed = removable.take(overflow)
            for (item in removed) {
                stats.remove(item.skillId)
                XLog.i(TAG, "evictIfNeeded: removed dynamic skillId=${item.skillId}")
            }
            persistLocked()
        }
    }

    fun syncWithSkillCatalog(skills: Collection<Skill>) {
        synchronized(this) {
            val existing = stats.toMap()
            for (skill in skills) {
                if (!stats.containsKey(skill.id)) {
                    stats[skill.id] = SkillRuntimeMetrics(
                        skillId = skill.id,
                        utility = DEFAULT_UTILITY,
                        isDynamic = isDynamicCandidate(skill)
                    )
                } else {
                    val metric = existing[skill.id] ?: continue
                    stats[skill.id] = metric.copy(
                        isDynamic = isDynamicCandidate(skill)
                    )
                }
            }
            persistLocked()
        }
    }

    fun loadFromDisk() {
        synchronized(this) {
            val raw = runCatching { KVUtils.getString(STORAGE_KEY) }.getOrDefault("")
            if (raw.isBlank()) {
                return
            }

            val type = object : TypeToken<Map<String, SkillRuntimeMetrics>>() {}.type
            val parsed: Map<String, SkillRuntimeMetrics> = runCatching {
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(raw, type) as? Map<String, SkillRuntimeMetrics> ?: emptyMap()
            }.getOrDefault(emptyMap())
            if (parsed.isNotEmpty()) {
                stats.clear()
                stats.putAll(parsed)
                XLog.i(TAG, "loadFromDisk: loaded ${stats.size} metrics")
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            val payload = gson.toJson(stats)
            KVUtils.putString(STORAGE_KEY, payload)
            KVUtils.sync()
        }
    }

    private fun updateUtility(current: Double, reward: Double): Double {
        val bounded = current.coerceIn(0.0, 1.0)
        return (1 - ALPHA) * bounded + ALPHA * reward.coerceIn(0.0, 1.0)
    }

    private fun tokenList(text: String): List<String> {
        val normalized = text.lowercase()
        val tokens = tokenRegex.findAll(normalized).map { it.value }.toList()
        return if (tokens.isNotEmpty()) tokens else allWords.filter { it in normalized }
    }

    private fun calcTextHit(skill: Skill, taskText: String): Double {
        val taskTokens = tokenList(taskText)
        if (taskTokens.isEmpty()) return 0.0
        val skillText = buildString {
            append(skill.id)
            append(' ')
            append(skill.name)
            append(' ')
            append(skill.description)
            append(' ')
            append(skill.triggerPatterns.joinToString(" "))
        }
        val skillTokens = tokenList(skillText)
        if (skillTokens.isEmpty()) return 0.0

        var hit = 0
        for (skillToken in skillTokens.distinct()) {
            if (taskText.lowercase().contains(skillToken) || taskTokens.any { it.contains(skillToken) }) {
                hit++
            }
        }
        return (hit.toDouble() / skillTokens.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun matchByTrigger(skill: Skill, taskText: String): Boolean {
        val lower = taskText.lowercase()
        return skill.triggerPatterns.any { pattern ->
            try {
                val regex = pattern.lowercase().replace(Regex("\\{\\w+\\}"), "(.+)")
                Regex(regex).containsMatchIn(lower)
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun isDynamicCandidate(skill: Skill): Boolean {
        return isDynamicCandidate(skill.id)
    }

    private fun isDynamicCandidate(skillId: String): Boolean {
        return skillId.startsWith("dynamic_") || skillId.startsWith("user_")
    }
}
