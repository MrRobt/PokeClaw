// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-040 LobsterPersonalityClient — 3 personality operations

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterPersonalityApi
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawMoodUpdateReqVO
import io.agents.pokeclaw.cloud.lobster.model.PersonalityTypes
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog

/**
 * Lobster personality client.
 *
 * Provides three operations:
 * - [get]: retrieve current personality/mood
 * - [getTypes]: retrieve available personality type dimensions
 * - [update]: update personality/mood
 *
 * @param api The underlying Retrofit API
 */
class LobsterPersonalityClient(
    private val api: LobsterPersonalityApi,
) {
    private val TAG = "LobsterPersonalityClient"

    /**
     * Get current personality/mood.
     *
     * @return ClawMoodRespVO on success, null on failure
     */
    suspend fun get(): ClawMoodRespVO? {
        val resp = try {
            api.getPersonality()
        } catch (e: Exception) {
            XLog.e(TAG, "get: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "get: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: return null
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "get: biz code=${body.code} msg=${body.msg}")
            return null
        }
        return body.data as? ClawMoodRespVO
    }

    /**
     * Get available personality type dimensions.
     *
     * @return PersonalityTypes on success, null on failure
     */
    suspend fun getTypes(): PersonalityTypes? {
        val resp = try {
            api.getPersonalityTypes()
        } catch (e: Exception) {
            XLog.e(TAG, "getTypes: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "getTypes: HTTP ${resp.code()}")
            return null
        }
        val body = resp.body() ?: return null
        if (body.code != 0 && body.code != 200) {
            XLog.w(TAG, "getTypes: biz code=${body.code} msg=${body.msg}")
            return null
        }
        return body.data as? PersonalityTypes
    }

    /**
     * Update personality/mood.
     *
     * @param req mood update request
     * @return true on success, false on validation failure (400) or other failure
     */
    suspend fun update(req: ClawMoodUpdateReqVO): Boolean {
        val resp = try {
            api.updatePersonality(req)
        } catch (e: Exception) {
            XLog.e(TAG, "update: network exception", e)
            throw e
        }
        if (resp.code() == 400) {
            XLog.w(TAG, "update: validation failed 400")
            return false
        }
        if (!resp.isSuccessful) {
            XLog.w(TAG, "update: HTTP ${resp.code()}")
            return false
        }
        val body = resp.body()
        return body?.code == 0 || body?.code == 200
    }
}
