// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.app.Activity
import android.content.Intent
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
import io.agents.pokeclaw.memory.UserMemoryStore
import io.agents.pokeclaw.utils.XLog
import java.io.File

/**
 * User memory management screen (US-D-018-USER-MEMORY).
 *
 * Lists all stored memory entries, lets the user add / delete / export them.
 * The export writes JSON to Downloads/PokeClaw/memory-{timestamp}.json so it
 * can be inspected by the user or imported into another tool.
 */
class MemoryActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "MemoryActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: MemoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)
        title = "我的记忆"

        listView = findViewById(R.id.lv_memory)
        adapter = MemoryAdapter(this, UserMemoryStore.listAll())
        listView.adapter = adapter

        findViewById<Button>(R.id.btn_memory_add).setOnClickListener { showAddDialog() }
        findViewById<Button>(R.id.btn_memory_export).setOnClickListener { exportMemory() }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle("删除记忆？")
                .setMessage(entry.text)
                .setPositiveButton("删除") { _, _ ->
                    UserMemoryStore.delete(entry.id)
                    adapter.replaceAll(UserMemoryStore.listAll())
                    XLog.i(TAG, "memory: deleted id=${entry.id}")
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(UserMemoryStore.listAll())
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "例如：我偏好深色主题"
            setSingleLine(false)
            setMinLines(2)
        }
        AlertDialog.Builder(this)
            .setTitle("记住新条目")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString().trim()
                when (val result = UserMemoryStore.add(text)) {
                    is UserMemoryStore.AddResult.Accepted -> {
                        adapter.replaceAll(UserMemoryStore.listAll())
                        XLog.i(TAG, "memory: saved id=${result.entry.id}")
                    }
                    is UserMemoryStore.AddResult.Rejected -> {
                        val msg = when (result.reason) {
                            UserMemoryStore.REJECTED_SECRET -> "检测到可能的密钥/口令，已拒绝保存"
                            UserMemoryStore.REJECTED_TOO_LONG -> "内容超过 ${UserMemoryStore.MAX_ENTRY_LEN} 字符"
                            else -> "保存失败：${result.reason}"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportMemory() {
        val json = UserMemoryStore.exportJson()
        val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
        val out = File(dir, "memory-${System.currentTimeMillis()}.json")
        runCatching {
            out.writeText(json)
            Toast.makeText(this, "已导出到 ${out.absolutePath}", Toast.LENGTH_LONG).show()
            XLog.i(TAG, "memory: exported path=${out.absolutePath} bytes=${json.length}")
        }.onFailure { e ->
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            XLog.e(TAG, "memory: export failed", e)
        }
    }
}

private class MemoryAdapter(
    activity: Activity,
    initial: List<UserMemoryStore.MemoryEntry>,
) : ArrayAdapter<UserMemoryStore.MemoryEntry>(activity, 0, initial.toMutableList()) {

    fun replaceAll(newList: List<UserMemoryStore.MemoryEntry>) {
        clear()
        addAll(newList)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val entry = getItem(position) ?: return view
        (view as LinearLayout).removeAllViews()
        val textView = TextView(parent.context).apply {
            text = entry.text
            textSize = 14f
        }
        val meta = TextView(parent.context).apply {
            text = "${entry.source.name} • ${entry.useCount} 次 • 最近 ${entry.lastUsedAt / 1000}"
            textSize = 11f
            alpha = 0.6f
        }
        view.addView(textView)
        view.addView(meta)
        return view
    }
}