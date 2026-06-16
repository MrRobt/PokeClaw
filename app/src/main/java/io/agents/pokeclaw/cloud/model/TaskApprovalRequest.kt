// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud.model

import com.google.gson.annotations.SerializedName

/**
 * Owner-approval decision record sent to `POST /api/claw/task-approval`.
 *
 * @param requestId Unique approval request id (de-dup key for the offline queue).
 * @param taskUuid The task the decision is about.
 * @param decision One of "APPROVE" / "REJECT".
 * @param deviceId The device that owned the task (auto-filled by CloudApprovalReporter).
 * @param decidedAtMillis When the user pressed the button.
 * @param reason Optional human-readable reason (e.g. for REJECT).
 */
data class TaskApprovalRequest(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("taskUuid") val taskUuid: String,
    @SerializedName("decision") val decision: String,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("decidedAtMillis") val decidedAtMillis: Long = System.currentTimeMillis(),
    @SerializedName("reason") val reason: String? = null,
)
