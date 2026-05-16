// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.skill

import io.agents.pokeclaw.utils.XLog

/**
 * 技能注册中心。
 * 保持原有 Tier-1.5 trigger 逻辑不变，并新增运行时排序入口。
 */
object SkillRegistry {

    private const val TAG = "SkillRegistry"
    private val skills = linkedMapOf<String, Skill>()

    /**
     * 注册技能，重复 ID 覆盖。
     */
    fun register(skill: Skill) {
        skills[skill.id] = skill
        XLog.d(TAG, "Registered skill: ${skill.id} (${skill.name})")
    }

    fun findById(id: String): Skill? = skills[id]

    fun getAll(): List<Skill> = skills.values.toList()

    fun getByCategory(category: SkillCategory): List<Skill> =
        skills.values.filter { it.category == category }

    fun getUserFacing(): List<Skill> =
        skills.values.filter { it.userFacing }

    /**
     * 先按 trigger pattern 进行精确匹配（Tier-1.5）。
     */
    fun findByTrigger(task: String): Skill? {
        val lower = task.lowercase()

        // 复合指令应走 AgentLoop，避免误分发。
        if (lower.contains(" and ") || lower.contains(" then ") || lower.contains(" after ")) {
            XLog.d(TAG, "Compound task detected, skipping skill matching: $task")
            return null
        }

        return skills.values.find { skill ->
            skill.triggerPatterns.any { pattern ->
                try {
                    val regex = pattern.lowercase()
                        .replace(Regex("\\{\\w+\\}"), "(.+)")
                    Regex(regex).containsMatchIn(lower)
                } catch (e: Exception) {
                    XLog.w(TAG, "Invalid trigger pattern: $pattern", e)
                    false
                }
            }
        }
    }

    /**
     * 轻量候选排序（Skill1）。
     * 仅在 trigger 未命中时调用。
     */
    fun rankedCandidates(task: String, k: Int = 5): List<SkillStatsStore.RankedCandidate> {
        val candidates = SkillStatsStore.rankedCandidates(task, getAll(), k)
        return candidates
            .sortedByDescending { it.selectionScore }
            .takeIf { it.isNotEmpty() }
            ?: emptyList()
    }

    fun getRuntimeStats(skillId: String): SkillStatsStore.SkillRuntimeMetrics? {
        return SkillStatsStore.getRuntimeStats(skillId)
    }

    fun updateRuntimeStats(skillId: String, success: Boolean, roundSuccess: Boolean, isFallback: Boolean) {
        SkillStatsStore.onOutcome(skillId, success, roundSuccess, isFallback)
    }

    /**
     * 路由日志与排序指标都从运行时状态更新，保持纯净单向依赖。
     */
    fun onSelection(skill: Skill) {
        SkillStatsStore.onSelection(skill)
    }

    /**
     * 加载内置技能，补齐运行时指标目录。
     */
    fun loadBuiltInSkills() {
        register(BuiltInSkills.searchInApp())
        register(BuiltInSkills.submitForm())
        register(BuiltInSkills.dismissPopup())
        register(BuiltInSkills.scrollAndRead())
        register(BuiltInSkills.copyScreenText())
        register(BuiltInSkills.acceptPermission())
        register(BuiltInSkills.swipeGesture())
        register(BuiltInSkills.goBack())
        register(BuiltInSkills.waitForContent())

        SkillStatsStore.loadFromDisk()
        SkillStatsStore.syncWithSkillCatalog(skills.values)
        SkillStatsStore.evictIfNeeded()
        XLog.i(TAG, "Loaded ${skills.size} built-in skills")
    }

    fun clear() = skills.clear()
}
