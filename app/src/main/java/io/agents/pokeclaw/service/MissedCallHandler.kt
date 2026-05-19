// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.service

import android.content.Context
import android.telephony.SmsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 漏接来电处理器接口
 */
interface MissedCallHandler {
    /**
     * 当检测到漏接来电时调用
     */
    suspend fun onMissedCall(event: MissedCallEvent)
}

/**
 * 默认漏接来电处理器
 * 
 * 处理流程：
 * 1. 接收漏接来电事件
 * 2. 根据配置决定发送渠道（SMS优先）
 * 3. 构造跟进消息
 * 4. 延迟发送
 * 5. 更新状态到UI
 */
class DefaultMissedCallHandler(
    private val context: Context,
    private val messageSender: FollowUpMessageSender,
    private val historyManager: FollowUpHistoryManager
) : MissedCallHandler {

    companion object {
        private const val TAG = "MissedCallHandler"
    }

    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 配置
    private var config: FollowUpConfig = FollowUpConfig()

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: FollowUpConfig) {
        this.config = newConfig
    }

    override suspend fun onMissedCall(event: MissedCallEvent) {
        android.util.Log.i(TAG, "处理漏接来电: ${event.phoneNumber}, 响铃${event.ringDurationMs}ms")

        // 创建跟进消息记录
        val messageContent = generateMessageContent(event)
        val followUpMessage = FollowUpMessage.create(
            missedCall = event,
            messageContent = messageContent
        )

        // 保存到历史记录
        historyManager.addMessage(followUpMessage)

        // 延迟发送
        if (config.delayMs > 0) {
            android.util.Log.d(TAG, "延迟 ${config.delayMs}ms 后发送")
            delay(config.delayMs)
        }

        // 检查是否被取消
        if (followUpMessage.status == FollowUpStatus.CANCELLED) {
            android.util.Log.d(TAG, "消息已被取消，跳过发送")
            return
        }

        // 发送消息
        sendFollowUpMessage(followUpMessage, event)
    }

    /**
     * 生成消息内容
     */
    private fun generateMessageContent(event: MissedCallEvent): String {
        val callerName = event.callerName ?: event.phoneNumber
        return config.defaultMessage
            .replace("{name}", callerName)
            .replace("{time}", event.getFormattedTime())
    }

    /**
     * 发送跟进消息
     */
    private suspend fun sendFollowUpMessage(
        message: FollowUpMessage,
        event: MissedCallEvent
    ) {
        historyManager.updateStatus(message.id, FollowUpStatus.SENDING)

        val result = when {
            config.smsPreferred -> trySendSms(message, event)
            else -> trySendViaAccessibility(message, event)
        }

        when (result) {
            is SendResult.Success -> {
                historyManager.updateStatus(
                    messageId = message.id,
                    status = FollowUpStatus.SENT,
                    sendTime = System.currentTimeMillis()
                )
                android.util.Log.i(TAG, "跟进消息发送成功: ${event.phoneNumber}")
            }
            is SendResult.Failure -> {
                historyManager.updateStatus(
                    messageId = message.id,
                    status = FollowUpStatus.FAILED,
                    errorMessage = result.error
                )
                android.util.Log.e(TAG, "跟进消息发送失败: ${event.phoneNumber}, ${result.error}")
                
                // 如果SMS失败且允许回退到WhatsApp
                if (config.smsPreferred && config.whatsappFallback && result.canRetry) {
                    android.util.Log.d(TAG, "尝试通过WhatsApp发送")
                    trySendViaAccessibility(message, event)
                }
            }
        }
    }

    /**
     * 尝试通过SMS发送
     */
    private suspend fun trySendSms(
        message: FollowUpMessage,
        event: MissedCallEvent
    ): SendResult {
        return try {
            if (config.autoSend) {
                val smsManager = SmsManager.getDefault()
                
                // 如果消息太长，需要分割
                val parts = smsManager.divideMessage(message.messageContent)
                
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(
                        event.phoneNumber,
                        null,
                        parts,
                        null,
                        null
                    )
                } else {
                    smsManager.sendTextMessage(
                        event.phoneNumber,
                        null,
                        message.messageContent,
                        null,
                        null
                    )
                }
                
                SendResult.Success
            } else {
                // 非自动发送模式，交给UI层处理
                SendResult.Failure("需要用户确认", canRetry = false)
            }
        } catch (e: Exception) {
            SendResult.Failure("SMS发送失败: ${e.message}", canRetry = true)
        }
    }

    /**
     * 尝试通过Accessibility服务发送（WhatsApp等）
     */
    private suspend fun trySendViaAccessibility(
        message: FollowUpMessage,
        event: MissedCallEvent
    ): SendResult {
        // TODO: 通过Accessibility服务发送消息到WhatsApp
        // 这需要启动一个任务让Agent执行
        return SendResult.Failure("Accessibility发送尚未实现", canRetry = false)
    }

    /**
     * 发送结果
     */
    sealed class SendResult {
        object Success : SendResult()
        data class Failure(val error: String, val canRetry: Boolean) : SendResult()
    }

    /**
     * 释放资源
     */
    fun release() {
        handlerScope.cancel()
    }
}

/**
 * 跟进消息发送器接口
 */
interface FollowUpMessageSender {
    suspend fun sendSms(phoneNumber: String, message: String): Boolean
    suspend fun sendViaWhatsApp(phoneNumber: String, message: String): Boolean
}

/**
 * 跟进历史记录管理器
 */
class FollowUpHistoryManager {
    private val messages = ConcurrentHashMap<String, FollowUpMessage>()
    
    private val _allMessages = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val allMessages: StateFlow<List<FollowUpMessage>> = _allMessages

    fun addMessage(message: FollowUpMessage) {
        messages[message.id] = message
        updateFlow()
    }

    fun updateStatus(messageId: String, status: FollowUpStatus, sendTime: Long? = null, errorMessage: String? = null) {
        messages[messageId]?.let { msg ->
            val updated = msg.copy(
                status = status,
                sendTime = sendTime ?: msg.sendTime,
                errorMessage = errorMessage ?: msg.errorMessage
            )
            messages[messageId] = updated
            updateFlow()
        }
    }

    fun getMessage(messageId: String): FollowUpMessage? = messages[messageId]
    
    fun getPendingMessages(): List<FollowUpMessage> {
        return messages.values.filter { it.status == FollowUpStatus.PENDING }
    }

    fun getMessagesByPhoneNumber(phoneNumber: String): List<FollowUpMessage> {
        return messages.values.filter { it.phoneNumber == phoneNumber }
    }

    fun clear() {
        messages.clear()
        updateFlow()
    }

    private fun updateFlow() {
        _allMessages.value = messages.values.sortedByDescending { it.createTime }
    }
}
