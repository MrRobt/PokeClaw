// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 设备注册请求数据模型 — 对齐 device.openapi.yaml

package io.agents.pokeclaw.cloud.model

import com.google.gson.annotations.SerializedName

/**
 * 设备注册请求
 * POST /api/claw-device/register
 */
data class DeviceRegisterRequest(
    @SerializedName("deviceId")
    val deviceId: String,
    
    @SerializedName("deviceName")
    val deviceName: String? = null,
    
    @SerializedName("deviceModel")
    val deviceModel: String? = null,
    
    @SerializedName("androidVersion")
    val androidVersion: String? = null,
    
    @SerializedName("appVersion")
    val appVersion: String? = null,
    
    @SerializedName("publicKey")
    val publicKey: String? = null
)

/**
 * 设备注册响应
 */
data class DeviceRegisterResponse(
    @SerializedName("deviceToken")
    val deviceToken: String,
    
    @SerializedName("refreshToken")
    val refreshToken: String,
    
    @SerializedName("expiresIn")
    val expiresIn: Int
)

/**
 * 设备心跳请求
 * POST /api/claw-device/heartbeat
 */
data class DeviceHeartbeatRequest(
    @SerializedName("batteryLevel")
    val batteryLevel: Int? = null,
    
    @SerializedName("isCharging")
    val isCharging: Boolean? = null,
    
    @SerializedName("networkType")
    val networkType: String? = null  // wifi, cellular, offline
)

/**
 * 设备心跳响应
 */
data class DeviceHeartbeatResponse(
    @SerializedName("pendingTaskCount")
    val pendingTaskCount: Int = 0,
    
    @SerializedName("skillVersion")
    val skillVersion: Int = 0,
    
    @SerializedName("serverTime")
    val serverTime: Long = 0
)

/**
 * Token 刷新请求
 * POST /api/claw-device/token/refresh
 */
data class TokenRefreshRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

/**
 * Token 刷新响应
 */
data class TokenRefreshResponse(
    @SerializedName("deviceToken")
    val deviceToken: String,
    
    @SerializedName("expiresIn")
    val expiresIn: Int
)

/**
 * 待处理任务项
 */
data class PendingTaskItem(
    @SerializedName("taskUuid")
    val taskUuid: String,
    
    @SerializedName("command")
    val command: String,
    
    @SerializedName("mode")
    val mode: String? = null,
    
    @SerializedName("createdAt")
    val createdAt: Long,
    
    @SerializedName("priority")
    val priority: String? = null
)

/**
 * 任务结果上报请求
 * POST /api/claw-device/tasks/{taskUuid}/result
 *
 * 对齐 device.openapi.yaml，扩展错误回传字段
 */
data class TaskResultRequest(
    @SerializedName("status")
    val status: String,  // SUCCESS, FAILED, RUNNING, CANCELLED

    @SerializedName("result")
    val result: String? = null,

    @SerializedName("errorMessage")
    val errorMessage: String? = null,  // 错误信息（用户可读）

    @SerializedName("executionTimeMs")
    val executionTimeMs: Long? = null,

    @SerializedName("toolCalls")
    val toolCalls: String? = null,

    @SerializedName("evidenceUrls")
    val evidenceUrls: String? = null,

    @SerializedName("modelUsed")
    val modelUsed: String? = null,

    // 新增：失败回传字段
    @SerializedName("errorCategory")
    val errorCategory: String? = null,  // 错误大类

    @SerializedName("errorCode")
    val errorCode: String? = null,  // 错误码

    @SerializedName("errorDetail")
    val errorDetail: String? = null,  // 详细错误信息（技术层面）

    @SerializedName("recoverable")
    val recoverable: Boolean? = null,  // 是否可重试

    @SerializedName("suggestedAction")
    val suggestedAction: String? = null,  // 建议用户操作

    @SerializedName("screenshotBase64")
    val screenshotBase64: String? = null,  // 失败时的截图（可选）

    @SerializedName("logSnippet")
    val logSnippet: String? = null  // 相关日志片段
)

/**
 * 通用 API 响应包装
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("msg")
    val msg: String? = null,
    
    @SerializedName("data")
    val data: T? = null
) {
    fun isSuccess(): Boolean = code == 0 || code == 200
}

/**
 * 设备云端状态枚举
 */
enum class DeviceCloudStatus {
    UNREGISTERED,   // 未注册
    REGISTERED,     // 已注册，未连接
    ONLINE,         // 在线（心跳正常）
    OFFLINE,        // 离线（心跳失败）
    ERROR           // 错误状态
}

/**
 * 网络类型枚举
 */
enum class NetworkType(val value: String) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    OFFLINE("offline"),
    UNKNOWN("unknown")
}

/**
 * 任务状态枚举
 */
enum class TaskStatus(val value: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED")
}
