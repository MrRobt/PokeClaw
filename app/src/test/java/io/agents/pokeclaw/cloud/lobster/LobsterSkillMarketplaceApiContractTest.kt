// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 LobsterSkillMarketplaceApi MockWebServer 契约测试

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterSkillMarketplaceApi
import io.agents.pokeclaw.cloud.lobster.model.BatchSkillStatusReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillSaveReqVO
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking

class LobsterSkillMarketplaceApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LobsterSkillMarketplaceApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LobsterSkillMarketplaceApi::class.java)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `listSkills 200 returns skill list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{
                    "code": 0,
                    "data": [
                        {
                            "skillId": "skill-1",
                            "skillName": "WeChat",
                            "description": "WeChat assistant",
                            "vendor": "PokeClaw",
                            "installStatus": "INSTALLED",
                            "version": "1.0.0",
                            "iconUrl": "https://example.com/icon.png",
                            "channelCode": "wechat"
                        }
                    ]
                }"""
            )
        )
        val resp = api.listSkills()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertNotNull(body)
        assertEquals(0, body?.code)
        @Suppress("UNCHECKED_CAST")
        val list = body?.data as? List<*>
        assertNotNull(list)
        assertEquals(1, list?.size)
    }

    @Test fun `listSkills 200 returns empty list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": []}""")
        )
        val resp = api.listSkills()
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        @Suppress("UNCHECKED_CAST")
        val list = body?.data as? List<*>
        assertNotNull(list)
        assertTrue(list?.isEmpty() == true)
    }

    @Test fun `installSkill 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": true}""")
        )
        val resp = api.installSkill(mapOf("skillId" to "skill-1"))
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(0, body?.code)
        assertEquals(true, body?.data)
    }

    @Test fun `installSkill 200 returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": false}""")
        )
        val resp = api.installSkill(mapOf("skillId" to "skill-2"))
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(false, body?.data)
    }

    @Test fun `saveSkill new returns string id`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": "new-skill-id-123"}""")
        )
        val req = ClawAppSkillSaveReqVO(
            skillName = "My Custom Skill",
            vendor = "PokeClaw",
            channelCode = "custom"
        )
        val resp = api.saveSkill(req)
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals("new-skill-id-123", body?.data)
    }

    @Test fun `saveSkill update returns boolean`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": true}""")
        )
        val req = ClawAppSkillSaveReqVO(
            id = "existing-skill-id",
            skillName = "Updated Skill",
            vendor = "PokeClaw",
            channelCode = "custom"
        )
        val resp = api.saveSkill(req)
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(true, body?.data)
    }

    @Test fun `removeSkill 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": true}""")
        )
        val resp = api.removeSkill("skill-1")
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(true, body?.data)
    }

    @Test fun `removeSkill 200 returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": false}""")
        )
        val resp = api.removeSkill("skill-nonexistent")
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(false, body?.data)
    }

    @Test fun `batchUpdateStatus 200 returns true`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"code": 0, "data": true}""")
        )
        val req = BatchSkillStatusReqVO(
            ids = listOf("skill-1", "skill-2", "skill-3"),
            enable = true
        )
        val resp = api.batchUpdateStatus(req)
        assertEquals(200, resp.code())
        val body = resp.body()
        assertEquals(true, body?.data)
    }
}