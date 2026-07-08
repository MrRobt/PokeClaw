package io.agents.pokeclaw.cloud.cloudphone

import io.agents.pokeclaw.utils.XLog
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * HTTP integration test for [CloudPhoneClient] against a MockWebServer standing in
 * for the dyq cloudphone admin API. Verifies the yudao CommonResult unwrapping,
 * auth/tenant headers, and the InstanceControlReqVO body shape.
 */
class CloudPhoneClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CloudPhoneClient

    @Before fun setUp() {
        XLog.setTestMode(true)
        server = MockWebServer()
        server.start()
        client = CloudPhoneClient(server.url("/").toString(), bearerToken = "tok", tenantId = "1")
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun listInstancesUnwrapsCommonResultPage() {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"list":[{"id":7,"instanceName":"CP-7","instanceStatus":1,"powerStatus":1,"packageName":"io.claw.app"}],"total":1},"msg":""}"""
            )
        )

        val list = client.listInstances()

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.startsWith("/admin-api/cloudphone/instance/page"))
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals("1", req.getHeader("tenant-id"))
        assertEquals(1, list.size)
        assertEquals(7L, list[0].id)
        assertEquals("CP-7", list[0].label())
    }

    @Test fun controlSendsActionTypeAndParams() {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"instanceId":7,"success":true,"taskId":"t1"},"msg":""}"""))

        val r = client.tap(7, 100, 200)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/admin-api/cloudphone/instance/control", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"actionType\":\"click\""))
        assertTrue(body.contains("\"instanceId\":7"))
        assertTrue(body.contains("100"))
        assertTrue(r?.success == true)
    }

    @Test fun nonZeroCodeReturnsNull() {
        server.enqueue(MockResponse().setBody("""{"code":500,"data":null,"msg":"err"}"""))
        assertNull(client.getConnection(7))
    }
}
