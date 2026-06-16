// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.
//
// POST /api/claw-device/tasks/{taskUuid}/cancel 响应模型 — 对齐 device.openapi.yaml v1.1.0 C3-01。

package io.agents.pokeclaw.cloud.model

import com.google.gson.annotations.SerializedName

/**
 * POST /api/claw-device/tasks/{taskUuid}/cancel 响应。
 *
 * OpenAPI 定义：`{ code, data: boolean, msg }`。
 * - data=true：取消成功，任务从未到达终态
 * - data=false：任务不存在或已是终态（SUCCESS / FAILED / CANCELLED）
 *
 * 端侧语义：仍以 HTTP 2xx + code=0/200 视为网络层成功；data 决定业务层是否成功。
 */
data class CancelTaskResponse(

    @SerializedName("code")
    val code: Int? = null,

    @SerializedName("msg")
    val msg: String? = null,

    @SerializedName("data")
    val data: Boolean? = null,
) {
    fun isSuccess(): Boolean = code == 0 || code == 200

    /** 后端报告的"是否真的取消了该任务"。false 通常意味着终态冲突，调用方可降级处理。 */
    fun cancelled(): Boolean = isSuccess() && data == true
}
