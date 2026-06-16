// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.
//
// HMAC-SHA256 签名工具 — 对应 dyq device.openapi.yaml v1.1.0 (2026-05-21 §4 P0-2)。
//
// 签名算法：
//   signing_string = X-Claw-Timestamp + "\n" + X-Claw-Nonce + "\n" + path + "\n" + sha256_hex(body)
//   X-Claw-Signature = hex(HMAC-SHA256(device_token, signing_string))
//
// 该工具为 pure-Kotlin（无 Android 依赖），便于在 JVM 单元测试中独立验证。
// 生产环境用 javax.crypto.Mac；单元测试环境如有 Robolectric 也走同一份代码。

package io.agents.pokeclaw.cloud.auth

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 签名工具。
 *
 * 关键约定（与 dyq 后端 claw-device v1.1.0 一致）：
 * - 路径必须以 "/" 开头（与 Spring Web 的 HttpServletRequest.getRequestURI() 一致）
 * - body 字节流为空时，sha256_hex 是 SHA-256("") 的固定值，而不是 0
 * - 输出全部使用小写 hex（64 字符）
 * - 换行符固定是 "\n"（LF），不跟随系统
 */
object HmacSigner {

    private const val HMAC_ALG = "HmacSHA256"
    private const val SHA256_ALG = "SHA-256"
    private const val EMPTY_BODY_SHA256_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private const val LINE_SEPARATOR = "\n"

    /**
     * 计算 SHA-256 摘要并返回小写 hex 字符串。
     * 字节为空时返回 SHA-256("") 的固定摘要，不返回 0 或抛错。
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(SHA256_ALG)
        val hash = digest.digest(bytes)
        return toHex(hash)
    }

    /**
     * 计算 HMAC-SHA256 摘要并返回小写 hex 字符串。
     */
    fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        require(key.isNotEmpty()) { "HMAC 密钥不能为空" }
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(key, HMAC_ALG))
        val signature = mac.doFinal(data)
        return toHex(signature)
    }

    /**
     * 计算 submitTaskResult 调用所需的完整签名。
     *
     * @param deviceToken 当前设备令牌（不脱敏，直接用于 HMAC 密钥）
     * @param timestampMillis X-Claw-Timestamp 头（毫秒）
     * @param nonce X-Claw-Nonce 头（UUID 字符串即可）
     * @param path 请求路径，必须以 "/" 开头（例："/api/claw-device/tasks/abc-123/result"）
     * @param bodyBytes 请求体字节流；空 body 时传 ByteArray(0)
     */
    fun signSubmitResult(
        deviceToken: String,
        timestampMillis: Long,
        nonce: String,
        path: String,
        bodyBytes: ByteArray,
    ): String {
        require(deviceToken.isNotBlank()) { "deviceToken 不能为空" }
        require(nonce.isNotBlank()) { "nonce 不能为空" }
        require(path.startsWith("/")) { "path 必须以 \"/\" 开头：$path" }
        val bodyHash = if (bodyBytes.isEmpty()) EMPTY_BODY_SHA256_HEX else sha256Hex(bodyBytes)
        val signingString = buildString {
            append(timestampMillis)
            append(LINE_SEPARATOR)
            append(nonce)
            append(LINE_SEPARATOR)
            append(path)
            append(LINE_SEPARATOR)
            append(bodyHash)
        }
        return hmacSha256Hex(deviceToken.toByteArray(Charsets.UTF_8), signingString.toByteArray(Charsets.UTF_8))
    }

    /**
     * 5 分钟时间窗校验（用于本地重试前快速失败）。
     * 服务端也是 5 分钟，但这里只用于客户端自检，减少无意义的网络往返。
     */
    fun isWithinWindow(timestampMillis: Long, nowMillis: Long, windowMillis: Long = DEFAULT_WINDOW_MS): Boolean {
        val skew = if (nowMillis >= timestampMillis) nowMillis - timestampMillis else timestampMillis - nowMillis
        return skew <= windowMillis
    }

    const val DEFAULT_WINDOW_MS: Long = 5L * 60L * 1000L

    private fun toHex(bytes: ByteArray): String {
        val hex = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            hex[i * 2] = HEX_CHARS[b ushr 4]
            hex[i * 2 + 1] = HEX_CHARS[b and 0x0F]
        }
        return String(hex)
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
