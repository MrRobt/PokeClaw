// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.remake

/**
 * Bundle-seeded remake scene catalog (US-D-028-REMAKE-SCENE-PICKER).
 *
 * The 6 entries mirror the seed rows from the dyq 2026-06-10 prod
 * bundle (aigc_remake_scene). When the cloud endpoint
 * `GET /aigc/remake/scenes` is live, [RemakeSceneFetcher] can replace
 * this list at runtime; until then the picker always shows these 6
 * presets so the feature is fully demoable offline.
 *
 * Keep the order stable — the picker renders in the order returned.
 */
object RemakeSceneCatalog {

    val SCENES: List<RemakeScene> = listOf(
        RemakeScene(
            id = "viral_generic",
            name = "通用爆款",
            description = "把任意视频改编成符合抖音/快手风格的 30 秒爆款",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "热门BGM+紧凑剪辑+大字幕，节奏快、反转多",
                "fixedScript" to "开头 3 秒抛钩子 → 中段冲突 → 结尾意外反转",
            ),
        ),
        RemakeScene(
            id = "ecommerce",
            name = "电商带货",
            description = "商品展示 + 价格 + 卖点，结尾引导下单",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "商品多角度展示，价格贴纸醒目，结尾 CTA 明确",
                "productDescription" to "新款真皮男士钱包，头层牛皮，多卡位",
                "fixedScript" to "痛点引入 → 卖点罗列 → 限时优惠 → 引导下单",
            ),
        ),
        RemakeScene(
            id = "game",
            name = "游戏集锦",
            description = "精彩操作/搞笑失误/剧情名场面混剪",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "激昂 BGM + 慢动作回放 + 数据浮层，节奏感强",
                "fixedScript" to "开篇高燃操作 → 高光时刻慢放 → 结尾神级操作",
            ),
        ),
        RemakeScene(
            id = "knowledge",
            name = "知识科普",
            description = "用通俗讲解 + 动画/图示把复杂概念讲清楚",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "通俗语言 + 关键概念字幕 + 生活化类比，信息密度高",
                "fixedScript" to "提问引入 → 拆解概念 → 举例说明 → 一句话总结",
            ),
        ),
        RemakeScene(
            id = "beauty",
            name = "美妆教程",
            description = "妆容步骤 / 产品测评 / 仿妆挑战",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "自然光特写 + 步骤字幕 + 产品链接卡片",
                "productDescription" to "新品奶油肌粉底液，持妆 12 小时",
                "fixedScript" to "妆前 → 底妆 → 重点部位 → 整体效果 → 持妆反馈",
            ),
        ),
        RemakeScene(
            id = "review",
            name = "测评种草",
            description = "深度测评 + 真实使用 + 优缺点对比",
            exampleImageUrl = null,
            exampleInputs = mapOf(
                "styleDescription" to "客观对比 + 真实使用 + 优缺点总结",
                "productDescription" to "无线降噪耳机新款，主打通勤降噪",
                "fixedScript" to "开箱 → 核心功能 → 真实使用一周 → 优缺点 → 购买建议",
            ),
        ),
    )

    /** All scenes in render order. */
    fun listAll(): List<RemakeScene> = SCENES

    /** Look up a scene by its stable id; null if not found. */
    fun findById(id: String?): RemakeScene? {
        if (id.isNullOrBlank()) return null
        return SCENES.firstOrNull { it.id == id }
    }

    /** The number of seeded scenes. Useful for the UI (e.g. "6 场景"). */
    fun count(): Int = SCENES.size
}
