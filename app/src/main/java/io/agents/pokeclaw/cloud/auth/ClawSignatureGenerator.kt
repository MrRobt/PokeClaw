// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// HMAC-SHA256 签名生成器 — 对齐 device.openapi.yaml v1.1.0

package io.agents.pokeclaw.cloud.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Claw HMAC-SHA256 签名生成器。
 *
 * 签名算法（device.openapi.yaml）：
 * ```
 * signing_string = timestamp + "\n" + nonce + "\n" + path + "\n" + sha256_hex(body)
 * X-Claw-Signature = hex(HMAC-SHA256(device_token, signing_string))
 * ```
 *
 * 安全要求：
 * - 不在日志中输出完整 deviceToken 或签名密钥
 * - 时间戳为毫秒级 Unix 时间戳
 * - Nonce 为 UUID 格式，5分钟内不可重复
 */
object ClawSignatureGenerator {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val SHA256_ALGORITHM = "SHA-256"

    /**
     * 签名头数据。
     *
     * @param timestamp X-Claw-Timestamp: 客户端毫秒时间戳
     * @param nonce X-Claw-Nonce: 客户端生成 UUID
     * @param signature X-Claw-Signature: HMAC-SHA256 签名（十六进制）
     */
    data class SignatureHeaders(
        val timestamp: Long,
        val nonce: String,
        val signature: String
    )

    /**
     * 生成签名头。
     *
     * @param deviceToken 设备令牌（作为 HMAC 密钥）
     * @param path 请求路径（如 /api/claw-device/tasks/{taskUuid}/result）
     * @param bodyJson JSON 请求体字符串
     * @return 签名头数据
     */
    fun generateHeaders(
        deviceToken: String,
        path: String,
        bodyJson: String
    ): SignatureHeaders {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val signature = generateSignature(deviceToken, timestamp, nonce, path, bodyJson)
        return SignatureHeaders(timestamp, nonce, signature)
    }

    /**
     * 生成 HMAC-SHA256 签名。
     *
     * @param deviceToken 设备令牌（作为 HMAC 密钥）
     * @param timestamp 毫秒时间戳
     * @param nonce UUID 随机数
     * @param path 请求路径
     * @param bodyJson JSON 请求体
     * @return 十六进制签名字符串
     */
    fun generateSignature(
        deviceToken: String,
        timestamp: Long,
        nonce: String,
        path: String,
        bodyJson: String
    ): String {
        val bodySha256 = sha256Hex(bodyJson)
        val signingString = buildSigningString(timestamp, nonce, path, bodySha256)
        return hmacSha256Hex(deviceToken, signingString)
    }

    /**
     * 构造签名串。
     *
     * signing_string = timestamp + "\n" + nonce + "\n" + path + "\n" + sha256_hex(body)
     */
    private fun buildSigningString(
        timestamp: Long,
        nonce: String,
        path: String,
        bodySha256: String
    ): String {
        return "$timestamp\n$nonce\n$path\n$bodySha256"
    }

    /**
     * 计算 SHA256 十六进制哈希。
     */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    /**
     * 计算 HMAC-SHA256 十六进制签名。
     */
    private fun hmacSha256Hex(key: String, data: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        val signatureBytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(signatureBytes)
    }

    /**
     * 字节数组转十六进制字符串。
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexString = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val hex = Integer.toHexString(0xff and byte.toInt())
            if (hex.length == 1) {
                hexString.append('0')
            }
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
