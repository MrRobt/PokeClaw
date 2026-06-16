// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.diagnose

import io.agents.pokeclaw.utils.XLog

/**
 * Rule engine that maps logcat snippets to actionable diagnoses.
 *
 * Each rule has:
 *  - id: machine-friendly code (e.g., "model_load_fail")
 *  - regex: logcat pattern to match
 *  - userMessage: human-readable message
 *  - suggestion: what the user should try
 */
object LogcatRuleEngine {

    private const val TAG = "LogcatRule"

    data class Rule(
        val id: String,
        val regex: Regex,
        val userMessage: String,
        val suggestion: String,
    )

    data class Diagnosis(
        val ruleId: String,
        val userMessage: String,
        val suggestion: String,
        val matchedSnippet: String,
    )

    val RULES: List<Rule> = listOf(
        Rule(
            id = "model_load_fail",
            regex = Regex("(?i)(model.{0,20}load|InterpreterCreate).{0,50}(fail|error|abort)"),
            userMessage = "Local model failed to load",
            suggestion = "Try re-downloading the model or switching to a smaller variant.",
        ),
        Rule(
            id = "network_unreachable",
            regex = Regex("(?i)(network.{0,30}(unreachable|fail)|UnknownHostException|ConnectException)"),
            userMessage = "Network unreachable",
            suggestion = "Check Wi-Fi or cellular connection, then retry.",
        ),
        Rule(
            id = "accessibility_disabled",
            regex = Regex("(?i)(accessibility.{0,30}(disabled|disconnected)|ServiceNotConnectedException)"),
            userMessage = "Accessibility service is disabled",
            suggestion = "Open Settings → Accessibility → enable PokeClaw.",
        ),
        Rule(
            id = "permission_denied",
            regex = Regex("(?i)(SecurityException|Permission.{0,20}denied)"),
            userMessage = "Required permission was denied",
            suggestion = "Open Settings → Apps → PokeClaw → Permissions and grant missing ones.",
        ),
        Rule(
            id = "oom",
            regex = Regex("(?i)(OutOfMemoryError|GC.{0,15}overhead|low.{0,10}memory)"),
            userMessage = "Out of memory",
            suggestion = "Close other apps and retry. Consider switching to a smaller model.",
        ),
        Rule(
            id = "ssl_cert",
            regex = Regex("(?i)(SSLHandshakeException|TrustManager|HostnameVerifier|cert.{0,15}expired)"),
            userMessage = "SSL/TLS error",
            suggestion = "Check device date/time and network certificate.",
        ),
        Rule(
            id = "token_expired",
            regex = Regex("(?i)(token.{0,15}expired|401.{0,20}Unauthorized)"),
            userMessage = "Cloud token expired",
            suggestion = "Re-enroll device or refresh token.",
        ),
        Rule(
            id = "input_timeout",
            regex = Regex("(?i)(Timed out waiting for|injectInputEvent.{0,30}timed)"),
            userMessage = "Input event timed out",
            suggestion = "Wake up the screen and unlock the device, then retry.",
        ),
        Rule(
            id = "tflite_init",
            regex = Regex("(?i)(TensorFlowLite|Interpreter).{0,30}(native crash|init.{0,20}fail)"),
            userMessage = "TensorFlow Lite init failed",
            suggestion = "Reinstall the app or contact support with logs.",
        ),
        Rule(
            id = "tool_unknown",
            regex = Regex("(?i)(unknown tool|ToolNotFound|no tool named)"),
            userMessage = "Agent referenced an unknown tool",
            suggestion = "This is a model issue — try rephrasing the task.",
        ),
    )

    /**
     * Run rules against a logcat snippet. Returns the first match (deterministic order).
     * If no rule matches, returns null.
     */
    fun diagnose(logcat: String): Diagnosis? {
        if (logcat.isBlank()) return null
        for (rule in RULES) {
            val match = rule.regex.find(logcat) ?: continue
            val snippet = match.value
            XLog.d(TAG, "diagnose: matched rule=${rule.id} snippet='${snippet.take(60)}'")
            return Diagnosis(
                ruleId = rule.id,
                userMessage = rule.userMessage,
                suggestion = rule.suggestion,
                matchedSnippet = snippet,
            )
        }
        return null
    }

    /** Run all matches — useful for surfacing multiple probable causes. */
    fun diagnoseAll(logcat: String): List<Diagnosis> {
        if (logcat.isBlank()) return emptyList()
        val matches = mutableListOf<Diagnosis>()
        for (rule in RULES) {
            val match = rule.regex.find(logcat) ?: continue
            matches.add(Diagnosis(
                ruleId = rule.id,
                userMessage = rule.userMessage,
                suggestion = rule.suggestion,
                matchedSnippet = match.value,
            ))
        }
        XLog.d(TAG, "diagnoseAll: matched ${matches.size} rules")
        return matches
    }
}
