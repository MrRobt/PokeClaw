// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 日志脱敏工具 — 确保敏感信息不输出到日志

package io.agents.pokeclaw.cloud.auth

/**
 * 日志脱敏工具对象。
 *
 * 安全要求（DYQ-89）：
 * - 不脱敏的日志可能泄露 deviceToken、HMAC 签名等敏感信息
 * - 所有 cloud 模块日志必须经过此工具脱敏处理
 */
object LogSanitizer {

    /**
     * 脱敏 HTTP 头中的敏感信息。
     *
     * 脱敏字段（DYQ-90 安全要求）：
     * - Authorization: Bearer <token> → Authorization: Bearer [REDACTED]
     * - X-Claw-Signature: <signature> → X-Claw-Signature: [REDACTED]
     * - X-Claw-Nonce: <nonce> → X-Claw-Nonce: [REDACTED]
     * - X-Claw-Timestamp: <timestamp> → X-Claw-Timestamp: [REDACTED]
     *
     * @param message 原始日志消息
     * @return 脱敏后的日志消息
     */
    fun sanitizeHttpHeaders(message: String): String {
        return message
            // 脱敏 Authorization: Bearer <token>
            .replace(Regex("(Authorization:\\s*Bearer\\s+)\\S+", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}[REDACTED]"
            }
            // 脱敏 X-Claw-Signature（大小写不敏感）
            .replace(Regex("(X-Claw-Signature:\\s*)\\S+", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}[REDACTED]"
            }
            // 脱敏 X-Claw-Nonce（大小写不敏感）
            .replace(Regex("(X-Claw-Nonce:\\s*)\\S+", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}[REDACTED]"
            }
            // 脱敏 X-Claw-Timestamp（大小写不敏感，DYQ-90 要求）
            .replace(Regex("(X-Claw-Timestamp:\\s*)\\S+", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}[REDACTED]"
            }
    }

    /**
     * 脱敏 deviceToken（显示前后部分，中间隐藏）。
     *
     * @param token 完整 deviceToken
     * @return 脱敏后的 token 字符串，如 "abc123...xyz789"
     */
    fun maskDeviceToken(token: String): String {
        if (token.length <= 12) {
            return "[REDACTED]"
        }
        val prefix = token.take(6)
        val suffix = token.takeLast(6)
        return "$prefix...$suffix"
    }

    /**
     * 脱敏签名密钥（完全隐藏）。
     *
     * @param signature 完整签名
     * @return 脱敏后的签名，如 "[REDACTED]"
     */
    fun maskSignature(signature: String): String {
        return "[REDACTED]"
    }

    /**
     * 脱敏通用字符串（只显示前N个字符）。
     *
     * @param value 原始字符串
     * @param visiblePrefix 前缀显示字符数（默认4）
     * @param visibleSuffix 后缀显示字符数（默认4）
     * @return 脱敏后的字符串
     */
    fun maskString(
        value: String,
        visiblePrefix: Int = 4,
        visibleSuffix: Int = 4
    ): String {
        if (value.length <= visiblePrefix + visibleSuffix) {
            return "[REDACTED]"
        }
        val prefix = value.take(visiblePrefix)
        val suffix = value.takeLast(visibleSuffix)
        return "$prefix...$suffix"
    }
}
