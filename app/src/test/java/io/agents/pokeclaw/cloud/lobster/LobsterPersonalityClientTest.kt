// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterPersonalityClient 7 用例

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterPersonalityApi
import io.agents.pokeclaw.cloud.lobster.client.LobsterPersonalityClient
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodUpdateReqVO
import io.agents.pokeclaw.cloud.lobster.model.PersonalityDimension
import io.agents.pokeclaw.cloud.lobster.model.PersonalityTypes
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * US-D-040 LobsterPersonalityClient 7 用例
 */
class LobsterPersonalityClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Fake API ─────────────────────────────────────────────────────────────────

    private class FakeLobsterPersonalityApi : LobsterPersonalityApi {
        data class Enqueued(val block: () -> Response<CommonResult>)

        private val getQueue = ArrayDeque<Enqueued>()
        private val putQueue = ArrayDeque<Enqueued>()
        private val getTypesQueue = ArrayDeque<Enqueued>()

        fun enqueueGet(block: () -> Response<CommonResult>) { getQueue.add(Enqueued(block)) }
        fun enqueuePut(block: () -> Response<CommonResult>) { putQueue.add(Enqueued(block)) }
        fun enqueueGetTypes(block: () -> Response<CommonResult>) { getTypesQueue.add(Enqueued(block)) }

        override suspend fun getPersonality(): Response<CommonResult> {
            val e = getQueue.removeFirstOrNull() ?: error("no enqueued get response")
            return e.block()
        }
        override suspend fun updatePersonality(req: ClawMoodUpdateReqVO): Response<CommonResult> {
            val e = putQueue.removeFirstOrNull() ?: error("no enqueued put response")
            return e.block()
        }
        override suspend fun getPersonalityTypes(): Response<CommonResult> {
            val e = getTypesQueue.removeFirstOrNull() ?: error("no enqueued getTypes response")
            return e.block()
        }
    }

    // ── Test 1: get ok returns ClawMoodRespVO ─────────────────────────────────

    @Test
    fun `get ok returns ClawMoodRespVO`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = ClawMoodRespVO(
                            mood = "CALM",
                            intensity = 6,
                            traits = mapOf("warmth" to 7, "formality" to 3),
                            updatedAt = 100,
                        ),
                    ),
                )
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.get()
        assertNotNull(result)
        assertEquals("CALM", result!!.mood)
        assertEquals(6, result.intensity)
        assertEquals(7, result.traits?.get("warmth"))
    }

    // ── Test 2: get fail returns null ───────────────────────────────────────

    @Test
    fun `get fail returns null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet {
                Response.success(CommonResult(code = 999, msg = "server error"))
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.get()
        assertNull(result)
    }

    // ── Test 3: get types returns 5+ dimensions ───────────────────────────────

    @Test
    fun `get types returns 5+ dimensions`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGetTypes {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PersonalityTypes(
                            dimensions = listOf(
                                PersonalityDimension("warmth", "Warmth", 1, 10),
                                PersonalityDimension("formality", "Formality", 1, 10),
                                PersonalityDimension("humor", "Humor", 1, 10),
                                PersonalityDimension("empathy", "Empathy", 1, 10),
                                PersonalityDimension("verbosity", "Verbosity", 1, 10),
                            ),
                        ),
                    ),
                )
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.getTypes()
        assertNotNull(result)
        assertTrue(result!!.dimensions.size >= 5)
        assertEquals("warmth", result.dimensions[0].id)
    }

    // ── Test 4: put ok returns true ─────────────────────────────────────────

    @Test
    fun `put ok returns true`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.update(ClawMoodUpdateReqVO("HAPPY", 8, mapOf("warmth" to 9)))
        assertTrue(result)
    }

    // ── Test 5: put validation fail (400) returns false ───────────────────────

    @Test
    fun `put validation fail 400 returns false`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut {
                Response.error(400, ResponseBody.create(null, "validation error"))
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.update(ClawMoodUpdateReqVO("UNKNOWN", 99, null))
        assertFalse(result)
    }

    // ── Test 6: network exception throws ──────────────────────────────────────

    @Test
    fun `network exception throws`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet { throw IOException("simulated network failure") }
        }
        val client = LobsterPersonalityClient(api)
        assertTrue(runCatching { client.get() }.isFailure)
    }

    // ── Test 7: 401 on get returns null ──────────────────────────────────────

    @Test
    fun `401 on get returns null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet {
                Response.error(401, ResponseBody.create(null, "unauthorized"))
            }
        }
        val client = LobsterPersonalityClient(api)
        val result = client.get()
        assertNull(result)
    }

    // --- 扩展覆盖 ---

    @Test
    fun `get HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet { Response.error(500, ResponseBody.create(null, "Internal Error")) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.get())
    }

    @Test
    fun `get HTTP 200 但 body null 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet { Response.success(null as CommonResult?) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.get())
    }

    @Test
    fun `get code 200 同视为 success 返回 data`() = runBlocking {
        // OpenAPI 兼容：code=0 (业务码) 与 code=200 (HTTP 状态) 都视为成功
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet {
                Response.success(
                    CommonResult(
                        code = 200,
                        data = ClawMoodRespVO(
                            mood = "HAPPY",
                            intensity = 7,
                            traits = mapOf("warmth" to 5),
                            updatedAt = 100,
                        ),
                    ),
                )
            }
        }
        val client = LobsterPersonalityClient(api)
        val r = client.get()
        assertNotNull(r)
        assertEquals("HAPPY", r!!.mood)
    }

    @Test
    fun `get code=0 data 字段缺失 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet { Response.success(CommonResult(code = 0, data = null)) }
        }
        val client = LobsterPersonalityClient(api)
        // code=0 视为 success，但 data=null → as? ClawMoodRespVO = null
        assertNull(client.get())
    }

    @Test
    fun `get data 类型不匹配 返回 null`() = runBlocking {
        // 后端在 data 字段塞了字符串而非 ClawMoodRespVO → 强转失败 → null
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGet { Response.success(CommonResult(code = 0, data = "wrong-type")) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.get())
    }

    @Test
    fun `getTypes HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGetTypes { Response.error(500, ResponseBody.create(null, "Internal Error")) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.getTypes())
    }

    @Test
    fun `getTypes code=999 业务码错误 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGetTypes { Response.success(CommonResult(code = 999, msg = "wrong")) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.getTypes())
    }

    @Test
    fun `getTypes HTTP 200 但 body null 返回 null`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGetTypes { Response.success(null as CommonResult?) }
        }
        val client = LobsterPersonalityClient(api)
        assertNull(client.getTypes())
    }

    @Test
    fun `getTypes 网络异常 抛出`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueueGetTypes { throw IOException("net down") }
        }
        val client = LobsterPersonalityClient(api)
        assertTrue(runCatching { client.getTypes() }.isFailure)
    }

    @Test
    fun `update HTTP 500 返回 false 非 400 短路`() = runBlocking {
        // 注意：source 对 500 不会短路 400 分支（400 才会短路），其余非 2xx 仍走 !isSuccessful → false
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterPersonalityClient(api)
        assertFalse(client.update(ClawMoodUpdateReqVO("CALM", 5, null)))
    }

    @Test
    fun `update HTTP 401 返回 false`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { Response.error(401, ResponseBody.create(null, "Unauthorized")) }
        }
        val client = LobsterPersonalityClient(api)
        assertFalse(client.update(ClawMoodUpdateReqVO("CALM", 5, null)))
    }

    @Test
    fun `update HTTP 200 但 code=999 返回 false`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { Response.success(CommonResult(code = 999, msg = "rejected")) }
        }
        val client = LobsterPersonalityClient(api)
        assertFalse(client.update(ClawMoodUpdateReqVO("CALM", 5, null)))
    }

    @Test
    fun `update HTTP 200 但 body null 返回 false`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { Response.success(null as CommonResult?) }
        }
        val client = LobsterPersonalityClient(api)
        assertFalse(client.update(ClawMoodUpdateReqVO("CALM", 5, null)))
    }

    @Test
    fun `update 网络异常 抛出`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { throw IOException("net down") }
        }
        val client = LobsterPersonalityClient(api)
        assertTrue(runCatching { client.update(ClawMoodUpdateReqVO("CALM", 5, null)) }.isFailure)
    }

    @Test
    fun `update HTTP 200 code=200 同视为 success 返回 true`() = runBlocking {
        val api = FakeLobsterPersonalityApi().apply {
            enqueuePut { Response.success(CommonResult(code = 200, data = true)) }
        }
        val client = LobsterPersonalityClient(api)
        assertTrue(client.update(ClawMoodUpdateReqVO("CALM", 5, null)))
    }
}
