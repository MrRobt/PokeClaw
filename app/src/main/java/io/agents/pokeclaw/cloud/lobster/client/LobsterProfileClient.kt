// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-041 lobster profile 客户端：my + stats + executions + skills + suggestions

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterProfileApi
import io.agents.pokeclaw.cloud.lobster.model.ClawAppExecutionRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawLobsterStatsRespVO
import io.agents.pokeclaw.cloud.lobster.model.SuggestionResult
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.model.PageResult
import io.agents.pokeclaw.utils.XLog

/**
 * Lobster profile client.
 *
 * Provides five read operations:
 * - [getMy]: fetch current user lobster info
 * - [getStats]: fetch lobster statistics
 * - [getExecutions]: fetch execution history with optional skillId filter and pagination
 * - [getMySkills]: fetch installed skills list
 * - [getMySuggestions]: fetch suggestions
 *
 * All methods are suspending and thread-safe.
 *
 * @param api The underlying Retrofit API
 */
class LobsterProfileClient(
    private val api: LobsterProfileApi,
) {
    private val TAG = "LobsterProfileClient"

    /**
     * Get current user lobster info.
     *
     * @return ClawLobsterRespVO on success, null on business error or network failure
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun getMy(): ClawLobsterRespVO? {
        val resp = try {
            api.getMy()
        } catch (e: Exception) {
            XLog.e(TAG, "getMy: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getMy: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: run {
            XLog.w(TAG, "getMy: empty body")
            return null
        }
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getMy: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return body.data as? ClawLobsterRespVO
    }

    /**
     * Get lobster statistics.
     *
     * @return ClawLobsterStatsRespVO on success, null on business error or network failure
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun getStats(): ClawLobsterStatsRespVO? {
        val resp = try {
            api.getStats()
        } catch (e: Exception) {
            XLog.e(TAG, "getStats: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getStats: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: run {
            XLog.w(TAG, "getStats: empty body")
            return null
        }
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getStats: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return body.data as? ClawLobsterStatsRespVO
    }

    /**
     * Get execution history with optional skillId filter and pagination.
     *
     * @param skillId Optional skill ID to filter by
     * @param pageNo Page number (starting from 1)
     * @param pageSize Page size
     * @return List of ClawAppExecutionRespVO on success, null on business error or network failure
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun getExecutions(
        skillId: String? = null,
        pageNo: Int = 1,
        pageSize: Int = 20,
    ): List<ClawAppExecutionRespVO>? {
        val resp = try {
            api.getExecutions(skillId, pageNo, pageSize)
        } catch (e: Exception) {
            XLog.e(TAG, "getExecutions: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getExecutions: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: run {
            XLog.w(TAG, "getExecutions: empty body")
            return null
        }
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getExecutions: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val pageResult = body.data as? PageResult
        if (pageResult == null) {
            XLog.w(TAG, "getExecutions: no PageResult data")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return pageResult.list as? List<ClawAppExecutionRespVO>
    }

    /**
     * Get installed skills list.
     *
     * @return List of ClawAppSkillRespVO on success, null on business error or network failure
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun getMySkills(): List<ClawAppSkillRespVO>? {
        val resp = try {
            api.getMySkills()
        } catch (e: Exception) {
            XLog.e(TAG, "getMySkills: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getMySkills: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: run {
            XLog.w(TAG, "getMySkills: empty body")
            return null
        }
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getMySkills: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return body.data as? List<ClawAppSkillRespVO>
    }

    /**
     * Get suggestions.
     *
     * @return SuggestionResult on success, null on business error or network failure
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun getMySuggestions(): SuggestionResult? {
        val resp = try {
            api.getMySuggestions()
        } catch (e: Exception) {
            XLog.e(TAG, "getMySuggestions: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getMySuggestions: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: run {
            XLog.w(TAG, "getMySuggestions: empty body")
            return null
        }
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getMySuggestions: biz code=${body.code} msg=${body.msg}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return body.data as? SuggestionResult
    }
}