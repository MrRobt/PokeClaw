// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Quick action cards — custom prompt shortcuts on the chat home screen.
 *
 * Stored in KV as a JSON-ish list. Up to 8 cards visible at once.
 * Built-in defaults seeded on first use.
 */
object QuickActionManager {

    private const val TAG = "QuickAction"
    private const val KV_KEY = "quick_actions_v1"
    const val MAX_VISIBLE = 8

    data class QuickAction(
        val id: String,
        val title: String,
        val prompt: String,
        val icon: String = "▶",
        val position: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
    )

    /** Built-in defaults shown on first run. */
    fun defaultActions(): List<QuickAction> = listOf(
        QuickAction(id = "default-weather", title = "Weather", prompt = "What's the weather today?", icon = "☀", position = 0),
        QuickAction(id = "default-notifications", title = "Read Notifications", prompt = "Read my recent notifications", icon = "🔔", position = 1),
        QuickAction(id = "default-storage", title = "Storage Status", prompt = "Check my phone storage status", icon = "💾", position = 2),
        QuickAction(id = "default-battery", title = "Battery Status", prompt = "Check my battery status", icon = "🔋", position = 3),
    )

    /** Load all quick actions, seeding defaults on first use. */
    fun loadAll(context: Context): List<QuickAction> {
        val raw = KVUtils.getString(KV_KEY) ?: run {
            val defaults = defaultActions()
            save(context, defaults)
            return defaults
        }
        val result = mutableListOf<QuickAction>()
        raw.split("|").forEachIndexed { index, item ->
            val parts = item.split("::", limit = 4)
            if (parts.size >= 3) {
                result.add(QuickAction(
                    id = parts[0].ifEmpty { "u-$index-${System.currentTimeMillis()}" },
                    title = parts[1],
                    prompt = parts[2],
                    icon = parts.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: "▶",
                    position = index,
                ))
            }
        }
        return result.ifEmpty {
            val defaults = defaultActions()
            save(context, defaults)
            defaults
        }
    }

    /** Persist the list (overwrites). */
    fun save(context: Context, actions: List<QuickAction>) {
        val encoded = actions.joinToString("|") { action ->
            // 简单的字段转义：把 | 替换为 /，:: 替换为 //
            val safeId = action.id.replace("|", "/").replace("::", "//")
            val safeTitle = action.title.replace("|", "/").replace("::", "//")
            val safePrompt = action.prompt.replace("|", "/").replace("::", "//")
            val safeIcon = action.icon.replace("|", "/").replace("::", "//")
            "$safeId::$safeTitle::$safePrompt::$safeIcon"
        }
        KVUtils.putString(KV_KEY, encoded)
        XLog.d(TAG, "save: count=${actions.size}")
    }

    /** Add a new action at the end. */
    fun add(context: Context, title: String, prompt: String, icon: String = "▶"): QuickAction {
        val list = loadAll(context).toMutableList()
        if (list.size >= MAX_VISIBLE) {
            XLog.w(TAG, "add: max reached, dropping oldest")
            list.removeAt(list.size - 1)
        }
        val newAction = QuickAction(
            id = "user-${System.currentTimeMillis()}",
            title = title,
            prompt = prompt,
            icon = icon,
            position = list.size,
        )
        list.add(newAction)
        save(context, list)
        XLog.i(TAG, "quick-action: tap id=${newAction.id} prompt=$prompt")
        return newAction
    }

    /** Delete an action by id. */
    fun delete(context: Context, id: String) {
        val list = loadAll(context).toMutableList()
        list.removeAll { it.id == id }
        save(context, list)
        XLog.d(TAG, "delete: id=$id")
    }

    /** Move an action to a new position (for "pin to top"). */
    fun moveTo(context: Context, id: String, newPosition: Int) {
        val list = loadAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = list.removeAt(idx)
        val safePos = newPosition.coerceIn(0, list.size)
        list.add(safePos, item.copy(position = safePos))
        // re-number positions
        val renum = list.mapIndexed { i, a -> a.copy(position = i) }
        save(context, renum)
    }
}
