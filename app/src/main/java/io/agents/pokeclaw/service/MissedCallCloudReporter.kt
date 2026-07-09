// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 漏接来电云端上报器
 * 将漏接来电事件上报到Claw后端
 */
class MissedCallCloudReporter(
    private val baseUrl: String,
    private val deviceId: String,
    private val apiKey: String? = null
) {
    companion object {
        private const val TAG = "MissedCallCloudReporter"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JSON = Json { 
            ignoreUnknownKeys = true 
            prettyPrint = false
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 上报漏接来电事件
     */
    suspend fun reportMissedCall(event: MissedCallEvent): ReportResult = withContext(Dispatchers.IO) {
        try {
            val request = MissedCallReportRequest(
                deviceId = deviceId,
                eventId = event.missedCallId,
                phoneNumber = event.phoneNumber,
                callerName = event.callerName,
                callTime = event.callTime,
                ringDurationMs = event.ringDurationMs,
                timestamp = System.currentTimeMillis()
            )

            val jsonBody = JSON.encodeToString(request)
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/hermes/device/missed-call")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    apiKey?.let { addHeader("X-API-Key", it) }
                    addHeader("X-Device-ID", deviceId)
                }
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i(TAG, "漏接来电上报成功: ${event.phoneNumber}")
                    ReportResult.Success
                } else {
                    val errorMsg = "上报失败: HTTP ${response.code}"
                    android.util.Log.e(TAG, errorMsg)
                    ReportResult.Failure(errorMsg)
                }
            }
        } catch (e: Exception) {
            val errorMsg = "上报异常: ${e.message}"
            android.util.Log.e(TAG, errorMsg, e)
            ReportResult.Failure(errorMsg)
        }
    }

    /**
     * 上报跟进消息发送结果
     */
    suspend fun reportFollowUpResult(
        eventId: String,
        message: FollowUpMessage,
        success: Boolean,
        errorMessage: String? = null
    ): ReportResult = withContext(Dispatchers.IO) {
        try {
            val request = FollowUpResultRequest(
                deviceId = deviceId,
                eventId = eventId,
                messageId = message.id,
                status = if (success) "SENT" else "FAILED",
                sendTime = message.sendTime,
                errorMessage = errorMessage,
                timestamp = System.currentTimeMillis()
            )

            val jsonBody = JSON.encodeToString(request)
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/hermes/device/missed-call/result")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    apiKey?.let { addHeader("X-API-Key", it) }
                    addHeader("X-Device-ID", deviceId)
                }
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i(TAG, "跟进结果上报成功: ${message.id}")
                    ReportResult.Success
                } else {
                    val errorMsg = "上报失败: HTTP ${response.code}"
                    android.util.Log.e(TAG, errorMsg)
                    ReportResult.Failure(errorMsg)
                }
            }
        } catch (e: Exception) {
            val errorMsg = "上报异常: ${e.message}"
            android.util.Log.e(TAG, errorMsg, e)
            ReportResult.Failure(errorMsg)
        }
    }

    /**
     * 上报结果
     */
    sealed class ReportResult {
        object Success : ReportResult()
        data class Failure(val error: String) : ReportResult()
    }

    /**
     * 漏接来电上报请求
     */
    @Serializable
    data class MissedCallReportRequest(
        val deviceId: String,
        val eventId: String,
        val phoneNumber: String,
        val callerName: String?,
        val callTime: Long,
        val ringDurationMs: Long,
        val timestamp: Long
    )

    /**
     * 跟进结果上报请求
     */
    @Serializable
    data class FollowUpResultRequest(
        val deviceId: String,
        val eventId: String,
        val messageId: String,
        val status: String,
        val sendTime: Long?,
        val errorMessage: String?,
        val timestamp: Long
    )
}
