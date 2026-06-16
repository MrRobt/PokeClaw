// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-038 主人指令客户端：submit + 轮询 + hermes feedback

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterCommandApi
import io.agents.pokeclaw.cloud.lobster.model.CommandDetailResult
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteReq
import io.agents.pokeclaw.cloud.lobster.model.CommandExecuteResp
import io.agents.pokeclaw.cloud.lobster.model.HermesFeedbackReq
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.cloud.util.PollingPolicy
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.delay

/**
 * Lobster command channel client.
 *
 * Provides three operations:
 * - [submit]: submit a command and get an executionId immediately
 * - [poll]: poll for command execution result until terminal status or timeout
 * - [submitHermesFeedback]: send hermes feedback and return success/failure
 *
 * All methods are suspending and thread-safe.
 *
 * @param api The underlying Retrofit API
 * @param policy Polling policy for [poll] retries and timeout (default 3-phase backoff + 5min)
 * @param nowProvider Time provider for testing (default System.currentTimeMillis)
 */
class LobsterCommandClient(
    private val api: LobsterCommandApi,
    private val policy: PollingPolicy = PollingPolicy(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val TAG = "LobsterCommandClient"

    /** Result of submit or poll operations. */
    sealed class Result {
        /** Command accepted; [executionId] can be used for polling. */
        data class Ok(val executionId: String, val status: String, val detail: CommandDetailResult?) : Result()
        /** Command rejected by server (non-2xx HTTP or biz error). */
        data class Rejected(val message: String) : Result()
        /** Polling exceeded total timeout. */
        data class PollTimeout(val executionId: String) : Result()
        /** Execution ID not found (404). */
        data class NotFound(val executionId: String) : Result()
    }

    /**
     * Submit a command.
     *
     * @param command The command text
     * @param skillId Optional skill ID
     * @param context Optional context map
     * @return Result.Ok with executionId on success, Result.Rejected on business error
     * @throws Exception on network failure (caller should retry)
     */
    suspend fun submit(command: String, skillId: String? = null, context: Map<String, Any?>? = null): Result {
        val resp = try {
            api.executeCommand(CommandExecuteReq(command, skillId, context))
        } catch (e: Exception) {
            XLog.e(TAG, "submit: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        @Suppress("UNCHECKED_CAST")
        val data = body.data as? CommandExecuteResp
            ?: return Result.Rejected("no data")
        return Result.Ok(data.executionId, data.status, null)
    }

    /**
     * Poll command execution result until terminal status or timeout.
     *
     * @param executionId The execution ID returned by [submit]
     * @return Result.Ok with terminal status, Result.PollTimeout on total timeout,
     *         Result.NotFound on 404
     */
    suspend fun poll(executionId: String): Result {
        val startedAt = nowProvider()
        var attempt = 0
        while (true) {
            attempt++
            val elapsed = nowProvider() - startedAt
            if (policy.isExpired(elapsed)) {
                XLog.e(TAG, "poll: timeout executionId=$executionId attempt=$attempt")
                return Result.PollTimeout(executionId)
            }
            val resp = try {
                api.getCommandResult(executionId)
            } catch (e: Exception) {
                XLog.w(TAG, "poll: exception attempt=$attempt", e)
                delay(policy.nextDelayMillis(attempt, elapsed))
                continue
            }
            if (resp.code() == 404) return Result.NotFound(executionId)
            val body = resp.body()
            @Suppress("UNCHECKED_CAST")
            val data = body?.data as? CommandDetailResult
            if (data == null) {
                delay(policy.nextDelayMillis(attempt, elapsed))
                continue
            }
            if (policy.shouldStop(data.status)) {
                return Result.Ok(executionId, data.status, data)
            }
            delay(policy.nextDelayMillis(attempt, elapsed))
        }
    }

    /**
     * Submit Hermes feedback.
     *
     * @param feedbackType Feedback type string
     * @param payload Optional payload map
     * @param taskUuid Optional task UUID
     * @return true on HTTP 200 or 202, false otherwise
     * @throws Exception on network failure
     */
    suspend fun submitHermesFeedback(
        feedbackType: String,
        payload: Map<String, Any?>? = null,
        taskUuid: String? = null,
    ): Boolean {
        val resp = try {
            api.submitHermesFeedback(HermesFeedbackReq(feedbackType, payload, taskUuid))
        } catch (e: Exception) {
            XLog.e(TAG, "submitHermesFeedback: exception", e)
            throw e
        }
        return resp.code() == 200 || resp.code() == 202
    }
}