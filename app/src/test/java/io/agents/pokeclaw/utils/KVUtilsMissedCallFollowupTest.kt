// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Missed-call follow-up KV defaults must stay explicit opt-in because the feature can send SMS.

package io.agents.pokeclaw.utils

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KVUtilsMissedCallFollowupTest {

    @Before
    fun setUp() {
        KVUtils.resetTestBacking()
    }

    @After
    fun tearDown() {
        KVUtils.resetTestBacking()
    }

    @Test
    fun `missed call follow-up is disabled by default`() {
        assertFalse(KVUtils.isMissedCallFollowupEnabled())
    }

    @Test
    fun `missed call follow-up can be explicitly enabled and disabled`() {
        KVUtils.setMissedCallFollowupEnabled(true)
        assertTrue(KVUtils.isMissedCallFollowupEnabled())

        KVUtils.setMissedCallFollowupEnabled(false)
        assertFalse(KVUtils.isMissedCallFollowupEnabled())
    }
}
