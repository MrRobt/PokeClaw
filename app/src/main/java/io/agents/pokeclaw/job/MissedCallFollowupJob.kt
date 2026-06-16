// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.job

import android.content.Context
import android.telephony.SmsManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed SMS follow-up for missed calls.
 *
 * R2 US-B-MISSED-CALL-FOLLOWUP:
 *  - Scheduled 5s after a missed call is detected
 *  - Sends a preset SMS via [SmsManager.sendTextMessage]
 *  - Re-checks the enabled flag at execution time
 *  - Surfaces the follow-up card in the chat UI via the [MissedCallFollowupStore]
 *  - Permission-gated: READ_PHONE_STATE + SEND_SMS
 */
class MissedCallFollowupJob(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    companion object {
        private const val TAG = "MissedCallFollowupJob"
        const val UNIQUE_WORK_NAME = "pokeclaw_missed_call_followup"
        const val KEY_PHONE = "phone"
        const val KEY_RINGING_AT = "ringingAt"
        private const val DELAY_SECONDS = 5L
        private const val DEFAULT_TEMPLATE = "Sorry I missed your call — I'll get back to you shortly."

        fun schedule(context: Context, phone: String, ringingAtMillis: Long) {
            if (!KVUtils.isMissedCallFollowupEnabled()) {
                XLog.d(TAG, "missed-call schedule: feature disabled, skipping")
                return
            }
            val data = Data.Builder()
                .putString(KEY_PHONE, phone)
                .putLong(KEY_RINGING_AT, ringingAtMillis)
                .build()
            val request = OneTimeWorkRequestBuilder<MissedCallFollowupJob>()
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .setInputData(data)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            XLog.i(TAG, "missed-call: enqueued WorkManager job (delay=${DELAY_SECONDS}s) phone=$phone")
        }
    }

    override fun doWork(): Result {
        if (!KVUtils.isMissedCallFollowupEnabled()) {
            XLog.d(TAG, "missed-call doWork: feature disabled at runtime, skipping")
            return Result.success()
        }
        val phone = inputData.getString(KEY_PHONE).orEmpty()
        if (phone.isEmpty()) {
            XLog.w(TAG, "missed-call doWork: empty phone, skipping")
            return Result.success()
        }
        val template = KVUtils.getMissedCallSmsTemplate().ifBlank { DEFAULT_TEMPLATE }
        val sentAt = System.currentTimeMillis()

        val smsManager: SmsManager = try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } catch (e: Exception) {
            XLog.e(TAG, "missed-call doWork: SmsManager not available", e)
            return Result.failure()
        }
        return try {
            smsManager.sendTextMessage(phone, null, template, null, null)
            XLog.i(TAG, "missed-call doWork: SMS sent to $phone (template len=${template.length})")
            MissedCallFollowupStore.record(applicationContext, phone, template, sentAt)
            Result.success()
        } catch (e: SecurityException) {
            XLog.w(TAG, "missed-call doWork: missing SEND_SMS permission: ${e.message}")
            MissedCallFollowupStore.recordFailed(applicationContext, phone, "missing SEND_SMS permission")
            Result.failure()
        } catch (e: Exception) {
            XLog.e(TAG, "missed-call doWork: send failed for $phone", e)
            MissedCallFollowupStore.recordFailed(applicationContext, phone, e.message ?: "send failed")
            Result.retry()
        }
    }
}

/**
 * Light-weight record of recent follow-up attempts. Persisted in MMKV so the
 * ComposeChatActivity can show a follow-up card on next open.
 */
object MissedCallFollowupStore {
    private const val TAG = "MissedCallFollowupStore"
    private const val MAX_ENTRIES = 20

    data class Entry(
        val phone: String,
        val template: String,
        val sentAt: Long,
        val status: Status,
        val error: String = "",
    ) {
        enum class Status { SENT, FAILED }
    }

    fun record(context: Context, phone: String, template: String, sentAt: Long) {
        add(context, Entry(phone, template, sentAt, Entry.Status.SENT))
    }

    fun recordFailed(context: Context, phone: String, error: String) {
        add(context, Entry(phone, "", System.currentTimeMillis(), Entry.Status.FAILED, error))
    }

    fun recent(context: Context, limit: Int = 5): List<Entry> {
        val json = KVUtils.getMissedCallFollowupHistory()
        if (json.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val out = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Entry(
                        phone = o.optString("phone"),
                        template = o.optString("template"),
                        sentAt = o.optLong("sentAt"),
                        status = runCatching { Entry.Status.valueOf(o.optString("status", "SENT")) }.getOrDefault(Entry.Status.SENT),
                        error = o.optString("error", ""),
                    )
                )
            }
            out.take(limit.coerceAtLeast(1))
        } catch (e: Exception) {
            XLog.w(TAG, "recent: parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun add(context: Context, entry: Entry) {
        val current = recent(context, limit = MAX_ENTRIES)
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        val arr = org.json.JSONArray()
        updated.forEach { e ->
            val o = org.json.JSONObject()
            o.put("phone", e.phone)
            o.put("template", e.template)
            o.put("sentAt", e.sentAt)
            o.put("status", e.status.name)
            o.put("error", e.error)
            arr.put(o)
        }
        KVUtils.setMissedCallFollowupHistory(arr.toString())
    }
}
