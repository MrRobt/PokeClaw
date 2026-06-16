// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.R
import io.agents.pokeclaw.agent.llm.CustomModelSource
import io.agents.pokeclaw.agent.llm.CustomModelSourceStore
import io.agents.pokeclaw.utils.XLog

/**
 * Settings screen for user-defined model sources (US-D-020-CUSTOM-MODEL-SOURCE).
 *
 * Shows the current list, allows adding a new one with URL/sha256/size/minRam,
 * and toggling / deleting existing entries.
 */
class CustomModelSourceActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "CustomModelSourceActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: SourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_model_source)
        title = "自定义模型源"

        listView = findViewById(R.id.lv_custom_sources)
        adapter = SourceAdapter(this, CustomModelSourceStore.listAll())
        listView.adapter = adapter

        findViewById<Button>(R.id.btn_custom_source_add).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(CustomModelSourceStore.listAll())
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameInput = EditText(this).apply { hint = "名称（必填）" }
        val urlInput = EditText(this).apply { hint = "HTTPS URL（必填）" }
        val shaInput = EditText(this).apply { hint = "SHA-256（可选，64 位 hex）" }
        val sizeInput = EditText(this).apply { hint = "Size MB（可选）" }
        val ramInput = EditText(this).apply { hint = "Min RAM GB（可选）" }
        container.addView(nameInput)
        container.addView(urlInput)
        container.addView(shaInput)
        container.addView(sizeInput)
        container.addView(ramInput)

        AlertDialog.Builder(this)
            .setTitle("添加自定义模型源")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString()
                val url = urlInput.text.toString()
                val sha = shaInput.text.toString().takeIf { it.isNotBlank() }
                val sizeMb = sizeInput.text.toString().toLongOrNull()
                val ramGb = ramInput.text.toString().toIntOrNull()
                val result = CustomModelSourceStore.add(
                    name = name,
                    url = url,
                    sha256 = sha,
                    sizeBytes = sizeMb?.let { it * 1_000_000L },
                    minRamGb = ramGb,
                )
                when (result) {
                    is CustomModelSourceStore.AddResult.Accepted -> {
                        adapter.replaceAll(CustomModelSourceStore.listAll())
                        XLog.i(TAG, "custom-model: saved id=${result.source.id}")
                    }
                    is CustomModelSourceStore.AddResult.Rejected -> {
                        val msg = when (result.reason) {
                            "URL_NOT_HTTPS" -> "URL 必须为 HTTPS"
                            "URL_NOT_ALLOWED" -> "URL host 不在白名单 (huggingface.co / github.com / gitlab.com / raw.githubusercontent.com)"
                            "INVALID_SHA256_FORMAT" -> "SHA-256 必须是 64 位 hex 字符串"
                            "MAX_SOURCES_EXCEEDED" -> "已达上限 ${CustomModelSourceStore.MAX_SOURCES} 条"
                            else -> "保存失败：${result.reason}"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

private class SourceAdapter(
    activity: android.app.Activity,
    initial: List<CustomModelSource>,
) : ArrayAdapter<CustomModelSource>(activity, 0, initial.toMutableList()) {

    fun replaceAll(newList: List<CustomModelSource>) {
        clear()
        addAll(newList)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val item = getItem(position) ?: return view
        (view as LinearLayout).removeAllViews()

        val header = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val name = TextView(parent.context).apply {
            text = item.name
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val enabled = CheckBox(parent.context).apply {
            isChecked = item.enabled
            setOnCheckedChangeListener { _, checked ->
                CustomModelSourceStore.setEnabled(item.id, checked)
            }
        }
        header.addView(name)
        header.addView(enabled)
        view.addView(header)

        val url = TextView(parent.context).apply {
            text = item.url
            textSize = 11f
            alpha = 0.6f
        }
        view.addView(url)
        if (!item.sha256.isNullOrBlank()) {
            val sha = TextView(parent.context).apply {
                text = "sha256=${item.sha256.take(16)}…"
                textSize = 11f
                alpha = 0.6f
            }
            view.addView(sha)
        }

        view.setOnLongClickListener {
            AlertDialog.Builder(parent.context)
                .setTitle("删除自定义模型源？")
                .setMessage(item.name)
                .setPositiveButton("删除") { _, _ ->
                    CustomModelSourceStore.delete(item.id)
                    XLog.i("CustomModelSourceActivity", "custom-model: deleted id=${item.id}")
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        return view
    }
}