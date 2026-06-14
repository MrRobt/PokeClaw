// Copyright 2026 PokeClaw (agents.io). All rights reserved.

package io.agents.pokeclaw.cloud.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillVersionCacheTest {
    @Test fun `init has no version`() {
        val cache = SkillVersionCache()
        assertEquals(null, cache.current())
    }

    @Test fun `update to 5 sets current to 5`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertEquals(5, cache.current())
    }

    @Test fun `update with same value does not flag drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        val drifted = cache.update(5)
        assertTrue("should not drift", !drifted)
    }

    @Test fun `update with higher value flags drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertTrue(cache.update(7))
    }

    @Test fun `update with lower value flags drift`() {
        val cache = SkillVersionCache()
        cache.update(5)
        assertTrue(cache.update(3))
    }

    @Test fun `first update never flags drift`() {
        val cache = SkillVersionCache()
        assertTrue("first update should not drift", !cache.update(5))
    }
}