// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.diagnose

import io.agents.pokeclaw.utils.XLog
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Auto-diagnose a task failure by grabbing a logcat snapshot and matching
 * it against [LogcatRuleEngine].
 *
 * Output: errorDetail string suitable for embedding in TaskResultRequest.
 */
object FailureAutoDiagnose {

    private const val TAG = "FailureAutoDiagnose"
    private const val LOGCAT_LINES = 200

    data class Result(
        val errorCode: String,
        val errorDetail: String,
        val suggestion: String,
        val matchedRuleId: String? = null,
    )

    /**
     * Capture recent logcat (last 200 lines) and diagnose.
     * Falls back to the input errorMessage if logcat capture fails.
     */
    fun diagnose(taskUuid: String, errorMessage: String?): Result {
        val logcat = try {
            captureLogcat()
        } catch (e: Exception) {
            XLog.w(TAG, "diagnose: logcat capture failed: ${e.message}")
            ""
        }
        val diagnosis = LogcatRuleEngine.diagnose(logcat)
        if (diagnosis == null) {
            XLog.i(TAG, "auto-diagnose: errorCode=unknown matchedRule=none suggestion=retry")
            return Result(
                errorCode = "unknown",
                errorDetail = errorMessage ?: "Unknown error",
                suggestion = "Try again, or check device network and accessibility settings.",
                matchedRuleId = null,
            )
        }
        val result = Result(
            errorCode = diagnosis.ruleId.uppercase(),
            errorDetail = "${diagnosis.userMessage} (matched: ${diagnosis.matchedSnippet})",
            suggestion = diagnosis.suggestion,
            matchedRuleId = diagnosis.ruleId,
        )
        XLog.i(TAG, "auto-diagnose: errorCode=${result.errorCode} matchedRule=${result.matchedRuleId} suggestion=${result.suggestion}")
        return result
    }

    private fun captureLogcat(): String {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", LOGCAT_LINES.toString()))
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.forEachLine { line ->
                sb.appendLine(line)
            }
        }
        process.waitFor()
        return sb.toString()
    }
}
