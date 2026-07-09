// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * SMS服务
 * 负责发送短信和获取发送状态
 */
class SmsService(
    private val context: Context
) {
    companion object {
        private const val TAG = "SmsService"
        private const val SMS_SENT_ACTION = "io.agents.pokeclaw.SMS_SENT"
        private const val SMS_DELIVERED_ACTION = "io.agents.pokeclaw.SMS_DELIVERED"
        private const val TIMEOUT_MS = 60000L // 60秒超时
    }

    /**
     * 发送结果
     */
    data class SendResult(
        val success: Boolean,
        val message: String,
        val messageId: String? = null
    )

    /**
     * 发送短信
     * @param phoneNumber 目标电话号码
     * @param message 短信内容
     * @return 发送结果
     */
    suspend fun sendSms(phoneNumber: String, message: String): SendResult = withContext(Dispatchers.IO) {
        // 检查权限
        if (!hasSmsPermission()) {
            return@withContext SendResult(
                success = false,
                message = "缺少SMS发送权限"
            )
        }

        // 验证输入
        if (phoneNumber.isBlank()) {
            return@withContext SendResult(
                success = false,
                message = "电话号码不能为空"
            )
        }

        if (message.isBlank()) {
            return@withContext SendResult(
                success = false,
                message = "短信内容不能为空"
            )
        }

        // 检查内容长度
        if (message.length > 160 * 10) { // 最多10条短信
            return@withContext SendResult(
                success = false,
                message = "短信内容过长，超过1600字符限制"
            )
        }

        val messageId = "${System.currentTimeMillis()}_${(0..9999).random()}"
        
        try {
            // 创建发送完成和送达的 Deferred
            val sentDeferred = CompletableDeferred<Boolean>()
            val deliveredDeferred = CompletableDeferred<Boolean>()

            // 注册广播接收器
            val sentReceiver = createSentReceiver(sentDeferred)
            val deliveredReceiver = createDeliveredReceiver(deliveredDeferred)

            context.registerReceiver(sentReceiver, IntentFilter(SMS_SENT_ACTION))
            context.registerReceiver(deliveredReceiver, IntentFilter(SMS_DELIVERED_ACTION))

            try {
                // 创建PendingIntent
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.hashCode(),
                    Intent(SMS_SENT_ACTION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    messageId.hashCode() + 1,
                    Intent(SMS_DELIVERED_ACTION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 发送短信
                val smsManager = SmsManager.getDefault()
                
                // 分割长短信
                val parts = smsManager.divideMessage(message)
                
                android.util.Log.i(TAG, "正在发送短信到: $phoneNumber, 共${parts.size}条")

                if (parts.size == 1) {
                    // 单条短信
                    smsManager.sendTextMessage(
                        phoneNumber,
                        null,
                        message,
                        sentIntent,
                        deliveredIntent
                    )
                } else {
                    // 长短信
                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(sentIntent) }
                    }
                    val deliveredIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(deliveredIntent) }
                    }
                    smsManager.sendMultipartTextMessage(
                        phoneNumber,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                }

                // 等待发送结果
                val sentSuccess = withTimeout(TIMEOUT_MS) {
                    sentDeferred.await()
                }

                if (!sentSuccess) {
                    return@withContext SendResult(
                        success = false,
                        message = "短信发送失败"
                    )
                }

                // 等待送达结果（可选，不阻塞）
                val deliveredSuccess = try {
                    withTimeout(30000) {
                        deliveredDeferred.await()
                    }
                } catch (e: Exception) {
                    false // 送达确认超时不算失败
                }

                android.util.Log.i(TAG, "短信发送完成: messageId=$messageId, delivered=$deliveredSuccess")

                return@withContext SendResult(
                    success = true,
                    message = if (deliveredSuccess) "短信已送达" else "短信已发送",
                    messageId = messageId
                )

            } finally {
                // 注销广播接收器
                try {
                    context.unregisterReceiver(sentReceiver)
                } catch (e: Exception) {
                    // 忽略
                }
                try {
                    context.unregisterReceiver(deliveredReceiver)
                } catch (e: Exception) {
                    // 忽略
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "发送短信异常", e)
            return@withContext SendResult(
                success = false,
                message = "发送异常: ${e.message}"
            )
        }
    }

    /**
     * 创建发送状态接收器
     */
    private fun createSentReceiver(deferred: CompletableDeferred<Boolean>): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val result = when (resultCode) {
                    android.app.Activity.RESULT_OK -> {
                        android.util.Log.d(TAG, "SMS发送成功")
                        true
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        android.util.Log.e(TAG, "SMS发送失败: 通用错误")
                        false
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        android.util.Log.e(TAG, "SMS发送失败: 无服务")
                        false
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        android.util.Log.e(TAG, "SMS发送失败: PDU为空")
                        false
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        android.util.Log.e(TAG, "SMS发送失败: 无线关闭")
                        false
                    }
                    else -> {
                        android.util.Log.e(TAG, "SMS发送失败: 未知错误 code=$resultCode")
                        false
                    }
                }
                deferred.complete(result)
            }
        }
    }

    /**
     * 创建送达状态接收器
     */
    private fun createDeliveredReceiver(deferred: CompletableDeferred<Boolean>): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val delivered = resultCode == android.app.Activity.RESULT_OK
                android.util.Log.d(TAG, "SMS送达状态: $delivered")
                deferred.complete(delivered)
            }
        }
    }

    /**
     * 检查SMS权限
     */
    fun hasSmsPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查设备是否支持SMS
     */
    fun isSmsCapable(): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) 
                as android.telephony.TelephonyManager
            telephonyManager.isSmsCapable
        } catch (e: Exception) {
            false
        }
    }
}
