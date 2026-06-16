// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.app.AlertDialog
import android.content.Context
import io.agents.pokeclaw.R
import io.agents.pokeclaw.task.TaskFailureClassifier

/**
 * 任务失败对话框：把错误归类 + 提供可操作建议。
 *
 * UI 规则：
 *  - 标题显示错误分类（Environment / Business / System）
 *  - 正文显示人类可读的错误描述
 *  - 按钮根据建议动态生成
 */
object TaskFailureDialog {

    /**
     * 显示失败对话框。
     *
     * @param onAction 动作处理回调，参数是用户点击的按钮文字
     */
    fun show(
        context: Context,
        classification: TaskFailureClassifier.Classification,
        onAction: (action: String) -> Unit = {},
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Task failed • ${classification.category.displayName}")
        builder.setMessage(classification.humanMessage)

        // 第一个动作：主操作（Retry）
        builder.setPositiveButton(classification.suggestedActions.firstOrNull() ?: "OK") { _, _ ->
            onAction(classification.suggestedActions.firstOrNull() ?: "OK")
        }
        // 中间动作：其他建议
        classification.suggestedActions.drop(1).forEachIndexed { index, action ->
            if (index == 0) {
                builder.setNegativeButton(action) { _, _ -> onAction(action) }
            } else {
                builder.setNeutralButton(action) { _, _ -> onAction(action) }
            }
        }
        // 兜底 Cancel
        if (classification.suggestedActions.size <= 1) {
            builder.setNegativeButton(context.getString(R.string.common_cancel)) { d, _ -> d.dismiss() }
        }
        builder.show()
    }
}
