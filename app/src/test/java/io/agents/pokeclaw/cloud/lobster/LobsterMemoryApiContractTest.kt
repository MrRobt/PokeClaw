// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterMemoryApi MockWebServer 契约测试

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterMemoryApi
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryCreateReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryRespVO
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking

class LobsterMemoryApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LobsterMemoryApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LobsterMemoryApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `listMemories 200 returns page with memories`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"list":[{"id":"mem-1","content":"hello","memoryType":"CHAT","createdAt":1,"updatedAt":1,"tags":[]}],"total":1}}"""
            )
        )
        val resp = api.listMemories(memoryType = "CHAT", pageNo = 1, pageSize = 20)
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
    }

    @Test fun `listMemories empty list returns empty list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}""")
        )
        val resp = api.listMemories()
        assertEquals(200, resp.code())
        val data = resp.body()?.data
        assertNotNull(data)
    }

    @Test fun `createMemory 200 returns created memory`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"id":"mem-new","content":"new memory","memoryType":"HABIT","createdAt":100,"updatedAt":100,"tags":["work"]}}"""
            )
        )
        val resp = api.createMemory(ClawMemoryCreateReqVO("new memory", "HABIT", listOf("work")))
        assertEquals(200, resp.code())
        // CommonResult.data is Any? (auto-generated non-parameterized), so Gson parses as Map
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("mem-new", data?.get("id"))
    }

    @Test fun `deleteMemory 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":true}""")
        )
        val resp = api.deleteMemory("mem-1")
        assertEquals(200, resp.code())
    }

    @Test fun `clearAllMemories 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":true}""")
        )
        val resp = api.clearAllMemories()
        assertEquals(200, resp.code())
    }

    // --- request shape (URL / body) ---

    @Test fun `listMemories GET path includes memoryType pageNo pageSize as query`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}""")
        )
        api.listMemories(memoryType = "HABIT", pageNo = 3, pageSize = 50)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        val path = recorded.path ?: ""
        assertTrue("path must start with memories route: $path", path.startsWith("/app-api/claw/app/lobster/my/memories"))
        assertTrue("path must include memoryType=HABIT: $path", path.contains("memoryType=HABIT"))
        assertTrue("path must include pageNo=3: $path", path.contains("pageNo=3"))
        assertTrue("path must include pageSize=50: $path", path.contains("pageSize=50"))
    }

    @Test fun `listMemories with default args omits all query params`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}""")
        )
        api.listMemories()
        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        // pageNo/pageSize have defaults → still serialized as 1 and 20.
        assertTrue("default pageNo=1 must be in path: $path", path.contains("pageNo=1"))
        assertTrue("default pageSize=20 must be in path: $path", path.contains("pageSize=20"))
        // memoryType is nullable, omitted when null.
        assertTrue("null memoryType must not be in path: $path", !path.contains("memoryType"))
    }

    @Test fun `createMemory POST body includes content memoryType tags`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"id":"mem-1","content":"c","memoryType":"HABIT","createdAt":1,"updatedAt":1,"tags":[]}}""")
        )
        api.createMemory(ClawMemoryCreateReqVO("c", "HABIT", listOf("work", "morning")))
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/app-api/claw/app/lobster/memory", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body must include content: $body", body.contains("\"content\":\"c\""))
        assertTrue("body must include memoryType: $body", body.contains("\"memoryType\":\"HABIT\""))
        assertTrue("body must include both tags: $body", body.contains("work") && body.contains("morning"))
    }

    @Test fun `createMemory with null tags omits the field`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"id":"mem-1","content":"c","memoryType":"HABIT","createdAt":1,"updatedAt":1,"tags":null}}""")
        )
        api.createMemory(ClawMemoryCreateReqVO("c", "HABIT", tags = null))
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("null tags must not appear in body: $body", !body.contains("\"tags\""))
    }

    @Test fun `deleteMemory DELETE path includes id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":true}"""))
        api.deleteMemory("mem-abc-9")
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/app-api/claw/app/lobster/memory/mem-abc-9", recorded.path)
    }

    @Test fun `clearAllMemories hits the all path with DELETE method`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":true}"""))
        api.clearAllMemories()
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/app-api/claw/app/lobster/memory/all", recorded.path)
    }

    // --- error responses ---

    @Test fun `listMemories 500 returns 500 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
        val resp = api.listMemories()
        assertEquals(500, resp.code())
        assertNull("body() for 500 must be null", resp.body())
        assertNotNull("errorBody() for 500 must carry the raw payload", resp.errorBody())
    }

    @Test fun `listMemories 401 unauthorized returns 401 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val resp = api.listMemories()
        assertEquals(401, resp.code())
        assertNull(resp.body())
    }

    @Test fun `deleteMemory 404 returns 404 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val resp = api.deleteMemory("mem-missing")
        assertEquals(404, resp.code())
        assertNull(resp.body())
    }

    @Test fun `createMemory business code 2002 returns code in body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":2002,"msg":"duplicate content","data":null}""")
        )
        val resp = api.createMemory(ClawMemoryCreateReqVO("dup", "HABIT"))
        assertEquals(200, resp.code())
        assertEquals(2002, resp.body()?.code)
        assertEquals("duplicate content", resp.body()?.msg)
    }

    // --- DTO round-trip ---

    @Test fun `ClawMemoryRespVO optional tags default to null`() {
        val resp = ClawMemoryRespVO(
            id = "m1",
            content = "c",
            memoryType = "CHAT",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals("m1", resp.id)
        assertNull(resp.tags)
    }

    @Test fun `ClawMemoryCreateReqVO optional tags default to null`() {
        val req = ClawMemoryCreateReqVO(content = "c", memoryType = "HABIT")
        assertEquals("c", req.content)
        assertEquals("HABIT", req.memoryType)
        assertNull(req.tags)
    }
}
