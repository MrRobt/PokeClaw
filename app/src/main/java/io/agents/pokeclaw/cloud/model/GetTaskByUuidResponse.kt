// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.
//
// GET /api/claw-device/tasks/{taskUuid} 响应模型 — 对齐 device.openapi.yaml v1.1.0 C3-01。
// 端侧拿到该响应后可用于：状态确认 / 断点续跑 / 结果兜底。

package io.agents.pokeclaw.cloud.model

import com.google.gson.annotations.SerializedName

/**
 * GET /api/claw-device/tasks/{taskUuid} 响应。
 *
 * OpenAPI 定义：`{ code, data: DeviceTaskVO, msg }`。
 * 端侧语义：code=0/200 视为成功，data 为 null 表示任务不存在或已回收。
 */
data class GetTaskByUuidResponse(

    @SerializedName("code")
    val code: Int? = null,

    @SerializedName("msg")
    val msg: String? = null,

    @SerializedName("data")
    val data: DeviceTaskVO? = null,
) {
    fun isSuccess(): Boolean = code == 0 || code == 200
}
