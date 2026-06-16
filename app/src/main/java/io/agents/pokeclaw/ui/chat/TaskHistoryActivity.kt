// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.CommonToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务历史列表 + 搜索 / 状态 / 时间范围 / 类型过滤。
 *
 * 布局：搜索框 + 4 个下拉过滤 + 列表。
 */
class TaskHistoryActivity : BaseActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView
    private var currentFilter = TaskHistoryManager.Filter()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)
        setContentView(R.layout.activity_task_history)

        val contentFrame = findViewById<ViewGroup>(android.R.id.content)
        contentFrame?.setBackgroundColor(tc.bg)
        (contentFrame?.getChildAt(0) as? View)?.setBackgroundColor(tc.bg)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle("Task History")
            setTitleColor(tc.aiText)
            setBackgroundColor(tc.toolbarBg)
            showBackButton(true) { finish() }
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }

        listContainer = findViewById(R.id.listContainer)
        emptyView = findViewById(R.id.emptyView)
        emptyView.setTextColor(tc.aiText)
        listContainer.setBackgroundColor(tc.bg)

        setupFilters()
        refresh()
    }

    private fun setupFilters() {
        val searchBox = findViewById<EditText>(R.id.searchBox)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentFilter = currentFilter.copy(query = s?.toString())
                refresh()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val statusSpinner = findViewById<Spinner>(R.id.statusSpinner)
        val statusOptions = arrayOf("All", "Success", "Failed", "Running", "Cancelled")
        statusSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statusOptions)
        statusSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = currentFilter.copy(
                    status = if (position == 0) null else TaskHistoryManager.Status.values()[position - 1]
                )
                refresh()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val timeSpinner = findViewById<Spinner>(R.id.timeSpinner)
        val timeOptions = TaskHistoryManager.TimeRange.values().map { it.displayName }.toTypedArray()
        timeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeOptions)
        timeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = currentFilter.copy(timeRange = TaskHistoryManager.TimeRange.values()[position])
                refresh()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val typeSpinner = findViewById<Spinner>(R.id.typeSpinner)
        val typeOptions = arrayOf("All", "Chat", "Task", "Monitor", "Cloud Task")
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeOptions)
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = currentFilter.copy(
                    type = if (position == 0) null else TaskHistoryManager.TaskType.values()[position - 1]
                )
                refresh()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun refresh() {
        val result = TaskHistoryManager.query(currentFilter)
        listContainer.removeAllViews()
        if (result.records.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = if (TaskHistoryManager.totalCount() == 0) {
                "No tasks yet"
            } else {
                "No matching tasks"
            }
        } else {
            emptyView.visibility = View.GONE
            result.records.forEach { record -> listContainer.addView(createRecordView(record)) }
        }
        XLog.d(TAG, "refresh: filter=$currentFilter totalCount=${result.totalCount} hasMore=${result.hasMore}")
    }

    private fun createRecordView(record: TaskHistoryManager.TaskRecord): View {
        val tc = ThemeManager.getColors()
        val view = layoutInflater.inflate(R.layout.item_task_history, listContainer, false)
        view.findViewById<TextView>(R.id.tvDisplayId).apply {
            text = record.displayTaskId
            setTextColor(tc.sendColor)
        }
        view.findViewById<TextView>(R.id.tvStatus).apply {
            text = record.status.name
            setTextColor(when (record.status) {
                TaskHistoryManager.Status.SUCCESS -> tc.sendColor
                TaskHistoryManager.Status.FAILED -> android.graphics.Color.RED
                TaskHistoryManager.Status.RUNNING -> 0xFFFFA500.toInt()
                TaskHistoryManager.Status.CANCELLED -> tc.toolDefault
            })
        }
        view.findViewById<TextView>(R.id.tvTaskText).apply {
            text = record.taskText
            setTextColor(tc.aiText)
        }
        view.findViewById<TextView>(R.id.tvMeta).apply {
            text = "${dateFormat.format(Date(record.createdAtMillis))} • ${record.type} • ${record.totalTokens} tokens"
            setTextColor(tc.toolDefault)
        }
        return view
    }

    companion object {
        private const val TAG = "TaskHistoryActivity"
    }
}
