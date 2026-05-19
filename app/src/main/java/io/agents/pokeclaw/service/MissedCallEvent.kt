// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.service

/**
 * 漏接来电事件数据类
 */
data class MissedCallEvent(
    val phoneNumber: String,
    val callerName: String?,
    val callTime: Long,
    val ringDurationMs: Long,
    val missedCallId: String
) {
    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(callTime))
    }

    /**
     * 获取响铃时长描述
     */
    fun getRingDurationDesc(): String {
        return when {
            ringDurationMs < 5000 -> "短响 (<5秒)"
            ringDurationMs < 15000 -> "正常 (${ringDurationMs / 1000}秒)"
            else -> "长响 (${ringDurationMs / 1000}秒)"
        }
    }
}

/**
 * 跟进消息状态
 */
enum class FollowUpStatus {
    PENDING,      // 待发送
    SENDING,      // 发送中
    SENT,         // 已发送
    FAILED,       // 发送失败
    CANCELLED     // 已取消
}

/**
 * 跟进消息记录
 */
data class FollowUpMessage(
    val id: String,
    val missedCallId: String,
    val phoneNumber: String,
    val messageContent: String,
    val status: FollowUpStatus,
    val createTime: Long,
    var sendTime: Long? = null,
    var errorMessage: String? = null,
    val chatSessionId: String? = null
) {
    companion object {
        fun create(
            missedCall: MissedCallEvent,
            messageContent: String,
            chatSessionId: String? = null
        ): FollowUpMessage {
            return FollowUpMessage(
                id = java.util.UUID.randomUUID().toString(),
                missedCallId = missedCall.missedCallId,
                phoneNumber = missedCall.phoneNumber,
                messageContent = messageContent,
                status = FollowUpStatus.PENDING,
                createTime = System.currentTimeMillis(),
                chatSessionId = chatSessionId
            )
        }
    }
}

/**
 * 跟进配置
 */
data class FollowUpConfig(
    val enabled: Boolean = false,
    val autoSend: Boolean = false,
    val defaultMessage: String = "抱歉，刚才在忙没有接到您的电话，请问有什么可以帮您？",
    val delayMs: Long = 3000,  // 延迟发送时间
    val smsPreferred: Boolean = true,  // 优先使用SMS
    val whatsappFallback: Boolean = false,  // SMS失败时尝试WhatsApp
    val businessHoursOnly: Boolean = false,  // 仅工作时间发送
    val businessStartHour: Int = 9,
    val businessEndHour: Int = 18,
    val excludeContacts: Set<String> = emptySet()  // 排除的联系人
) {
    /**
     * 检查当前时间是否在业务时间内
     */
    fun isBusinessHours(): Boolean {
        if (!businessHoursOnly) return true
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in businessStartHour until businessEndHour
    }
}
