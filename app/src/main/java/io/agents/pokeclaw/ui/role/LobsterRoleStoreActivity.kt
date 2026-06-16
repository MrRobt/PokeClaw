// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.role

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.role.LobsterRoleStore
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton

/**
 * Lobster role store — browse pre-defined roles from the (light) cloud.
 *
 * R2 US-B-ROLE-STORE: list / get / activate / sync.
 * End-side cache hits → return; first/expired → sync() fetches the contract.
 */
class LobsterRoleStoreActivity : BaseActivity() {

    companion object {
        private const val TAG = "LobsterRoleStore"
    }

    private lateinit var store: LobsterRoleStore
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LobsterRoleStore.get(this)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(tc.bg) }
        val toolbar = CommonToolbar(this).apply {
            setTitle(getString(R.string.lobster_role_store_title))
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            showBackButton(true) { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val syncBtn = KButton(this).apply { text = getString(R.string.lobster_role_store_sync) }
        syncBtn.setOnClickListener { onSync() }
        bottomBar.addView(syncBtn)
        root.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun onSync() {
        val result = store.sync()
        Toast.makeText(this, "Synced: ${result.fetched} fetched, ${result.applied} applied, ${result.skipped} skipped${result.error?.let { " (err=$it)" } ?: ""}", Toast.LENGTH_SHORT).show()
        XLog.d(TAG, "role-store sync: $result")
        render()
    }

    private fun render() {
        container.removeAllViews()
        val tc = ThemeManager.getColors()
        val entries = store.list()
        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.lobster_role_store_empty)
                setTextColor(tc.userText)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            }
            container.addView(empty)
            return
        }
        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(tc.bg)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(10)
            card.layoutParams = params
            val title = TextView(this).apply {
                text = entry.name
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(tc.aiText)
            }
            card.addView(title)
            val meta = TextView(this).apply {
                text = "id=${entry.id} · v${entry.version} · ${if (entry.cached) "cached" else "fresh"}"
                textSize = 11f
                setTextColor(tc.userText)
                setPadding(0, dp(2), 0, dp(6))
            }
            card.addView(meta)
            val summary = TextView(this).apply {
                text = entry.role.take(220)
                textSize = 13f
                setTextColor(tc.aiText)
                setPadding(0, 0, 0, dp(8))
            }
            card.addView(summary)
            val actBtn = KButton(this).apply { text = "Activate" }
            actBtn.setOnClickListener {
                if (store.activate(entry.id)) {
                    Toast.makeText(this, "Activated: ${entry.name}", Toast.LENGTH_SHORT).show()
                }
            }
            card.addView(actBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            container.addView(card)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
