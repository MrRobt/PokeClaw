// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * UI to manage persistent monitor nicknames (US-D-023-MONITOR-NICKNAMES).
 *
 * Lists every entry in [MonitorNicknameStore]; long-press to clear, tap to
 * edit. New nicknames are added via a dialog that takes a stableId + name.
 */
class MonitorNicknamesActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "MonitorNicknamesActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: NicknameAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor_nicknames)
        title = "监控昵称"

        listView = findViewById(R.id.lv_monitor_nicknames)
        adapter = NicknameAdapter(this, MonitorNicknameStore.listAll().toList())
        listView.adapter = adapter

        findViewById<Button>(R.id.btn_nickname_add).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    internal fun refresh() {
        adapter.replaceAll(MonitorNicknameStore.listAll().toList())
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val idInput = EditText(this).apply { hint = "稳定 ID（phone:15551234 或 tg:alice）" }
        val nickInput = EditText(this).apply { hint = "昵称" }
        container.addView(idInput)
        container.addView(nickInput)
        AlertDialog.Builder(this)
            .setTitle("添加监控昵称")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val id = idInput.text.toString().trim()
                val nick = nickInput.text.toString().trim()
                if (id.isBlank() || nick.isBlank()) {
                    Toast.makeText(this, "ID 与昵称均不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                MonitorNicknameStore.setNickname(id, nick)
                refresh()
                XLog.i(TAG, "nickname: added id=$id len=${nick.length}")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    internal fun confirmEdit(existing: Pair<String, String>) {
        val input = EditText(this).apply { setText(existing.second) }
        AlertDialog.Builder(this)
            .setTitle("编辑昵称")
            .setMessage("ID: ${existing.first}")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                MonitorNicknameStore.setNickname(existing.first, input.text.toString())
                refresh()
            }
            .setNeutralButton("删除") { _, _ ->
                MonitorNicknameStore.clearAll(existing.first)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

private class NicknameAdapter(
    activity: Activity,
    initial: List<Pair<String, String>>,
) : ArrayAdapter<Pair<String, String>>(activity, 0, initial.toMutableList()) {

    fun replaceAll(newList: List<Pair<String, String>>) {
        clear()
        addAll(newList)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (id, nick) = getItem(position) ?: return convertView ?: LinearLayout(parent.context)
        val row: LinearLayout
        val holder: NicknameRowHolder
        if (convertView == null) {
            row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 16, 24, 16)
            }
            val title = TextView(parent.context).apply { textSize = 14f }
            val sub = TextView(parent.context).apply {
                textSize = 11f
                alpha = 0.6f
            }
            row.addView(title)
            row.addView(sub)
            holder = NicknameRowHolder(title, sub)
            row.tag = holder
        } else {
            row = convertView as LinearLayout
            holder = convertView.tag as NicknameRowHolder
        }
        holder.title.text = nick
        holder.sub.text = "id=$id"
        row.setOnClickListener { (parent.context as MonitorNicknamesActivity).confirmEdit(id to nick) }
        row.setOnLongClickListener {
            AlertDialog.Builder(parent.context)
                .setTitle("删除昵称？")
                .setMessage("$nick ($id)")
                .setPositiveButton("删除") { _, _ ->
                    MonitorNicknameStore.clearAll(id)
                    (parent.context as MonitorNicknamesActivity).refresh()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        return row
    }
}

private data class NicknameRowHolder(val title: TextView, val sub: TextView)
