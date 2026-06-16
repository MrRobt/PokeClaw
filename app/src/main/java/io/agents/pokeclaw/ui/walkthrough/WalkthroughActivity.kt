// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.walkthrough

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton

/**
 * 4-step first-launch walkthrough.
 *
 * Steps:
 *  1) Permissions (Accessibility / Notification)
 *  2) Model (recommend / download)
 *  3) Run quick task
 *  4) Cloud (optional)
 *
 * Each step must be completed before the next one becomes available.
 */
class WalkthroughActivity : BaseActivity() {

    companion object {
        private const val TAG = "Walkthrough"
        const val KV_WALKTHROUGH_COMPLETED = "walkthrough_completed"
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }

    private val stepTitles = listOf("Permissions", "Model", "Quick Task", "Cloud (Optional)")
    private val stepDescriptions = listOf(
        "Grant Accessibility + Notification access so PokeClaw can act on your behalf.",
        "Pick and download a local model so tasks can run offline.",
        "Run your first quick task to verify everything works.",
        "Optional: connect a cloud account for cross-device task execution.",
    )
    private val stepStatus = booleanArrayOf(false, false, false, false)
    private var currentStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }

        val toolbar = CommonToolbar(this).apply {
            setTitle("Welcome to PokeClaw")
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            showBackButton(true) { finish() }
        }
        if (intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)) {
            // From settings — back button already shows; no special title change
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        // Progress bar
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = stepTitles.size
            progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)))

        // Step container
        val stepContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        root.addView(stepContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Step list views
        val stepRows = mutableListOf<View>()
        for (i in stepTitles.indices) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setBackgroundColor(tc.bg)
            }
            val title = TextView(this).apply {
                text = "${i + 1}. ${stepTitles[i]}"
                textSize = 17f
                setTextColor(tc.aiText)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val desc = TextView(this).apply {
                text = stepDescriptions[i]
                textSize = 13f
                setTextColor(tc.userText)
                setPadding(0, dp(4), 0, dp(8))
            }
            val actionBtn = KButton(this).apply {
                text = "Start"
            }
            card.addView(title)
            card.addView(desc)
            card.addView(actionBtn)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(12)
            stepContainer.addView(card, params)
            stepRows.add(card)
            actionBtn.setOnClickListener { onStepAction(i) }
        }

        // Bottom action: finish / skip
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(20), dp(8), dp(20), dp(20))
        }
        val skipBtn = KButton(this).apply { text = "Skip" }
        skipBtn.setOnClickListener {
            XLog.i(TAG, "walkthrough: skipped at step $currentStep")
            completeWalkthrough()
        }
        val nextBtn = KButton(this).apply { text = "Next" }
        nextBtn.setOnClickListener {
            if (currentStep < stepTitles.size - 1) {
                currentStep++
                refreshStepUI(stepRows, progress)
            } else {
                completeWalkthrough()
            }
        }
        bottomBar.addView(skipBtn)
        bottomBar.addView(nextBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12)
        })
        root.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun onStepAction(index: Int) {
        XLog.d(TAG, "walkthrough: step $index tapped")
        when (index) {
            0 -> {
                // Open accessibility settings
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    Toast.makeText(this, "Enable PokeClaw, then return.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    XLog.w(TAG, "open accessibility settings failed", e)
                }
                // User must come back and tap "I have done this"
                markStepDone(index)
            }
            1 -> {
                try {
                    val intent = Intent(this, io.agents.pokeclaw.ui.settings.LlmConfigActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    XLog.w(TAG, "open models failed", e)
                }
                markStepDone(index)
            }
            2 -> {
                // Send a tiny test task
                val intent = Intent(this, io.agents.pokeclaw.ui.chat.ComposeChatActivity::class.java).apply {
                    putExtra("walkthrough_test", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                markStepDone(index)
            }
            3 -> {
                // Cloud is optional
                try {
                    val intent = Intent(this, io.agents.pokeclaw.ui.settings.SettingsActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    XLog.w(TAG, "open settings failed", e)
                }
                markStepDone(index)
            }
        }
    }

    private fun markStepDone(index: Int) {
        stepStatus[index] = true
        XLog.i(TAG, "walkthrough: step $index done (${stepTitles[index]})")
        // Advance currentStep if possible
        while (currentStep < stepStatus.size - 1 && stepStatus[currentStep + 1]) {
            currentStep++
        }
    }

    private fun refreshStepUI(rows: List<View>, progress: ProgressBar) {
        var done = 0
        for (s in stepStatus) if (s) done++
        progress.progress = done
    }

    private fun completeWalkthrough() {
        KVUtils.putBoolean(KV_WALKTHROUGH_COMPLETED, true)
        XLog.i(TAG, "walkthrough: completed=true")
        Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, io.agents.pokeclaw.ui.chat.ComposeChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
