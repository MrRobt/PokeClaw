// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.app.Activity
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * Owner approval dialog (端内 AlertDialog) for high-stakes tasks.
 *
 * R2 US-B-TASK-APPROVAL:
 *  - Shown when a task metadata has priority=HIGH or costYuan >= 1.0
 *  - Shows: task summary + estimated cost + risk assessment
 *  - Two actions: Approve (continues execution) / Reject (REJECTED terminal state)
 *  - The result is reported back to [io.agents.pokeclaw.TaskOrchestrator] via the callback
 *  - Note: the actual dialog is constructed by the caller to keep the activity
 *    reference and styling; this helper centralizes the layout + strings
 */
object OwnerApprovalDialog {

    private const val TAG = "OwnerApproval"

    data class ApprovalRequest(
        val taskText: String,
        val estimatedCostYuan: Double = 0.0,
        val priority: String = "NORMAL",
        val riskAssessment: String = "No specific risks identified",
    )

    enum class Decision { APPROVE, REJECT }

    fun show(
        activity: Activity,
        request: ApprovalRequest,
        onDecision: (Decision) -> Unit,
    ) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        container.addView(buildRow(activity, "Task", request.taskText.take(280)))
        container.addView(buildRow(activity, "Priority", request.priority))
        container.addView(buildRow(activity, "Estimated cost", String.format("¥%.2f", request.estimatedCostYuan)))
        container.addView(buildRow(activity, "Risk", request.riskAssessment))

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.task_approval_title))
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.task_approval_approve)) { d, _ ->
                XLog.i(TAG, "task-approval: approve task='${request.taskText.take(40)}' cost=${request.estimatedCostYuan}")
                onDecision(Decision.APPROVE)
                d.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.task_approval_reject)) { d, _ ->
                XLog.i(TAG, "task-approval: reject task='${request.taskText.take(40)}'")
                onDecision(Decision.REJECT)
                d.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun buildRow(activity: Activity, label: String, value: String): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * activity.resources.displayMetrics.density).toInt(), 0, 0)
        }
        val labelView = TextView(activity).apply {
            text = "$label: "
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val valueView = TextView(activity).apply {
            text = value
            setPadding((8 * activity.resources.displayMetrics.density).toInt(), 0, 0, 0)
        }
        row.addView(labelView)
        row.addView(valueView)
        return row
    }
}
