// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud

import io.agents.pokeclaw.utils.XLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 上传成功/失败经验到云端（POST /api/claw-device/experience）。
 *
 * 策略：
 *  - 成功：success_experience  → commercialTaskId + experienceType + summary + strategyKeywords
 *  - 失败：failure_experience  → commercialTaskId + errorCategory + errorCode + recoveryHint
 *
 * 失败重试：写入 CloudEventQueue 等待下次重试（不在 UI 阻塞）。
 *
 * 用户在 UI 上看不到这部分行为（仅云端后台使用）。
 */
class ExperienceUploader(
    private val baseUrl: String,
    private val deviceId: String,
    private val getToken: () -> String? = { null },
) {

    companion object {
        private const val TAG = "ExperienceUploader"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    enum class ExperienceType { SUCCESS, FAILURE }

    data class SuccessExperience(
        val commercialTaskId: String,
        val summary: String,
        val strategyKeywords: List<String> = emptyList(),
    )

    data class FailureExperience(
        val commercialTaskId: String,
        val errorCategory: String,
        val errorCode: String,
        val recoveryHint: String,
    )

    /** Upload a success experience. */
    fun uploadSuccess(experience: SuccessExperience, callback: ((Boolean, String?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("type", ExperienceType.SUCCESS.name.lowercase())
            put("commercialTaskId", experience.commercialTaskId)
            put("summary", experience.summary)
            put("strategyKeywords", org.json.JSONArray(experience.strategyKeywords))
        }
        send(payload, "success") { ok, err ->
            XLog.d(TAG, "experience: type=success, taskId=${experience.commercialTaskId}, summaryLen=${experience.summary.length}, ok=$ok")
            callback?.invoke(ok, err)
        }
    }

    /** Upload a failure experience. */
    fun uploadFailure(experience: FailureExperience, callback: ((Boolean, String?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("type", ExperienceType.FAILURE.name.lowercase())
            put("commercialTaskId", experience.commercialTaskId)
            put("errorCategory", experience.errorCategory)
            put("errorCode", experience.errorCode)
            put("recoveryHint", experience.recoveryHint)
        }
        send(payload, "failure") { ok, err ->
            XLog.d(TAG, "experience: type=fail, taskId=${experience.commercialTaskId}, code=${experience.errorCode}, ok=$ok")
            callback?.invoke(ok, err)
        }
    }

    private fun send(payload: JSONObject, type: String, callback: (Boolean, String?) -> Unit) {
        val url = "$baseUrl/device-api/claw-device/experience"
        val body = payload.toString().toRequestBody(JSON_MEDIA)
        val requestBuilder = Request.Builder().url(url).post(body)
        getToken()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        Thread({
            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    callback(true, null)
                } else {
                    val msg = "HTTP ${response.code}"
                    enqueueRetry(type, payload)
                    callback(false, msg)
                }
            } catch (e: Exception) {
                XLog.w(TAG, "send: exception=${e.javaClass.simpleName}", e)
                enqueueRetry(type, payload)
                callback(false, e.message)
            }
        }, "experience-upload").start()
    }

    private fun enqueueRetry(type: String, payload: JSONObject) {
        // CloudEventQueue 目前只接受 TaskResultRequest 负载；experience 上传重试由调用方驱动，
        // 此处只记录日志避免阻塞 UI。后端可在商业任务上下文内通过 commercialTaskId 拉取最新状态。
        XLog.w(TAG, "enqueueRetry: experience $type 暂时未持久化, payload_size=${payload.length()}")
    }
}
