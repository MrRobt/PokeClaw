// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.role

import android.app.AlertDialog as AndroidAlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.role.LobsterRole
import io.agents.pokeclaw.role.LobsterRoleManager
import io.agents.pokeclaw.role.LobsterRoleStore
import io.agents.pokeclaw.ui.chat.ThemeManager
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.KButton

/**
 * Lobster role manager UI — list, activate, pause, disable, create, edit, delete.
 *
 * R2 US-B-CMP-1-1 acceptance criteria:
 *  - Top toolbar with title + "Role Store" entry (light cloud)
 *  - Scrollable card per role: name / source / status / version / activate-pause-disable buttons
 *  - Bottom: "+ New Role" button opens an editor dialog
 *  - Active role highlighted in green
 */
class LobsterRoleManagerActivity : BaseActivity() {

    companion object {
        private const val TAG = "LobsterRoleManagerUI"
    }

    private lateinit var manager: LobsterRoleManager
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = LobsterRoleManager.get(this)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tc.bg)
        }

        val toolbar = CommonToolbar(this).apply {
            setTitle(getString(R.string.lobster_role_manager_title))
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            showBackButton(true) { finish() }
        }
        val storeBtn = KButton(this).apply {
            text = getString(R.string.lobster_role_store_button)
        }
        storeBtn.setOnClickListener {
            try {
                startActivity(Intent(this@LobsterRoleManagerActivity, LobsterRoleStoreActivity::class.java))
            } catch (e: Exception) {
                XLog.w(TAG, "open role store failed", e)
                Toast.makeText(this@LobsterRoleManagerActivity, "Open role store failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
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
        val newBtn = KButton(this).apply {
            text = getString(R.string.lobster_role_new_button)
        }
        newBtn.setOnClickListener { showEditor(null) }
        bottomBar.addView(storeBtn)
        bottomBar.addView(newBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12)
        })
        root.addView(bottomBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()
        val tc = ThemeManager.getColors()
        val activeId = KVUtils.getLobsterActiveRoleId()
        val roles = manager.list()
        if (roles.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.lobster_role_empty)
                setTextColor(tc.userText)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            }
            container.addView(empty)
            return
        }
        roles.forEach { role ->
            container.addView(buildRoleCard(role, activeId, tc))
        }
    }

    private fun buildRoleCard(role: LobsterRole, activeId: String, tc: io.agents.pokeclaw.ui.chat.ThemeManager.ChatColors): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(tc.bg)
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(10)
        card.layoutParams = params

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = role.name
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(tc.aiText)
        }
        val isActive = role.id == activeId && role.status == LobsterRole.Status.ENABLED
        if (isActive) {
            val badge = TextView(this).apply {
                text = "  ${getString(R.string.lobster_role_active_badge)}  "
                setBackgroundColor(Color.parseColor("#1A6F3F"))
                setTextColor(Color.WHITE)
                textSize = 11f
            }
            titleRow.addView(title)
            titleRow.addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        } else {
            titleRow.addView(title)
        }
        card.addView(titleRow)

        val meta = TextView(this).apply {
            text = "id=${role.id} · v${role.version} · ${role.source.name} · ${role.status.name}"
            textSize = 11f
            setTextColor(tc.userText)
            setPadding(0, dp(2), 0, dp(6))
        }
        card.addView(meta)

        val summary = TextView(this).apply {
            text = role.role.take(220)
            textSize = 13f
            setTextColor(tc.aiText)
            setPadding(0, 0, 0, dp(8))
        }
        card.addView(summary)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (role.source == LobsterRole.Source.USER) {
            val editBtn = KButton(this).apply { text = getString(R.string.lobster_role_edit) }
            editBtn.setOnClickListener { showEditor(role) }
            actionRow.addView(editBtn)
            val delBtn = KButton(this).apply { text = getString(R.string.lobster_role_delete) }
            delBtn.setOnClickListener { confirmDelete(role) }
            actionRow.addView(delBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        if (role.status == LobsterRole.Status.ENABLED && !isActive) {
            val actBtn = KButton(this).apply { text = getString(R.string.lobster_role_activate) }
            actBtn.setOnClickListener {
                if (manager.activate(role.id)) {
                    XLog.d(TAG, "lobster-role activate ui: id=${role.id}")
                    render()
                }
            }
            actionRow.addView(actBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        if (role.status == LobsterRole.Status.ENABLED) {
            val pauseBtn = KButton(this).apply { text = getString(R.string.lobster_role_pause) }
            pauseBtn.setOnClickListener {
                manager.pause(role.id)
                render()
            }
            actionRow.addView(pauseBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        } else if (role.status == LobsterRole.Status.PAUSED) {
            val resumeBtn = KButton(this).apply { text = getString(R.string.lobster_role_resume) }
            resumeBtn.setOnClickListener {
                manager.activate(role.id)
                render()
            }
            actionRow.addView(resumeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
            val disBtn = KButton(this).apply { text = getString(R.string.lobster_role_disable) }
            disBtn.setOnClickListener {
                manager.disable(role.id)
                render()
            }
            actionRow.addView(disBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        } else if (role.status == LobsterRole.Status.DISABLED) {
            val enBtn = KButton(this).apply { text = getString(R.string.lobster_role_enable) }
            enBtn.setOnClickListener {
                manager.activate(role.id)
                render()
            }
            actionRow.addView(enBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        card.addView(actionRow)
        return card
    }

    private fun confirmDelete(role: LobsterRole) {
        AndroidAlertDialog.Builder(this)
            .setTitle(getString(R.string.lobster_role_delete_title))
            .setMessage(getString(R.string.lobster_role_delete_message, role.name))
            .setPositiveButton(getString(R.string.lobster_role_delete_confirm)) { _, _ ->
                if (manager.deleteLocal(role.id)) {
                    Toast.makeText(this, getString(R.string.lobster_role_deleted_toast, role.name), Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            .setNegativeButton(getString(R.string.lobster_role_cancel), null)
            .show()
    }

    private fun showEditor(existing: LobsterRole?) {
        val pad = dp(16)
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.lobster_role_name_hint)
            setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val roleInput = EditText(this).apply {
            hint = getString(R.string.lobster_role_role_hint)
            setText(existing?.role ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setPadding(0, dp(8), 0, 0)
        }
        val dutiesInput = EditText(this).apply {
            hint = getString(R.string.lobster_role_duties_hint)
            setText(existing?.duties?.joinToString("\n") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setPadding(0, dp(8), 0, 0)
        }
        containerLayout.addView(nameInput)
        containerLayout.addView(roleInput)
        containerLayout.addView(dutiesInput)
        val dialog = AndroidAlertDialog.Builder(this)
            .setTitle(if (existing == null) getString(R.string.lobster_role_new_title) else getString(R.string.lobster_role_edit_title, existing.name))
            .setView(containerLayout)
            .setPositiveButton(getString(R.string.lobster_role_save)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val roleText = roleInput.text.toString().trim()
                val duties = dutiesInput.text.toString().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isEmpty() || roleText.isEmpty()) {
                    Toast.makeText(this, getString(R.string.lobster_role_validation), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    manager.createLocal(name = name, role = roleText, duties = duties)
                } else {
                    manager.updateLocal(existing.copy(name = name, role = roleText, duties = duties))
                }
                render()
            }
            .setNegativeButton(getString(R.string.lobster_role_cancel), null)
            .create()
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
