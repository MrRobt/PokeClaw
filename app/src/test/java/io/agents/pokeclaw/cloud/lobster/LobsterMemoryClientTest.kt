// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterMemoryClient 9 用例

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.cloud.lobster.api.LobsterMemoryApi
import io.agents.pokeclaw.cloud.lobster.client.LobsterMemoryClient
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryCreateReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryRespVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
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
 * US-D-040 LobsterMemoryClient 9 用例
 */
class LobsterMemoryClientTest {

    @Before
    fun setup() {
        XLog.setTestMode(true)
    }

    // ── Fake API ─────────────────────────────────────────────────────────────────

    private class FakeLobsterMemoryApi : LobsterMemoryApi {
        data class Enqueued(val block: () -> Response<CommonResult>)

        private val listQueue = ArrayDeque<Enqueued>()
        private val createQueue = ArrayDeque<Enqueued>()
        private val deleteQueue = ArrayDeque<Enqueued>()
        private val clearAllQueue = ArrayDeque<Enqueued>()

        fun enqueueList(block: () -> Response<CommonResult>) { listQueue.add(Enqueued(block)) }
        fun enqueueCreate(block: () -> Response<CommonResult>) { createQueue.add(Enqueued(block)) }
        fun enqueueDelete(block: () -> Response<CommonResult>) { deleteQueue.add(Enqueued(block)) }
        fun enqueueClearAll(block: () -> Response<CommonResult>) { clearAllQueue.add(Enqueued(block)) }

        override suspend fun listMemories(memoryType: String?, pageNo: Int, pageSize: Int): Response<CommonResult> {
            val e = listQueue.removeFirstOrNull() ?: error("no enqueued list response")
            return e.block()
        }
        override suspend fun createMemory(req: ClawMemoryCreateReqVO): Response<CommonResult> {
            val e = createQueue.removeFirstOrNull() ?: error("no enqueued create response")
            return e.block()
        }
        override suspend fun deleteMemory(id: String): Response<CommonResult> {
            val e = deleteQueue.removeFirstOrNull() ?: error("no enqueued delete response for $id")
            return e.block()
        }
        override suspend fun clearAllMemories(): Response<CommonResult> {
            val e = clearAllQueue.removeFirstOrNull() ?: error("no enqueued clearAll response")
            return e.block()
        }
    }

    // ── Test 1: list 0 items returns empty list ─────────────────────────────────

