package io.agents.pokeclaw.vision

import io.agents.pokeclaw.cloud.modelhub.ModelHubClient
import io.agents.pokeclaw.utils.XLog
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Full vision-client integration against a MockWebServer hub: resolve → download →
 * checksum-verify → cache → version-update, plus the mid-task defer and checksum
 * rejection paths.
 */
class YoloModelClientIntegrationTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        XLog.setTestMode(true)
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun resolveJson(version: Int, checksum: String, dlUrl: String?) = """
        {"software_key":"io.claw.app","package_name":"io.claw.app","model_id":"mdl",
         "version":$version,"status":"active","source":"software_active","source_kind":"software",
         "classes":["interactable"],"default_confidence":0.3,"confidence_thresholds":{},
         "download_url":${dlUrl?.let { "\"$it\"" } ?: "null"},"checksum":"$checksum","size_bytes":10,
         "format":"stub","needs_data":false,"update_available":true,"candidate":null}
    """.trimIndent()

    private fun newClient() = YoloModelClient(
        ModelHubClient(server.url("/").toString()),
        ModelCache(Files.createTempDirectory("yc").toFile()),
    )

    @Test fun resolveDownloadsCachesAndVersionUpdates() {
        val bytes = "WEIGHTS_V2".toByteArray()
        val cs = sha256(bytes)
        val dl = server.url("/api/v1/models/mdl/download").toString()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path ?: ""
                return when {
                    p.contains("/download") -> MockResponse().setResponseCode(200).setBody(String(bytes))
                    p.endsWith("/models/resolve") -> MockResponse().setResponseCode(200).setBody(resolveJson(2, cs, dl))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val cache = ModelCache(Files.createTempDirectory("yc").toFile())
        val yolo = YoloModelClient(ModelHubClient(server.url("/").toString()), cache)

        val r1 = yolo.ensureModel(SoftwareIdentity(packageName = "io.claw.app"))
        assertTrue("first fetch downloads", r1.updated)
        assertEquals(2, r1.descriptor.version)
        assertEquals(2, cache.cachedVersion("io.claw.app"))
        assertTrue(cache.isCached("io.claw.app", 2))
        assertNotNull(r1.modelFile)

        val r2 = yolo.ensureModel(SoftwareIdentity(packageName = "io.claw.app"))
        assertFalse("same version -> no re-download", r2.updated)
    }

    @Test fun checksumMismatchNotCached() {
        val bytes = "REAL".toByteArray()
        val dl = server.url("/api/v1/models/mdl/download").toString()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path ?: ""
                return when {
                    p.contains("/download") -> MockResponse().setResponseCode(200).setBody(String(bytes))
                    p.endsWith("/models/resolve") -> MockResponse().setResponseCode(200).setBody(resolveJson(2, "deadbeef", dl))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val cache = ModelCache(Files.createTempDirectory("yc").toFile())
        val yolo = YoloModelClient(ModelHubClient(server.url("/").toString()), cache)

        val r = yolo.ensureModel(SoftwareIdentity(packageName = "io.claw.app"))
        assertFalse(r.updated)
        assertEquals(0, cache.cachedVersion("io.claw.app"))
    }

    @Test fun midTaskDefersFirstFetch() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(resolveJson(2, "abc", null)))
        val cache = ModelCache(Files.createTempDirectory("yc").toFile())
        val yolo = YoloModelClient(ModelHubClient(server.url("/").toString()), cache)

        val r = yolo.ensureModel(SoftwareIdentity(packageName = "io.claw.app"), taskRunning = true)
        assertFalse(r.updated)
        assertEquals(0, cache.cachedVersion("io.claw.app"))
    }
}
