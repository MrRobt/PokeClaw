// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.
//
// PendingTaskItem.mode 枚举 — 对齐 device.openapi.yaml v1.1.0。
// 现有：TASK, INTERACTIVE；v1.1.0 新增：DRY_RUN, PREPARE_ONLY（仅 WeFlow 云端微信托管使用）。

package io.agents.pokeclaw.cloud.model

import com.google.gson.annotations.SerializedName

/**
 * 任务模式枚举（v1.1.0 扩展）。
 *
 * 端侧策略：
 * - TASK / INTERACTIVE → 正常执行
 * - DRY_RUN → 仅记录指令和参数，不实际执行；上报时 result 字段填 "DRY_RUN_OK"
 * - PREPARE_ONLY → 仅完成前置准备（如打开应用、定位页面），不进入主流程；上报 result="PREPARED"
 *
 * 兜底：未识别的 mode 字符串会被解析为 [UNKNOWN]，调用方应降级为普通 TASK 行为。
 */
enum class TaskMode(val raw: String) {
    @SerializedName("TASK") TASK("TASK"),
    @SerializedName("INTERACTIVE") INTERACTIVE("INTERACTIVE"),
    @SerializedName(value = "dry_run", alternate = ["DRY_RUN"]) DRY_RUN("dry_run"),
    @SerializedName(value = "prepare_only", alternate = ["PREPARE_ONLY"]) PREPARE_ONLY("prepare_only"),
    UNKNOWN("UNKNOWN");

    /** 是否需要在端侧真正执行。 */
    val isExecutable: Boolean
        get() = this == TASK || this == INTERACTIVE

    /**
     * 对应的上报 result 字符串（供 LocalAgentTaskExecutor 兜底上报时使用）。
     * 返回 null 表示使用业务逻辑正常填写的 result。
     */
    fun stubResult(): String? = when (this) {
        DRY_RUN -> "DRY_RUN_OK"
        PREPARE_ONLY -> "PREPARED"
        else -> null
    }

    companion object {
        /** 安全解析：未知字符串 → UNKNOWN，null → TASK（向后兼容旧任务）。 */
        fun parse(raw: String?): TaskMode {
            if (raw.isNullOrBlank()) return TASK
            return entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * PendingTaskItem.mode 的解析扩展。
 *
 * 用法：`task.modeAsEnum()` — 返回 enum 便于模式匹配。
 */
fun PendingTaskItem.modeAsEnum(): TaskMode = TaskMode.parse(mode)
