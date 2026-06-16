package io.agents.pokeclaw.agent.learning

import android.content.Context
import io.agents.pokeclaw.cloud.ExperienceLocalCache
import io.agents.pokeclaw.cloud.ExperienceReader
import io.agents.pokeclaw.utils.XLog

/**
 * Local learning loop for task execution.
 *
 * It stores compact success/failure experiences locally and can inject the
 * most recent useful examples into the next agent prompt. Cloud sync can reuse
 * the same ExperienceReader/ExperienceLocalCache data model.
 */
class TaskLearningManager(
    private val context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "TaskLearning"
        private const val LOCAL_BASE_URL = "http://localhost"
        private const val LOCAL_DEVICE_ID = "local"
        private const val MAX_CACHE_ENTRIES = 200
        private val KEYWORD_REGEX = Regex("[\\p{L}\\p{N}]{2,}")
    }

    private val promptReader = ExperienceReader(
        context = context,
        baseUrl = LOCAL_BASE_URL,
        deviceId = LOCAL_DEVICE_ID,
    )

    fun buildPrompt(taskText: String): String {
        val section = buildPromptSection() ?: return taskText
        return buildString {
            appendLine(section)
            appendLine()
            appendLine("## Current task")
            append(taskText)
        }
    }

    fun buildPromptSection(): String? {
        return runCatching {
            val experiences = ExperienceLocalCache.load(context)
            promptReader.fewShotFor(experiences).asPromptSection()
        }.onFailure { e ->
            XLog.w(TAG, "buildPromptSection failed: ${e.message}", e)
        }.getOrNull()
    }

    fun recordSuccess(
        taskId: String,
        taskText: String,
        summary: String,
        strategyKeywords: List<String> = keywordsFor(taskText),
    ): ExperienceReader.Experience {
        val experience = ExperienceReader.Experience(
            commercialTaskId = taskId.ifBlank { fallbackTaskId(taskText) },
            type = ExperienceReader.Experience.Type.SUCCESS,
            summary = compact("Task: $taskText\nResult: $summary"),
            strategyKeywords = strategyKeywords,
            recordedAt = clock(),
        )
        append(experience)
        return experience
    }

    fun recordFailure(
        taskId: String,
        taskText: String,
        errorCategory: String,
        errorCode: String,
        recoveryHint: String,
    ): ExperienceReader.Experience {
        val experience = ExperienceReader.Experience(
            commercialTaskId = taskId.ifBlank { fallbackTaskId(taskText) },
            type = ExperienceReader.Experience.Type.FAILURE,
            summary = compact(taskText),
            errorCategory = errorCategory.ifBlank { "TASK_FAILED" },
            errorCode = errorCode.ifBlank { "UNKNOWN" },
            recoveryHint = recoveryHint.ifBlank { "Retry with a different strategy." },
            strategyKeywords = keywordsFor(taskText),
            recordedAt = clock(),
        )
        append(experience)
        return experience
    }

    private fun append(experience: ExperienceReader.Experience) {
        runCatching {
            val existing = ExperienceLocalCache.load(context)
            val next = buildList {
                add(experience)
                existing
                    .asSequence()
                    .filterNot { it.commercialTaskId == experience.commercialTaskId && it.type == experience.type }
                    .take(MAX_CACHE_ENTRIES - 1)
                    .forEach { add(it) }
            }
            ExperienceLocalCache.save(context, next)
        }.onFailure { e ->
            XLog.w(TAG, "append failed: ${e.message}", e)
        }
    }

    private fun fallbackTaskId(taskText: String): String {
        val hash = Integer.toHexString(taskText.trim().lowercase().hashCode())
        return "local-$hash"
    }

    private fun compact(text: String, maxLen: Int = 500): String {
        val normalized = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (normalized.length <= maxLen) normalized else normalized.take(maxLen).trimEnd() + "..."
    }

    private fun keywordsFor(taskText: String): List<String> {
        return KEYWORD_REGEX.findAll(taskText.lowercase())
            .map { it.value }
            .distinct()
            .take(8)
            .toList()
    }
}
