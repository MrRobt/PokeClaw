// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.R
import io.agents.pokeclaw.instructions.GlobalInstructionsStore
import io.agents.pokeclaw.utils.XLog

/**
 * Editor for the persistent global instructions (US-D-022).
 *
 * Lets the user view, edit, save, and clear a short instructions block
 * that's prepended to every new task. Length and secret-pattern rules are
 * enforced here at the UI layer (the store also enforces them, defense in depth).
 */
class GlobalInstructionsActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "GlobalInstructionsActivity"
    }

    private lateinit var input: EditText
    private lateinit var counter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_instructions)
        title = "全局指令"

        input = findViewById(R.id.et_global_instructions)
        counter = findViewById(R.id.tv_global_counter)

        input.setText(GlobalInstructionsStore.get())
        updateCounter(input.text.length)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCounter(s?.length ?: 0)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.btn_global_save).setOnClickListener { save() }
        findViewById<Button>(R.id.btn_global_clear).setOnClickListener { confirmClear() }
    }

    private fun updateCounter(length: Int) {
        counter.text = "$length / ${GlobalInstructionsStore.MAX_LEN}"
        counter.alpha = if (length > GlobalInstructionsStore.MAX_LEN) 1f else 0.6f
    }

    private fun save() {
        val text = input.text.toString()
        when (val err = GlobalInstructionsStore.set(text)) {
            null -> {
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                XLog.i(TAG, "instructions: saved via UI")
                finish()
            }
            GlobalInstructionsStore.REJECTED_TOO_LONG ->
                Toast.makeText(this, "内容超过 ${GlobalInstructionsStore.MAX_LEN} 字符", Toast.LENGTH_LONG).show()
            GlobalInstructionsStore.REJECTED_SECRET ->
                Toast.makeText(this, "检测到可能的密钥/口令，已拒绝保存", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "保存失败：$err", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空全局指令？")
            .setMessage("已保存的全局指令将被移除，后续任务不再自动追加。")
            .setPositiveButton("清空") { _, _ ->
                GlobalInstructionsStore.clear()
                input.setText("")
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                XLog.i(TAG, "instructions: cleared via UI")
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
