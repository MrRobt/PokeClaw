// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import io.agents.pokeclaw.job.MissedCallFollowupJob
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Listens for call state changes; when a call goes IDLE while we previously
 * saw RINGING with a non-empty incoming number, AND the call duration was
 * < 5s (i.e. effectively a missed call), it enqueues a delayed SMS follow-up.
 *
 * R2 US-B-MISSED-CALL-FOLLOWUP:
 *  - Reads EXTRA_STATE=IDLE
 *  - Captures incomingNumber
 *  - Computes duration: now - ringingAtMillis; only fires if < 5_000ms
 *  - Requires [KVUtils.isMissedCallFollowupEnabled] (default OFF)
 *  - Schedules [MissedCallFollowupJob] 5s later (gives the user a chance to
 *    pick up a second incoming call before sending the SMS)
 *  - Does NOT depend on accessibility or WhatsApp automation
 */
class MissedCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MissedCallReceiver"
        const val MAX_CALL_DURATION_MS = 5_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (number.isNotEmpty()) {
                    KVUtils.setLastRingingNumber(number)
                    KVUtils.setLastRingingAt(System.currentTimeMillis())
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (!KVUtils.isMissedCallFollowupEnabled()) {
                    XLog.d(TAG, "missed-call: feature disabled, skipping")
                    return
                }
                val lastNumber = KVUtils.getLastRingingNumber()
                val ringingAt = KVUtils.getLastRingingAt()
                if (lastNumber.isEmpty() || ringingAt <= 0L) {
                    XLog.d(TAG, "missed-call: no recent ringing recorded, skipping")
                    return
                }
                val duration = System.currentTimeMillis() - ringingAt
                if (duration >= MAX_CALL_DURATION_MS) {
                    XLog.d(TAG, "missed-call: duration=${duration}ms (>= ${MAX_CALL_DURATION_MS}), probably answered, skipping")
                    return
                }
                XLog.i(TAG, "missed-call: number=$lastNumber duration=${duration}ms, scheduling follow-up")
                MissedCallFollowupJob.schedule(context, lastNumber, ringingAt)
                // Clear state to prevent double-schedule
                KVUtils.setLastRingingNumber("")
                KVUtils.setLastRingingAt(0L)
            }
        }
    }
}
