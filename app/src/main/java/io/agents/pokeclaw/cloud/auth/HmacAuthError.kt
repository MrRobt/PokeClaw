// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 HMAC 双层认证 4 错误码 + 403001 TASK_DEVICE_MISMATCH

package io.agents.pokeclaw.cloud.auth

/**
 * HMAC 双层认证错误码类型化映射，覆盖：
 * - 401001 INVALID_SIGNATURE: 签名校验失败（密钥错或签名计算错）
 * - 401002 TIMESTAMP_EXPIRED: 时间戳超出 5 分钟窗口
 * - 401003 NONCE_DUPLICATE: nonce 重放
 * - 401004 DEVICE_MISMATCH: 请求 deviceId 与 token 绑定不一致
 * - 403001 TASK_DEVICE_MISMATCH: 任务领取后设备身份与任务派单不一致
 *
 * 仅 403001 触发 token + deviceId 失效 + 重新注册；其余仅日志告警。
 */
class HmacAuthError private constructor(
    val code: Code,
    val message: String,
) {
    /** 委托到 code 枚举，使 HmacAuthError.INVALID_SIGNATURE.shouldTriggerReregister 可直接访问。 */
    val shouldTriggerReregister: Boolean get() = code.shouldTriggerReregister

    enum class Code(val numeric: Int) {
        INVALID_SIGNATURE(401001),
        TIMESTAMP_EXPIRED(401002),
        NONCE_DUPLICATE(401003),
        DEVICE_MISMATCH(401004),
        TASK_DEVICE_MISMATCH(403001),
        UNKNOWN(-1),
        ;

        /** 仅 TASK_DEVICE_MISMATCH 触发 token 失效 + 重新注册流程。 */
        val shouldTriggerReregister: Boolean
            get() = this == TASK_DEVICE_MISMATCH
    }

    companion object {
        /** 把数字错误码映射到类型化枚举；未知码 → UNKNOWN（默认仅日志，不重试）。 */
        fun parse(code: Int): Code = when (code) {
            401001 -> Code.INVALID_SIGNATURE
            401002 -> Code.TIMESTAMP_EXPIRED
            401003 -> Code.NONCE_DUPLICATE
            401004 -> Code.DEVICE_MISMATCH
            403001 -> Code.TASK_DEVICE_MISMATCH
            else -> Code.UNKNOWN
        }

        val INVALID_SIGNATURE = HmacAuthError(Code.INVALID_SIGNATURE, "INVALID_SIGNATURE")
    }
}