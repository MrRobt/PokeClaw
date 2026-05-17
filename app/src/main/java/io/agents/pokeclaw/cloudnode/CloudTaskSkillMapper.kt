// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 云端任务到本地技能的映射器：将自然语言指令转换为可执行的 Skill。

package io.agents.pokeclaw.cloudnode

import io.agents.pokeclaw.agent.skill.Skill
import io.agents.pokeclaw.agent.skill.SkillRegistry

/**
 * 云端任务指令到本地技能的映射器。
 * 保持轻量：只做关键词匹配，复杂语义留给云端预处理。
 */
object CloudTaskSkillMapper {

    /**
     * 简单指令分类：将云端下发的指令映射为技能ID和参数。
     * 返回 null 表示无法确定性匹配，应走 AgentLoop 或返回不支持。
     */
    fun mapToSkill(instruction: String): SkillMapping? {
        val lower = instruction.lowercase()

        // 1. 打开应用类指令
        if (lower.contains("打开") || lower.contains("启动") || lower.contains("open")) {
            val appName = extractAppName(instruction)
            return SkillMapping(
                skillId = "launch_app",
                params = mapOf("app_name" to (appName ?: "")),
                confidence = if (appName != null) 0.9 else 0.5
            )
        }

        // 2. 点击/查找类指令
        if (lower.contains("点击") || lower.contains("查找") || lower.contains("tap") || lower.contains("find")) {
            val target = extractTarget(lower)
            return SkillMapping(
                skillId = "find_and_tap",
                params = mapOf("text" to target),
                confidence = 0.8
            )
        }

        // 3. 输入类指令
        if (lower.contains("输入") || lower.contains("填写") || lower.contains("type") || lower.contains("input")) {
            val text = extractInputText(instruction)
            return SkillMapping(
                skillId = "input_text",
                params = mapOf("text" to (text ?: "")),
                confidence = if (text != null) 0.85 else 0.5
            )
        }

        // 4. 返回/后退
        if (lower.contains("返回") || lower.contains("后退") || lower.contains("go back") || lower.contains("back")) {
            return SkillMapping(
                skillId = "go_back",
                params = emptyMap(),
                confidence = 0.95
            )
        }

        // 5. 搜索类指令
        if (lower.contains("搜索") || lower.contains("查找") || lower.contains("search")) {
            val query = extractQuery(lower)
            return SkillMapping(
                skillId = "search_in_app",
                params = mapOf("query" to query),
                confidence = 0.8
            )
        }

        // 6. 截图/状态类指令
        if (lower.contains("截图") || lower.contains("状态") || lower.contains("屏幕") || lower.contains("screenshot") || lower.contains("screen")) {
            return SkillMapping(
                skillId = "screenshot",
                params = emptyMap(),
                confidence = 0.9
            )
        }

        // 7. 权限类指令
        if (lower.contains("权限") || lower.contains("允许") || lower.contains("permission") || lower.contains("allow")) {
            return SkillMapping(
                skillId = "accept_permission",
                params = emptyMap(),
                confidence = 0.85
            )
        }

        // 8. 关闭/取消类指令
        if (lower.contains("关闭") || lower.contains("取消") || lower.contains("关闭弹窗") || lower.contains("dismiss") || lower.contains("close")) {
            return SkillMapping(
                skillId = "dismiss_popup",
                params = emptyMap(),
                confidence = 0.85
            )
        }

        // 9. 滑动类指令
        if (lower.contains("滑动") || lower.contains("上滑") || lower.contains("下滑") || lower.contains("左滑") || lower.contains("右滑") ||
            lower.contains("swipe") || lower.contains("scroll")) {
            val direction = extractDirection(lower)
            return SkillMapping(
                skillId = "swipe_gesture",
                params = mapOf("direction" to direction),
                confidence = 0.8
            )
        }

        // 无法确定性匹配
        return null
    }

    /**
     * 查找实际可执行的 Skill 实例。
     */
    fun resolveSkill(mapping: SkillMapping): Skill? {
        return SkillRegistry.findById(mapping.skillId)
    }

    private fun extractAppName(instruction: String): String? {
        val patterns = listOf(
            "打开(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "启动(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "open\\s+(.+?)[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(instruction)?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotBlank()) return it
            }
        }
        return null
    }

    private fun extractTarget(instruction: String): String {
        val patterns = listOf(
            "点击(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "查找(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "tap\\s+(.+?)[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE),
            "find\\s+(.+?)[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(instruction)?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotBlank()) return it
            }
        }
        return instruction
    }

    private fun extractInputText(instruction: String): String? {
        val patterns = listOf(
            "输入[\"\"\"](.+?)[\"\"\"]*[\"\"\\s]*${'$'}".toRegex(),
            "填写[\"\"\"](.+?)[\"\"\"]*[\"\"\\s]*${'$'}".toRegex(),
            "type\\s+[\"\"\"](.+?)[\"\"\"]*[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE),
            "input\\s+[\"\"\"](.+?)[\"\"\"]*[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(instruction)?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotBlank()) return it
            }
        }
        return null
    }

    private fun extractQuery(instruction: String): String {
        val patterns = listOf(
            "搜索(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "查找(.+?)[\"\"\\s]*${'$'}".toRegex(),
            "search\\s+for\\s+(.+?)[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE),
            "search\\s+(.+?)[\"\"\\s]*${'$'}".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(instruction)?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotBlank()) return it
            }
        }
        return instruction
    }

    private fun extractDirection(instruction: String): String {
        val lower = instruction.lowercase()
        return when {
            lower.contains("上") || lower.contains("up") -> "up"
            lower.contains("下") || lower.contains("down") -> "down"
            lower.contains("左") || lower.contains("left") -> "left"
            lower.contains("右") || lower.contains("right") -> "right"
            else -> "up"
        }
    }
}

/**
 * 映射结果：技能ID、参数、置信度。
 */
data class SkillMapping(
    val skillId: String,
    val params: Map<String, String>,
    val confidence: Double
)
