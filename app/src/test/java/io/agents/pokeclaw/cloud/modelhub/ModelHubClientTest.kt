package io.agents.pokeclaw.cloud.modelhub

import io.agents.pokeclaw.cloud.modelhub.model.ResolveRequestDto
import io.agents.pokeclaw.utils.XLog
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * HTTP integration test for [ModelHubClient] against a MockWebServer standing in
 * for the cloud model hub. Verifies request path/method/body and response parsing
 * for the full surface the App uses (resolve/download/upload/train/promote/rollback).
 */
class ModelHubClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ModelHubClient

    @Before fun setUp() {
        XLog.setTestMode(true)
        server = MockWebServer()
        server.start()
        client = ModelHubClient(server.url("/").toString())
    }

    @After fun tearDown() { server.shutdown() }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun resolveSendsSignalsAndParsesDescriptor() {
        val json = """
            {"software_key":"io.claw.app","package_name":"io.claw.app","model_id":"mdl_1",
             "version":2,"status":"active","source":"software_active","source_kind":"software",
             "classes":["interactable","text"],"default_confidence":0.3,
             "confidence_thresholds":{"interactable":0.5},"download_url":"http://h/dl",
             "checksum":"abc","size_bytes":10,"format":"stub","needs_data":false,
             "update_available":true,
             "candidate":{"model_id":"cand_3","version":3,"checksum":"c3","download_url":"http://h/c",
                          "classes":["x"],"shadow_only":true}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val d = client.resolve(ResolveRequestDto(packageName = "io.claw.app", softwareKey = "io.claw.app"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/models/resolve", req.path)
        assertTrue(req.body.readUtf8().contains("io.claw.app"))
        assertNotNull(d)
        assertEquals("software_active", d!!.source)
        assertEquals(2, d.version)
        assertEquals(0.5f, d.thresholdFor("interactable"))
        assertTrue(d.updateAvailable)
        assertEquals("cand_3", d.candidate?.modelId)
        assertTrue(d.candidate?.shadowOnly == true)
    }

    @Test fun downloadReturnsBytes() {
        val bytes = "MODEL-BYTES".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(bytes)))
        val f = File.createTempFile("m", ".bin")

        val out = client.download(server.url("/api/v1/models/mdl_1/download").toString(), f)

        assertNotNull(out)
        assertEquals("MODEL-BYTES", f.readText())
        assertEquals(sha256(bytes), sha256(out!!))
    }

    @Test fun uploadSampleHitsSoftwareKeyedPath() {
        server.enqueue(MockResponse().setBody("""{"sample_id":"s1","software_key":"io.claw.app","sample_count":5}"""))

        val ack = client.uploadSample("io.claw.app", mapOf("session_id" to "sess", "boxes" to emptyList<Any>()))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/datasets/io.claw.app/samples", req.path)
        assertTrue(req.body.readUtf8().contains("session_id"))
        assertEquals(5, ack?.sampleCount)
    }

    @Test fun batchTrainPromoteRollback() {
        server.enqueue(MockResponse().setBody("""{"software_key":"io.claw.app","ingested":6,"sample_count":6}"""))
        assertEquals(6, client.uploadSamples("io.claw.app", listOf(mapOf("a" to 1)))?.ingested)
        assertEquals("/api/v1/datasets/io.claw.app/samples/batch", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"job_id":"job1","software_key":"io.claw.app","status":"succeeded","candidate_model_id":"cand_9","candidate_version":9,"metrics":{"map50":0.42}}"""))
        val job = client.triggerTraining("io.claw.app", "note")
        assertEquals("/api/v1/training/io.claw.app/trigger", server.takeRequest().path)
        assertEquals("cand_9", job?.candidateModelId)
        assertEquals(0.42, (job?.metrics?.get("map50") as? Number)?.toDouble() ?: 0.0, 0.001)

        server.enqueue(MockResponse().setBody("""{"promoted":true,"gate_passed":true,"forced":false,"reasons":[]}"""))
        assertTrue(client.promote("cand_9", false)?.promoted == true)
        assertEquals("/api/v1/models/cand_9/promote", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"rolled_back":true,"to_version":1}"""))
        assertNotNull(client.rollback("io.claw.app"))
        assertEquals("/api/v1/software/io.claw.app/rollback", server.takeRequest().path)
    }

    @Test fun listSoftwareAndDatasets() {
        server.enqueue(MockResponse().setBody("""{"software":[{"software_key":"io.claw.app","category":"claw","model_count":1,"dataset_sample_count":6,"needs_data":false}]}"""))
        val sw = client.listSoftware()
        assertEquals("/api/v1/software", server.takeRequest().path)
        assertEquals("io.claw.app", sw?.software?.get(0)?.softwareKey)

        server.enqueue(MockResponse().setBody("""{"datasets":[{"software_key":"io.claw.app","sample_count":6,"num_classes":3}]}"""))
        val ds = client.listDatasets()
        assertEquals("/api/v1/datasets", server.takeRequest().path)
        assertEquals(6, ds?.datasets?.get(0)?.sampleCount)
    }

    @Test fun serverErrorReturnsNull() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertNull(client.resolve(ResolveRequestDto(packageName = "io.claw.app")))
    }
}
