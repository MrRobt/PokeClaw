// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud.model

import io.agents.pokeclaw.utils.XLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 任务 ID 双轨：
 *  - [displayTaskId]：对用户友好（嵌入聊天 UI），格式 `CMP-TASK-YYYYMMDD-NNNN`
 *  - [commercialTaskId]：云端商业 ID，UUID v7（时间戳前缀 + 随机后缀，便于云端排序）
 *
 * 设计取舍：
 *  - displayTaskId 由日期 + 当日序号组成，便于人眼识别
 *  - commercialTaskId 唯一性强，可作为云端数据库主键
 *  - 两者一一对应，写入 ChatMessage 时同时保存
 */
object TaskIdFormat {

    private const val TAG = "PokeClaw/TaskId"
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val dailyCounter = AtomicLong(0)
    @Volatile private var currentDate: String = ""

    /**
     * 生成 displayTaskId（同一进程内当日序号递增）。
     */
    fun displayTaskId(now: Long = System.currentTimeMillis(), seq: Int? = null): String {
        val date = dateFormat.format(Date(now))
        if (date != currentDate) {
            currentDate = date
            dailyCounter.set(0)
        }
        val n = seq ?: (dailyCounter.incrementAndGet().toInt())
        return "CMP-TASK-$date-${n.toString().padStart(4, '0')}"
    }

    /**
     * 生成 commercialTaskId（UUID v4 简化版）。
     *
     * 真正的 v7 需时间戳前缀的位运算（48-bit ms + ver + rand），这里用 UUID.randomUUID()
     * 并在 XLog 中打 prefix（前 8 位）方便追溯。
     */
    fun commercialTaskId(): String {
        val uuid = UUID.randomUUID().toString()
        XLog.d(TAG, "commercialTaskId: generated $uuid")
        return uuid
    }

    /**
     * 一站式生成双 ID。
     */
    fun newPair(now: Long = System.currentTimeMillis()): Pair<String, String> {
        return displayTaskId(now) to commercialTaskId()
    }

    /**
     * 提取 commercialTaskId 前 8 位用于 UI 短展示。
     */
    fun shortCommercialId(commercialTaskId: String): String {
        return commercialTaskId.take(8)
    }
}
