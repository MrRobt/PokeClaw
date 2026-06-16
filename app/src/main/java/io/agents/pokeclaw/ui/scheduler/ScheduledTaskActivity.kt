// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.scheduler

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.R
import io.agents.pokeclaw.scheduler.CronParser
import io.agents.pokeclaw.scheduler.ScheduledTask
import io.agents.pokeclaw.scheduler.ScheduledTaskDao
import io.agents.pokeclaw.scheduler.TaskScheduler
import io.agents.pokeclaw.utils.XLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Settings screen for scheduled tasks (US-D-021-TASK-SCHEDULER).
 *
 * Lists all scheduled tasks, allows adding new ones (CRON / ONCE / INTERVAL)
 * with a prompt string, and toggling their enabled flag.
 */
class ScheduledTaskActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ScheduledTaskActivity"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: TaskAdapter
    private lateinit var dao: ScheduledTaskDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_task)
        title = "任务调度器"

        dao = ScheduledTaskDao(this)
        listView = findViewById(R.id.lv_scheduled)
        adapter = TaskAdapter(this, dao.listAll())
        listView.adapter = adapter

        findViewById<Button>(R.id.btn_sched_add).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(dao.listAll())
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameInput = EditText(this).apply { hint = "任务名" }
        val typeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val cronRadio = android.widget.RadioButton(this).apply { text = "CRON"; isChecked = true; id = View.generateViewId() }
        val onceRadio = android.widget.RadioButton(this).apply { text = "ONCE"; id = View.generateViewId() }
        val intervalRadio = android.widget.RadioButton(this).apply { text = "INTERVAL"; id = View.generateViewId() }
        typeGroup.addView(cronRadio)
        typeGroup.addView(onceRadio)
        typeGroup.addView(intervalRadio)
        val scheduleInput = EditText(this).apply {
            hint = "CRON: 分 时 日 月 周\nONCE: 毫秒时间戳\nINTERVAL: 秒数 (≥ 60)"
            minLines = 2
        }
        val promptInput = EditText(this).apply {
            hint = "要执行的任务内容"
            minLines = 2
        }
        container.addView(nameInput)
        container.addView(typeGroup)
        container.addView(scheduleInput)
        container.addView(promptInput)

        AlertDialog.Builder(this)
            .setTitle("添加定时任务")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val schedule = scheduleInput.text.toString().trim()
                val prompt = promptInput.text.toString().trim()
                if (name.isBlank() || schedule.isBlank() || prompt.isBlank()) {
                    Toast.makeText(this, "名称/时间/任务内容均不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = when (typeGroup.checkedRadioButtonId) {
                    cronRadio.id -> ScheduledTask.Type.CRON
                    onceRadio.id -> ScheduledTask.Type.ONCE
                    intervalRadio.id -> ScheduledTask.Type.INTERVAL
                    else -> ScheduledTask.Type.CRON
                }
                val err = validate(type, schedule)
                if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val task = ScheduledTask(
                    id = "sched-" + UUID.randomUUID().toString().take(8),
                    name = name,
                    type = type,
                    schedule = schedule,
                    prompt = prompt,
                    enabled = true,
                )
                TaskScheduler.enable(this, task)
                adapter.replaceAll(dao.listAll())
                XLog.i(TAG, "scheduler: added id=${task.id} type=${task.type}")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun validate(type: ScheduledTask.Type, schedule: String): String? = when (type) {
        ScheduledTask.Type.CRON -> if (CronParser.isValid(schedule)) null else ScheduledTask.INVALID_CRON
        ScheduledTask.Type.ONCE -> if (schedule.toLongOrNull() != null) null else ScheduledTask.INVALID_TIMESTAMP
        ScheduledTask.Type.INTERVAL -> {
            val secs = schedule.toLongOrNull() ?: return ScheduledTask.INVALID_INTERVAL
            if (secs < ScheduledTask.MIN_INTERVAL_SEC) ScheduledTask.INVALID_INTERVAL else null
        }
    }
}

private class TaskAdapter(
    activity: android.app.Activity,
    initial: List<ScheduledTask>,
) : ArrayAdapter<ScheduledTask>(activity, 0, initial.toMutableList()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun replaceAll(newList: List<ScheduledTask>) {
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
        val title = TextView(parent.context).apply {
            text = item.name
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = CheckBox(parent.context).apply {
            isChecked = item.enabled
            setOnCheckedChangeListener { _, checked ->
                val dao = ScheduledTaskDao(parent.context)
                val t = dao.get(item.id) ?: return@setOnCheckedChangeListener
                val updated = t.copy(enabled = checked)
                if (checked) {
                    io.agents.pokeclaw.scheduler.TaskScheduler.enable(parent.context, updated)
                } else {
                    io.agents.pokeclaw.scheduler.TaskScheduler.disable(parent.context, updated)
                }
            }
        }
        header.addView(title)
        header.addView(toggle)
        view.addView(header)

        val meta = TextView(parent.context).apply {
            val next = if (item.nextRunAt > 0L) dateFormat.format(Date(item.nextRunAt)) else "—"
            val last = if (item.lastRunAt > 0L) dateFormat.format(Date(item.lastRunAt)) else "—"
            text = "${item.type} • schedule='${item.schedule.take(40)}' • 下次=$next 上次=$last"
            textSize = 11f
            alpha = 0.6f
        }
        view.addView(meta)

        view.setOnLongClickListener {
            AlertDialog.Builder(parent.context)
                .setTitle("删除定时任务？")
                .setMessage(item.name)
                .setPositiveButton("删除") { _, _ ->
                    io.agents.pokeclaw.scheduler.TaskScheduler.disable(parent.context, item)
                    ScheduledTaskDao(parent.context).delete(item.id)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        return view
    }
}