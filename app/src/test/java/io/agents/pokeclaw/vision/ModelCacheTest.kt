package io.agents.pokeclaw.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelCacheTest {

    private fun tempCache(): ModelCache =
        ModelCache(Files.createTempDirectory("yolo-cache").toFile())

    @Test
    fun storesAndReportsVersionAndChecksum() {
        val cache = tempCache()
        val bytes = "MODEL-V1-BYTES".toByteArray()
        val checksum = cache.sha256(bytes)

        assertEquals(0, cache.cachedVersion("io.claw.app"))
        assertFalse(cache.isCached("io.claw.app", 1))

        val f: File = cache.store("io.claw.app", 1, "mdl_1", checksum, bytes)
        assertTrue(f.isFile)
        assertTrue(cache.isCached("io.claw.app", 1))
        assertEquals(1, cache.cachedVersion("io.claw.app"))
        assertEquals(checksum, cache.cachedChecksum("io.claw.app"))
    }

    @Test
    fun updateReplacesPointerAndDrivesUpdatePolicy() {
        val cache = tempCache()
        val v1 = "v1".toByteArray()
        cache.store("app", 1, "m1", cache.sha256(v1), v1)
        val v2 = "v2-bigger".toByteArray()
        cache.store("app", 2, "m2", cache.sha256(v2), v2)

        assertEquals(2, cache.cachedVersion("app"))
        assertTrue(cache.isCached("app", 1))  // old version kept on disk
        assertTrue(cache.isCached("app", 2))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsChecksumMismatch() {
        val cache = tempCache()
        cache.store("app", 1, "m1", "deadbeef", "real-bytes".toByteArray())
    }
}
