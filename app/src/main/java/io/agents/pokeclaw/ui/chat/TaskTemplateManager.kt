// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import android.content.Context
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Task templates — store successful task prompts as one-tap shortcuts.
 *
 * User can save any task as a template after success, browse recent templates,
 * and re-use them from the home screen "most used" list (max 5).
 */
object TaskTemplateManager {

    private const val TAG = "TaskTemplate"
    private const val KV_KEY = "task_templates_v1"
    const val MAX_RECENT_VISIBLE = 5

    data class TaskTemplate(
        val id: String,
        val name: String,
        val prompt: String,
        val tags: List<String> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val lastUsedAt: Long = 0L,
        val useCount: Int = 0,
    )

    /** Load all templates sorted by lastUsedAt desc. */
    fun loadAll(context: Context): List<TaskTemplate> {
        val raw = KVUtils.getString(KV_KEY) ?: return emptyList()
        val result = mutableListOf<TaskTemplate>()
        raw.split("|").forEach { item ->
            val parts = item.split("::", limit = 6)
            if (parts.size >= 3) {
                result.add(TaskTemplate(
                    id = parts[0],
                    name = parts[1],
                    prompt = parts[2],
                    tags = parts.getOrNull(3)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                    createdAt = parts.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis(),
                    lastUsedAt = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                ))
            }
        }
        return result.sortedByDescending { it.lastUsedAt }
    }

    /** Persist templates. */
    fun save(context: Context, templates: List<TaskTemplate>) {
        val encoded = templates.joinToString("|") { t ->
            val safeId = t.id.replace("|", "/")
            val safeName = t.name.replace("|", "/")
            val safePrompt = t.prompt.replace("|", "/")
            val safeTags = t.tags.joinToString(",").replace("|", "/")
            "$safeId::$safeName::$safePrompt::$safeTags::${t.createdAt}::${t.lastUsedAt}"
        }
        KVUtils.putString(KV_KEY, encoded)
    }

    /** Save a new template. */
    fun saveTemplate(context: Context, name: String, prompt: String, tags: List<String> = emptyList()): TaskTemplate {
        val list = loadAll(context).toMutableList()
        val now = System.currentTimeMillis()
        val template = TaskTemplate(
            id = "tpl-$now",
            name = name,
            prompt = prompt,
            tags = tags,
            createdAt = now,
            lastUsedAt = 0L,
            useCount = 0,
        )
        list.add(template)
        save(context, list)
        XLog.i(TAG, "template: save name=$name promptLen=${prompt.length}")
        return template
    }

    /** Mark a template as used (increments useCount, updates lastUsedAt). */
    fun useTemplate(context: Context, id: String) {
        val list = loadAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val updated = list[idx].copy(
            useCount = list[idx].useCount + 1,
            lastUsedAt = System.currentTimeMillis(),
        )
        list[idx] = updated
        save(context, list)
        XLog.i(TAG, "template: use name=${updated.name}")
    }

    /** Delete a template by id. */
    fun delete(context: Context, id: String) {
        val list = loadAll(context).toMutableList()
        list.removeAll { it.id == id }
        save(context, list)
        XLog.d(TAG, "template: delete id=$id")
    }

    /** Top N templates by use count (for "Most used" home list). */
    fun getMostUsed(context: Context, n: Int = MAX_RECENT_VISIBLE): List<TaskTemplate> {
        return loadAll(context)
            .sortedByDescending { it.useCount }
            .take(n)
    }
}
