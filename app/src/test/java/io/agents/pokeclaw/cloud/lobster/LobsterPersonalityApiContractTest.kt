// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterPersonalityApi MockWebServer 契约测试

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterPersonalityApi
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodUpdateReqVO
import io.agents.pokeclaw.cloud.lobster.model.PersonalityDimension
import io.agents.pokeclaw.cloud.lobster.model.PersonalityTypes
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

class LobsterPersonalityApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LobsterPersonalityApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LobsterPersonalityApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `getPersonality 200 returns mood resp`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"mood":"CALM","intensity":6,"traits":{"warmth":7,"formality":3},"updatedAt":100}}"""
            )
        )
        val resp = api.getPersonality()
        assertEquals(200, resp.code())
        // CommonResult.data is Any? (auto-generated non-parameterized), so Gson parses as Map
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertEquals("CALM", data?.get("mood"))
        assertEquals(6.0, data?.get("intensity"))
    }

    @Test fun `updatePersonality 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":true}""")
        )
        val resp = api.updatePersonality(ClawMoodUpdateReqVO("HAPPY", 8, mapOf("warmth" to 9)))
        assertEquals(200, resp.code())
    }

    @Test fun `updatePersonality 400 returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":400,"msg":"validation error"}""")
                .setResponseCode(400)
        )
        val resp = api.updatePersonality(ClawMoodUpdateReqVO("UNKNOWN", 99, null))
        assertEquals(400, resp.code())
    }

    @Test fun `getPersonalityTypes 200 returns dimensions`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"dimensions":[{"id":"warmth","label":"Warmth","rangeMin":1,"rangeMax":10},{"id":"formality","label":"Formality","rangeMin":1,"rangeMax":10},{"id":"humor","label":"Humor","rangeMin":1,"rangeMax":10},{"id":"empathy","label":"Empathy","rangeMin":1,"rangeMax":10},{"id":"verbosity","label":"Verbosity","rangeMin":1,"rangeMax":10}]}}"""
            )
        )
        val resp = api.getPersonalityTypes()
        assertEquals(200, resp.code())
        // CommonResult.data is Any? (auto-generated non-parameterized), so Gson parses as Map
        @Suppress("UNCHECKED_CAST")
        val data = resp.body()?.data as? Map<String, Any?>
        assertNotNull(data)
        @Suppress("UNCHECKED_CAST")
        val dimensions = data?.get("dimensions") as? List<Map<String, Any?>>
        assertNotNull(dimensions)
        assertEquals(5, dimensions?.size)
        assertEquals("warmth", dimensions?.get(0)?.get("id"))
    }

    // --- error responses ---

    @Test fun `getPersonality 500 returns 500 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal server error"))
        val resp = api.getPersonality()
        assertEquals(500, resp.code())
        assertNull("body() for 500 must be null", resp.body())
        assertNotNull("errorBody() for 500 must carry raw payload", resp.errorBody())
    }

    @Test fun `getPersonality 401 returns 401 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val resp = api.getPersonality()
        assertEquals(401, resp.code())
        assertNull(resp.body())
    }

    @Test fun `getPersonality 404 returns 404 with null body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val resp = api.getPersonality()
        assertEquals(404, resp.code())
        assertNull(resp.body())
    }

    @Test fun `updatePersonality business code 2003 is preserved`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":2003,"msg":"intensity out of range","data":null}""")
        )
        val resp = api.updatePersonality(ClawMoodUpdateReqVO("HAPPY", 99, null))
        assertEquals(200, resp.code())
        assertEquals(2003, resp.body()?.code)
        assertEquals("intensity out of range", resp.body()?.msg)
    }

    @Test fun `getPersonalityTypes 502 returns 502`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val resp = api.getPersonalityTypes()
        assertEquals(502, resp.code())
        assertNull(resp.body())
    }

    // --- request shape ---

    @Test fun `getPersonality hits the personality route with GET method`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"mood":"CALM","intensity":5,"traits":null,"updatedAt":1}}""")
        )
        api.getPersonality()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/my/personality", recorded.path)
    }

    @Test fun `updatePersonality PUT method and body serialization`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":true}"""))
        api.updatePersonality(
            ClawMoodUpdateReqVO(
                mood = "EXCITED",
                intensity = 9,
                traits = mapOf("warmth" to 8, "humor" to 7),
            )
        )
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/app-api/claw/app/lobster/personality", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body must include mood: $body", body.contains("\"mood\":\"EXCITED\""))
        assertTrue("body must include intensity: $body", body.contains("\"intensity\":9"))
        assertTrue("body must include warmth: $body", body.contains("warmth"))
        assertTrue("body must include humor: $body", body.contains("humor"))
    }

    @Test fun `updatePersonality with null traits omits the field`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":true}"""))
        api.updatePersonality(ClawMoodUpdateReqVO(mood = "CALM", intensity = 5, traits = null))
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("null traits must not appear in body: $body", !body.contains("\"traits\""))
    }

    @Test fun `getPersonalityTypes hits the types route with GET method`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"dimensions":[]}}""")
        )
        api.getPersonalityTypes()
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/app-api/claw/app/lobster/personality/types", recorded.path)
    }

    // --- DTO round-trip ---

    @Test fun `ClawMoodRespVO optional traits default to null`() {
        val resp = ClawMoodRespVO(mood = "CALM", intensity = 5, updatedAt = 1L)
        assertEquals("CALM", resp.mood)
        assertEquals(5, resp.intensity)
        assertEquals(1L, resp.updatedAt)
        assertNull(resp.traits)
    }

    @Test fun `ClawMoodUpdateReqVO optional traits default to null`() {
        val req = ClawMoodUpdateReqVO(mood = "HAPPY", intensity = 7)
        assertEquals("HAPPY", req.mood)
        assertEquals(7, req.intensity)
        assertNull(req.traits)
    }

    @Test fun `PersonalityDimension direct construction preserves all fields`() {
        val dim = PersonalityDimension(id = "warmth", label = "Warmth", rangeMin = 1, rangeMax = 10)
        assertEquals("warmth", dim.id)
        assertEquals("Warmth", dim.label)
        assertEquals(1, dim.rangeMin)
        assertEquals(10, dim.rangeMax)
    }

    @Test fun `PersonalityTypes carries dimensions list as-is`() {
        val dims = listOf(
            PersonalityDimension("a", "A", 0, 1),
            PersonalityDimension("b", "B", 0, 5),
        )
        val types = PersonalityTypes(dimensions = dims)
        assertEquals(2, types.dimensions.size)
        assertEquals("a", types.dimensions[0].id)
    }
}
