// Copyright 2026 PokeClaw (agents.io). All rights reserved.

package io.agents.pokeclaw.cloud.auth

import org.junit.Assert.assertEquals
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
        assertEquals(false, HmacAuthError.INVALID_SIGNATURE.shouldTriggerReregister)
    }
    @Test fun `TASK_DEVICE_MISMATCH shouldTriggerReregister is true`() {
        assertEquals(true, HmacAuthError.Code.TASK_DEVICE_MISMATCH.shouldTriggerReregister)
    }
}