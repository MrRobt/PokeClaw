// Copyright 2026 PokeClaw (agents.io). All rights reserved.

package io.agents.pokeclaw.cloud.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HmacAuthErrorTest {
    @Test fun `parse code 401001 returns INVALID_SIGNATURE`() {
        assertEquals(HmacAuthError.Code.INVALID_SIGNATURE, HmacAuthError.parse(401001))
    }
    @Test fun `parse code 401002 returns TIMESTAMP_EXPIRED`() {
        assertEquals(HmacAuthError.Code.TIMESTAMP_EXPIRED, HmacAuthError.parse(401002))
    }
    @Test fun `parse code 401003 returns NONCE_DUPLICATE`() {
        assertEquals(HmacAuthError.Code.NONCE_DUPLICATE, HmacAuthError.parse(401003))
    }
    @Test fun `parse code 401004 returns DEVICE_MISMATCH`() {
        assertEquals(HmacAuthError.Code.DEVICE_MISMATCH, HmacAuthError.parse(401004))
    }
    @Test fun `parse code 403001 returns TASK_DEVICE_MISMATCH`() {
        assertEquals(HmacAuthError.Code.TASK_DEVICE_MISMATCH, HmacAuthError.parse(403001))
    }
    @Test fun `parse unknown code returns UNKNOWN`() {
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(999))
    }
    @Test fun `INVALID_SIGNATURE shouldTriggerReregister is false`() {
        assertFalse(HmacAuthError.Code.INVALID_SIGNATURE.shouldTriggerReregister)
    }
    @Test fun `TASK_DEVICE_MISMATCH shouldTriggerReregister is true`() {
        assertTrue(HmacAuthError.Code.TASK_DEVICE_MISMATCH.shouldTriggerReregister)
    }
    @Test fun `Code numeric values match HMAC spec`() {
        assertEquals(401001, HmacAuthError.Code.INVALID_SIGNATURE.numeric)
        assertEquals(401002, HmacAuthError.Code.TIMESTAMP_EXPIRED.numeric)
        assertEquals(401003, HmacAuthError.Code.NONCE_DUPLICATE.numeric)
        assertEquals(401004, HmacAuthError.Code.DEVICE_MISMATCH.numeric)
        assertEquals(403001, HmacAuthError.Code.TASK_DEVICE_MISMATCH.numeric)
        assertEquals(-1, HmacAuthError.Code.UNKNOWN.numeric)
    }

    // --- 扩展覆盖 ---

    @Test fun `TIMESTAMP_EXPIRED shouldTriggerReregister is false`() {
        assertFalse(HmacAuthError.Code.TIMESTAMP_EXPIRED.shouldTriggerReregister)
    }

    @Test fun `NONCE_DUPLICATE shouldTriggerReregister is false`() {
        assertFalse(HmacAuthError.Code.NONCE_DUPLICATE.shouldTriggerReregister)
    }

    @Test fun `DEVICE_MISMATCH shouldTriggerReregister is false`() {
        // 注意：DEVICE_MISMATCH (401004) 在 HmacAuthError.Code 模型中 shouldTriggerReregister=false，
        // 但 RetrofitDeviceCloudClient 中 401004 会额外调用 tokenStore.invalidate() + onAuthFailed，
        // 二者语义不同 — 前者是"完全重注册"，后者是"清掉当前 token 后下一轮重试"。
        assertFalse(HmacAuthError.Code.DEVICE_MISMATCH.shouldTriggerReregister)
    }

    @Test fun `UNKNOWN shouldTriggerReregister is false`() {
        assertFalse(HmacAuthError.Code.UNKNOWN.shouldTriggerReregister)
    }

    @Test fun `仅 TASK_DEVICE_MISMATCH shouldTriggerReregister 为 true`() {
        // 真值表校验 — 保证"只此一个"的契约
        val trueOnes = HmacAuthError.Code.values().filter { it.shouldTriggerReregister }
        assertEquals(1, trueOnes.size)
        assertEquals(HmacAuthError.Code.TASK_DEVICE_MISMATCH, trueOnes[0])
    }

    @Test fun `Code 枚举数量为 6`() {
        assertEquals(6, HmacAuthError.Code.values().size)
    }

    @Test fun `Code 枚举 name 稳定`() {
        assertEquals("INVALID_SIGNATURE", HmacAuthError.Code.INVALID_SIGNATURE.name)
        assertEquals("TIMESTAMP_EXPIRED", HmacAuthError.Code.TIMESTAMP_EXPIRED.name)
        assertEquals("NONCE_DUPLICATE", HmacAuthError.Code.NONCE_DUPLICATE.name)
        assertEquals("DEVICE_MISMATCH", HmacAuthError.Code.DEVICE_MISMATCH.name)
        assertEquals("TASK_DEVICE_MISMATCH", HmacAuthError.Code.TASK_DEVICE_MISMATCH.name)
        assertEquals("UNKNOWN", HmacAuthError.Code.UNKNOWN.name)
    }

    @Test fun `parse 与 numeric 互逆 已知码 round-trip 一致`() {
        // 已知码：parse(c.numeric) == c
        for (code in HmacAuthError.Code.values()) {
            if (code == HmacAuthError.Code.UNKNOWN) continue
            assertEquals("parse(c.numeric) 应等于 c", code, HmacAuthError.parse(code.numeric))
        }
    }

    @Test fun `parse 边界码 0 Int_MIN_VALUE Int_MAX_VALUE -100 均返回 UNKNOWN`() {
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(0))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(Int.MIN_VALUE))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(Int.MAX_VALUE))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(-100))
    }

    @Test fun `parse 临近已知码的边界 401000 401005 403000 403002 均返回 UNKNOWN`() {
        // 验证 numeric 不匹配邻居也不会被错误归类
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(401000))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(401005))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(403000))
        assertEquals(HmacAuthError.Code.UNKNOWN, HmacAuthError.parse(403002))
    }

    @Test fun `parse 已知 numeric 无重复 且全部大于 0（除 UNKNOWN）`() {
        val numerics = HmacAuthError.Code.values().filter { it != HmacAuthError.Code.UNKNOWN }.map { it.numeric }
        assertEquals("numeric 应唯一", numerics.size, numerics.toSet().size)
        assertTrue("已知 numeric 应均 > 0", numerics.all { it > 0 })
        assertTrue("已知 numeric 应均 >= 401001", numerics.all { it >= 401001 })
    }
}
