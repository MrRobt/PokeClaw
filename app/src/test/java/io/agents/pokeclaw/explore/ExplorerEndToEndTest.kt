package io.agents.pokeclaw.explore

import io.agents.pokeclaw.cloud.modelhub.ModelHubClient
import io.agents.pokeclaw.collect.SampleUploader
import io.agents.pokeclaw.device.DeviceActuator
import io.agents.pokeclaw.device.DeviceObservation
import io.agents.pokeclaw.device.DeviceObserver
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.vision.ModelCache
import io.agents.pokeclaw.vision.YoloModelClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full App-side loop against a MockWebServer hub: a fake cloud phone (observer +
 * actuator) is auto-explored; the resolved model routes to generic fallback; each
 * NEW page is collected and uploaded to the hub by software_key. Verifies dedup,
 * collection, and one upload per new page — end to end without a device.
 */
class ExplorerEndToEndTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        XLog.setTestMode(true)
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    /** Fake cloud phone: Home has two buttons → P1 / P2 (leaves); back pops. */
    private class FakeDevice : DeviceObserver, DeviceActuator {
        private val stack = ArrayDeque<String>().apply { addLast("home") }
        private fun cur() = stack.last()
        private val xml = mapOf(
            "home" to """[Button] text="A" id="btnA" pkg="io.claw.app" [clickable] bounds=[10,10][210,90]
[Button] text="B" id="btnB" pkg="io.claw.app" [clickable] bounds=[10,300][210,380]""",
            "p1" to """[TextView] text="one" pkg="io.claw.app" bounds=[10,10][410,90]""",
            "p2" to """[TextView] text="two" pkg="io.claw.app" bounds=[10,10][410,90]""",
        )

        override fun observe(captureScreenshot: Boolean) =
            DeviceObservation("io.claw.app", cur(), xml[cur()], "QUJD", 1080, 2400)

        override fun perform(action: ExploreAction): String = when {
            action.type == "back" -> { if (stack.size > 1) stack.removeLast(); "ok" }
            action.type == "tap" && cur() == "home" && action.nodeSignature?.contains("btnA") == true -> { stack.addLast("p1"); "changed" }
            action.type == "tap" && cur() == "home" && action.nodeSignature?.contains("btnB") == true -> { stack.addLast("p2"); "changed" }
            else -> "no_change"
        }

        override fun tap(x: Int, y: Int) = "ok"
        override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = "ok"
        override fun input(text: String) = "ok"
        override fun back() = "ok"
        override fun home() = "ok"
        override fun launch(pkg: String) = "ok"
    }

    @Test fun exploreCollectsAndUploadsPerNewPage() {
        val sampleReqs = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path ?: ""
                return when {
                    p.endsWith("/models/resolve") -> MockResponse().setResponseCode(200).setBody(
                        """{"software_key":"io.claw.app","package_name":"io.claw.app","model_id":"generic",
                            "version":1,"status":"active","source":"generic_fallback","source_kind":"generic",
                            "classes":["interactable","text"],"default_confidence":0.25,"confidence_thresholds":{},
                            "download_url":null,"checksum":null,"size_bytes":0,"format":"stub",
                            "needs_data":true,"update_available":false,"candidate":null}""".trimIndent()
                    )
                    p.contains("/datasets/") && p.endsWith("/samples") -> {
                        val n = sampleReqs.incrementAndGet()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"sample_id":"s$n","software_key":"io.claw.app","sample_count":$n}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val hub = ModelHubClient(server.url("/").toString())
        val yolo = YoloModelClient(hub, ModelCache(Files.createTempDirectory("yc").toFile()))
        val device = FakeDevice()
        val explorer = SoftwareExplorer(device, device, yolo, SampleUploader(hub), settleMs = 0)

        val report = explorer.explore(ExplorerConfig(maxSteps = 12, targetPackage = "io.claw.app"))

        assertEquals("home, p1, p2 deduped", 3, report.uniqueStates)
        assertEquals(3, report.collectedSamples)
        assertEquals("one dataset upload per new page", 3, sampleReqs.get())
        assertTrue(report.actionsExecuted >= 3)
    }
}
