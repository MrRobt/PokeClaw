// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterMemoryClient — 4 memory operations

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterMemoryApi
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryCreateReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMemoryRespVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
import io.agents.pokeclaw.utils.XLog
import retrofit2.Response

/**
 * Lobster memory client.
 *
 * Provides four operations:
 * - [list]: paginated memory list by type
 * - [create]: create a new memory
 * - [delete]: delete a single memory by id
 * - [clearAll]: delete all memories
 *
 * @param api The underlying Retrofit API
 */
class LobsterMemoryClient(
    private val api: LobsterMemoryApi,
) {
    private val TAG = "LobsterMemoryClient"

    /**
     * List memories with optional type filter.
     *
     * @param memoryType optional memory type filter
     * @param pageNo page number (1-based)
     * @param pageSize page size
     * @return list of ClawMemoryRespVO on success, null on failure
     */
    suspend fun list(
        memoryType: String? = null,
        pageNo: Int = 1,
        pageSize: Int = 20,
    ): List<ClawMemoryRespVO>? {
        val resp = try {
            api.listMemories(memoryType, pageNo, pageSize)
        } catch (e: Exception) {
            XLog.e(TAG, "list: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "list: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: return null
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "list: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val pageResult = body.data as? PageResult
            ?: return null
        @Suppress("UNCHECKED_CAST")
        return pageResult.list as? List<ClawMemoryRespVO> ?: null
    }

    /**
     * Create a new memory.
     *
     * @param req memory creation request
     * @return ClawMemoryRespVO on success, null on failure
     */
    suspend fun create(req: ClawMemoryCreateReqVO): ClawMemoryRespVO? {
        val resp = try {
            api.createMemory(req)
        } catch (e: Exception) {
            XLog.e(TAG, "create: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "create: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: return null
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "create: biz code=${body.code} msg=${body.msg}")
            return null
        }
        return body.data as? ClawMemoryRespVO
    }

    /**
     * Delete a single memory by id.
     *
     * @param id memory id
     * @return true on success, false on 404 or failure
     */
    suspend fun delete(id: String): Boolean {
        val resp = try {
            api.deleteMemory(id)
        } catch (e: Exception) {
            XLog.e(TAG, "delete: network exception id=$id", e)
            throw e
        }
        if (resp.code() == 404) {
            XLog.w(TAG, "delete: 404 id=$id")
            return false
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "delete: HTTP ${resp.code()} id=$id")
            return false
        }
        val body = resp.body()
        return body?.code == 0 || body?.code == 200
    }

    /**
     * Delete all memories.
     *
     * @return true on success, false on failure
     */
    suspend fun clearAll(): Boolean {
        val resp = try {
            api.clearAllMemories()
        } catch (e: Exception) {
            XLog.e(TAG, "clearAll: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "clearAll: HTTP ${resp.code()}")
            return false
        }
        val body = resp.body()
        return body?.code == 0 || body?.code == 200
    }
}
