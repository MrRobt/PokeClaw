// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.console

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.R
import io.agents.pokeclaw.TaskEvent
import io.agents.pokeclaw.appViewModel
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.channel.Channel
import io.agents.pokeclaw.role.LobsterRoleManager
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton
import java.util.UUID

/**
 * End-side background console — drive PokeClaw tasks locally without the cloud admin.
 *
 * R2 US-B-OW-001-ON-DEVICE-CONSOLE:
 *  - Top EditText + Send button + history dropdown
 *  - Click send → TaskOrchestrator.startNewTask(channel=LOCAL, messageId=UUID)
 *  - Middle RecyclerView event stream (LinearLayout + TextView per event for simplicity)
 *  - Bottom: active model + role + network type
 *  - Registered in AndroidManifest
 *  - XLog.d on-device-console send task log line
 */
class OnDeviceConsoleActivity : BaseActivity() {

    companion object {
        private const val TAG = "OnDeviceConsole"
        private const val MAX_HISTORY = 20
        private const val MAX_EVENTS = 200
    }

    private val history = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private lateinit var historySpinner: Spinner
    private lateinit var eventContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var input: EditText
    private var spinnerAdapter: ArrayAdapter<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(tc.bg) }
        val toolbar = CommonToolbar(this).apply {
            setTitle(getString(R.string.on_device_console_title))
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            showBackButton(true) { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        // === Top: input + history dropdown + send + clear ===
        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        historySpinner = Spinner(this).apply {
            prompt = getString(R.string.on_device_console_history)
        }
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, history)
        historySpinner.adapter = spinnerAdapter
        val spinnerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        spinnerParams.bottomMargin = dp(8)
        topSection.addView(historySpinner, spinnerParams)

        input = EditText(this).apply {
            hint = getString(R.string.on_device_console_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 5
        }
        val inputParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        inputParams.bottomMargin = dp(8)
        topSection.addView(input, inputParams)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val clearBtn = KButton(this).apply { text = getString(R.string.on_device_console_clear) }
        clearBtn.setOnClickListener {
            input.setText("")
            events.clear()
            eventContainer.removeAllViews()
            refreshStatusLine()
        }
        val sendBtn = KButton(this).apply { text = getString(R.string.on_device_console_send) }
        sendBtn.setOnClickListener { onSend() }
        buttonRow.addView(clearBtn)
        buttonRow.addView(sendBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        topSection.addView(buttonRow)
        root.addView(topSection, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // === Middle: event stream ===
        val scrollEvents = ScrollView(this).apply { isFillViewport = true }
        eventContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        scrollEvents.addView(eventContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scrollEvents, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // === Bottom: status line ===
        statusText = TextView(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(12))
            textSize = 12f
        }
        root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshStatusLine()

        setContentView(root)
        // Hook into the task event stream
        appViewModel.taskOrchestrator.taskEventCallback = { event -> onTaskEvent(event) }
    }

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter a task first", Toast.LENGTH_SHORT).show()
            return
        }
        val messageId = UUID.randomUUID().toString()
        XLog.d(TAG, "on-device-console send task: messageId=$messageId text='${text.take(60)}'")
        history.add(0, text)
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        spinnerAdapter?.notifyDataSetChanged()
        input.setText("")
        // Dispatch via TaskOrchestrator
        try {
            appViewModel.startTask(
                task = text,
                taskId = messageId,
                onEvent = { event -> onTaskEvent(event) },
            )
        } catch (e: Exception) {
            XLog.e(TAG, "send task failed", e)
            appendEvent("SEND FAILED: ${e.message}")
        }
    }

    private fun onTaskEvent(event: TaskEvent) {
        runOnUiThread {
            val text = when (event) {
                is TaskEvent.Response -> "response: ${event.text.take(120)}"
                is TaskEvent.ToolAction -> "tool: ${event.toolName}"
                is TaskEvent.ToolResult -> "tool result: ${event.toolName} ok=${event.success} data=${event.detail.take(80)}"
                is TaskEvent.LoopStart -> "loop start: round=${event.round}"
                is TaskEvent.Progress -> "progress: step=${event.step} ${event.description}"
                is TaskEvent.TokenUpdate -> "tokens: ${event.formattedTokens} cost=${event.formattedCost}"
                is TaskEvent.Completed -> "✓ done: ${event.answer.take(120)}"
                is TaskEvent.Failed -> "✗ failed: ${event.error}"
                is TaskEvent.Cancelled -> "cancelled"
                is TaskEvent.Blocked -> "blocked (system dialog)"
                is TaskEvent.Thinking -> "thinking: ${event.content.take(80)}"
                is TaskEvent.NeedsHuman -> "needs human: ${event.reason}"
            }
            appendEvent(text)
        }
    }

    private fun appendEvent(text: String) {
        val tc = ThemeManager.getColors()
        val line = TextView(this).apply {
            this.text = "${System.currentTimeMillis() % 100000}  $text"
            textSize = 12f
            setTextColor(tc.aiText)
            setPadding(0, dp(2), 0, dp(2))
        }
        eventContainer.addView(line)
        events.add(text)
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
            eventContainer.removeViewAt(0)
        }
    }

    private fun refreshStatusLine() {
        val roleName = LobsterRoleManager.get(this).activeRole()?.name ?: "default"
        val modelName = KVUtils.getLlmModelName().ifEmpty { "default" }
        val netType = runCatching {
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            when {
                caps == null -> "none"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        }.getOrDefault("unknown")
        statusText.text = getString(R.string.on_device_console_status, modelName, roleName, netType)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