    @Test
    fun `list 0 items returns empty list`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PageResult(list = emptyList<Any>(), total = 0),
                    ),
                )
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.list()
        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    // ── Test 2: list N items returns parsed list ───────────────────────────────

    @Test
    fun `list N items returns parsed list`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = PageResult(
                            list = listOf(
                                ClawMemoryRespVO("mem-1", "hello", "CHAT", 1, 1, listOf("tag1")),
                                ClawMemoryRespVO("mem-2", "world", "HABIT", 2, 2, listOf("tag2")),
                            ),
                            total = 2,
                        ),
                    ),
                )
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.list()
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("mem-1", result[0].id)
        assertEquals("hello", result[0].content)
    }

    // ── Test 3: create ok returns ClawMemoryRespVO ───────────────────────────

    @Test
    fun `create ok returns ClawMemoryRespVO`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueCreate {
                Response.success(
                    CommonResult(
                        code = 0,
                        data = ClawMemoryRespVO("mem-new", "new content", "CHAT", 100, 100, listOf()),
                    ),
                )
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.create(ClawMemoryCreateReqVO("new content", "CHAT", listOf()))
        assertNotNull(result)
        assertEquals("mem-new", result!!.id)
        assertEquals("new content", result.content)
    }

    // ── Test 4: create fail returns null ─────────────────────────────────────

    @Test
    fun `create fail returns null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueCreate {
                Response.success(CommonResult(code = 999, msg = "bad request"))
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.create(ClawMemoryCreateReqVO("bad", "CHAT", null))
        assertNull(result)
    }

    // ── Test 5: delete ok returns true ───────────────────────────────────────

    @Test
    fun `delete ok returns true`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.delete("mem-1")
        assertTrue(result)
    }

    // ── Test 6: delete 404 returns false ─────────────────────────────────────

    @Test
    fun `delete 404 returns false`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete {
                Response.error(404, ResponseBody.create(null, "not found"))
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.delete("mem-nonexistent")
        assertFalse(result)
    }

    // ── Test 7: clear all ok returns true ───────────────────────────────────

    @Test
    fun `clear all ok returns true`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueClearAll {
                Response.success(CommonResult(code = 0, data = true))
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.clearAll()
        assertTrue(result)
    }

    // ── Test 8: network exception throws ──────────────────────────────────────

    @Test
    fun `network exception throws`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList { throw IOException("simulated network failure") }
        }
        val client = LobsterMemoryClient(api)
        assertTrue(runCatching { client.list() }.isFailure)
    }

    // ── Test 9: 401 on list returns null (unauthorized) ──────────────────────

    @Test
    fun `401 on list returns null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList {
                Response.error(401, ResponseBody.create(null, "unauthorized"))
            }
        }
        val client = LobsterMemoryClient(api)
        val result = client.list()
        assertNull(result)
    }

    // --- 扩展覆盖 ---

    @Test fun `list HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList { Response.error(500, ResponseBody.create(null, "Internal Error")) }
        }
        val client = LobsterMemoryClient(api)
        assertNull(client.list())
    }

    @Test fun `list HTTP 200 body null 返回 null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList { Response.success(null as CommonResult?) }
        }
        val client = LobsterMemoryClient(api)
        assertNull(client.list())
    }

    @Test fun `list data=null 返回 null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList { Response.success(CommonResult(code = 0, data = null)) }
        }
        val client = LobsterMemoryClient(api)
        // code=0 但 data=null → as? PageResult = null
        assertNull(client.list())
    }

    @Test fun `list code=200 同视为 success 返回 items`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueList {
                Response.success(
                    CommonResult(
                        code = 200,
                        data = PageResult(
                            list = listOf(ClawMemoryRespVO("m", "hi", "CHAT", 1, 1, listOf())),
                            total = 1,
                        ),
                    ),
                )
            }
        }
        val client = LobsterMemoryClient(api)
        val r = client.list()
        assertNotNull(r)
        assertEquals(1, r!!.size)
    }

    @Test fun `create HTTP 500 返回 null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueCreate { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterMemoryClient(api)
        assertNull(client.create(ClawMemoryCreateReqVO("x", "CHAT", null)))
    }

    @Test fun `create HTTP 200 body null 返回 null`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueCreate { Response.success(null as CommonResult?) }
        }
        val client = LobsterMemoryClient(api)
        assertNull(client.create(ClawMemoryCreateReqVO("x", "CHAT", null)))
    }

    @Test fun `delete HTTP 500 返回 false 非 404 短路`() = runBlocking {
        // 500 不是 404，所以走 !isSuccessful 分支 → false
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterMemoryClient(api)
        assertFalse(client.delete("mem-1"))
    }

    @Test fun `delete HTTP 200 body null 返回 false`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete { Response.success(null as CommonResult?) }
        }
        val client = LobsterMemoryClient(api)
        assertFalse(client.delete("mem-1"))
    }

    @Test fun `delete HTTP 200 code=200 同视为 success 返回 true`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete { Response.success(CommonResult(code = 200, data = true)) }
        }
        val client = LobsterMemoryClient(api)
        assertTrue(client.delete("mem-1"))
    }

    @Test fun `clearAll HTTP 500 返回 false`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueClearAll { Response.error(500, ResponseBody.create(null, "Internal")) }
        }
        val client = LobsterMemoryClient(api)
        assertFalse(client.clearAll())
    }

    @Test fun `clearAll HTTP 401 返回 false`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueClearAll { Response.error(401, ResponseBody.create(null, "Unauthorized")) }
        }
        val client = LobsterMemoryClient(api)
        assertFalse(client.clearAll())
    }

    @Test fun `clearAll HTTP 200 body null 返回 false`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueClearAll { Response.success(null as CommonResult?) }
        }
        val client = LobsterMemoryClient(api)
        assertFalse(client.clearAll())
    }

    @Test fun `clearAll 网络异常 抛出`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueClearAll { throw IOException("net down") }
        }
        val client = LobsterMemoryClient(api)
        assertTrue(runCatching { client.clearAll() }.isFailure)
    }

    @Test fun `create 网络异常 抛出`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueCreate { throw IOException("net down") }
        }
        val client = LobsterMemoryClient(api)
        assertTrue(runCatching { client.create(ClawMemoryCreateReqVO("x", "CHAT", null)) }.isFailure)
    }

    @Test fun `delete 网络异常 抛出`() = runBlocking {
        val api = FakeLobsterMemoryApi().apply {
            enqueueDelete { throw IOException("net down") }
        }
        val client = LobsterMemoryClient(api)
        assertTrue(runCatching { client.delete("mem-1") }.isFailure)
    }
}
