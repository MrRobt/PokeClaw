// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 HMAC 双层认证 4 错误码 + 403001 TASK_DEVICE_MISMATCH

package io.agents.pokeclaw.cloud.auth

/**
 * HMAC 双层认证错误码类型化映射。
 *
 * 覆盖错误码：
 * - 401001 INVALID_SIGNATURE: 签名校验失败（密钥错或签名计算错）
 * - 401002 TIMESTAMP_EXPIRED: 时间戳超出 5 分钟窗口
 * - 401003 NONCE_DUPLICATE: nonce 重放
 * - 401004 DEVICE_MISMATCH: 请求 deviceId 与 token 绑定不一致
 * - 403001 TASK_DEVICE_MISMATCH: 任务领取后设备身份与任务派单不一致
 *
 * 仅 403001 触发 token + deviceId 失效 + 重新注册（[Code.shouldTriggerReregister]）；
 * 其余 HMAC 错误仅日志告警，不重试，由上层退避。
 */
object HmacAuthError {

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

    /**
     * 把后端数字错误码映射到 [Code]；未知码 → [Code.UNKNOWN]（默认仅日志，不重试）。
     */
    fun parse(code: Int): Code =
        Code.values().firstOrNull { it.numeric == code } ?: Code.UNKNOWN
}
