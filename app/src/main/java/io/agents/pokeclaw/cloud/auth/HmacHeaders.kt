// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.
//
// HMAC 签名 HTTP 头数据类 — 对应 dyq device.openapi.yaml v1.1.0 §4。

package io.agents.pokeclaw.cloud.auth

/**
 * 提交任务结果所需的 3 个 HMAC 头。
 *
 * @property timestampMillis X-Claw-Timestamp 头（毫秒）
 * @property nonce X-Claw-Nonce 头（任意不重复字符串；UUID 即可）
 * @property signature X-Claw-Signature 头（hex 小写，64 字符）
 */
data class HmacHeaders(
    val timestampMillis: Long,
    val nonce: String,
    val signature: String,
) {
    init {
        require(nonce.isNotBlank()) { "nonce 不能为空" }
        require(signature.isNotBlank()) { "signature 不能为空" }
        require(signature.length == EXPECTED_HEX_LENGTH) {
            "signature 必须是 $EXPECTED_HEX_LENGTH 字符的 hex 串：$signature"
        }
    }

    companion object {
        const val EXPECTED_HEX_LENGTH = 64

        /**
         * 给定 deviceToken + 请求路径 + body 字节流，生成完整签名头。
         * 默认 timestamp = 当前时间，nonce = UUID.randomUUID().toString()。
         */
        fun build(
            deviceToken: String,
            path: String,
            bodyBytes: ByteArray,
            nowMillis: Long = System.currentTimeMillis(),
            nonce: String = java.util.UUID.randomUUID().toString(),
        ): HmacHeaders {
            val signature = HmacSigner.signSubmitResult(
                deviceToken = deviceToken,
                timestampMillis = nowMillis,
                nonce = nonce,
                path = path,
                bodyBytes = bodyBytes,
            )
            return HmacHeaders(nowMillis, nonce, signature)
        }
    }
}

/**
 * HMAC 鉴权失败专用异常，便于上层按 code 分支处理。
 *
 * 错误码对应 dyq 后端 v1.1.0：
 * - 401001 INVALID_SIGNATURE
 * - 401002 TIMESTAMP_EXPIRED
 * - 401003 NONCE_DUPLICATE
 * - 401004 DEVICE_MISMATCH
 */
class HmacAuthException(
    val errorCode: Int,
    val reason: String,
) : RuntimeException("HMAC 鉴权失败：code=$errorCode reason=$reason") {

    val isDeviceMismatch: Boolean get() = errorCode == CODE_DEVICE_MISMATCH
    val isTimestampExpired: Boolean get() = errorCode == CODE_TIMESTAMP_EXPIRED
    val isNonceDuplicate: Boolean get() = errorCode == CODE_NONCE_DUPLICATE
    val isInvalidSignature: Boolean get() = errorCode == CODE_INVALID_SIGNATURE
    val isTaskDeviceMismatch: Boolean get() = errorCode == CODE_TASK_DEVICE_MISMATCH

    companion object {
        const val CODE_INVALID_SIGNATURE = 401001
        const val CODE_TIMESTAMP_EXPIRED = 401002
        const val CODE_NONCE_DUPLICATE = 401003
        const val CODE_DEVICE_MISMATCH = 401004
        const val CODE_TASK_DEVICE_MISMATCH = 403001

        fun forCode(code: Int): HmacAuthException? = when (code) {
            CODE_INVALID_SIGNATURE -> HmacAuthException(code, "INVALID_SIGNATURE")
            CODE_TIMESTAMP_EXPIRED -> HmacAuthException(code, "TIMESTAMP_EXPIRED")
            CODE_NONCE_DUPLICATE -> HmacAuthException(code, "NONCE_DUPLICATE")
            CODE_DEVICE_MISMATCH -> HmacAuthException(code, "DEVICE_MISMATCH")
            CODE_TASK_DEVICE_MISMATCH -> HmacAuthException(code, "TASK_DEVICE_MISMATCH")
            else -> null
        }
    }
}
