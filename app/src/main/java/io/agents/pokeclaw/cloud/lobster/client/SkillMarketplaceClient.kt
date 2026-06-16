// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-039 Skill Marketplace 客户端：list / install / save / remove / batch-status

package io.agents.pokeclaw.cloud.lobster.client

import io.agents.pokeclaw.cloud.lobster.api.LobsterSkillMarketplaceApi
import io.agents.pokeclaw.cloud.lobster.model.BatchSkillStatusReqVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillSaveReqVO
import io.agents.pokeclaw.cloud.model.CommonResult
import io.agents.pokeclaw.utils.XLog
import retrofit2.Response

/**
 * Skill Marketplace client (US-D-039).
 *
 * Provides five operations:
 * - [listSkills]: fetch available skills from marketplace
 * - [installSkill]: install a skill by ID
 * - [saveSkill]: create new skill or update existing
 * - [removeSkill]: delete a skill by ID
 * - [batchUpdateStatus]: enable/disable multiple skills
 *
 * All methods are suspending and thread-safe.
 *
 * @param api The underlying Retrofit API
 */
class SkillMarketplaceClient(
    private val api: LobsterSkillMarketplaceApi,
) {
    private val TAG = "SkillMarketplaceClient"

    /**
     * Result of list/install/remove/batch operations.
     * save returns either a String (new id) or Boolean (update).
     */
    sealed class Result {
        data class OkList(val skills: List<ClawAppSkillMarketRespVO>) : Result()
        data class OkBoolean(val value: Boolean) : Result()
        data class OkStringId(val id: String) : Result()
        data class Rejected(val message: String) : Result()
    }

    /**
     * 获取技能市场列表
     *
     * @return Result.OkList on success, Result.Rejected on error
     * @throws Exception on network failure
     */
    suspend fun listSkills(): Result {
        XLog.d(TAG, "listSkills: fetching skill list")
        val resp = try {
            api.listSkills()
        } catch (e: Exception) {
            XLog.e(TAG, "listSkills: network exception", e)
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
        val rawList = body.data as? List<*>
        val skills = rawList?.mapNotNull {
            it as? ClawAppSkillMarketRespVO
        } ?: emptyList()
        XLog.i(TAG, "listSkills: fetched ${skills.size} skills")
        return Result.OkList(skills)
    }

    /**
     * 安装技能
     *
     * @param skillId 技能 ID
     * @return Result.OkBoolean on success, Result.Rejected on error
     * @throws Exception on network failure
     */
    suspend fun installSkill(skillId: String): Result {
        XLog.d(TAG, "installSkill: skillId=$skillId")
        val resp = try {
            api.installSkill(mapOf("skillId" to skillId))
        } catch (e: Exception) {
            XLog.e(TAG, "installSkill: network exception skillId=$skillId", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        val success = (body.data as? Boolean) ?: false
        if (success) {
            XLog.i(TAG, "installSkill: skillId=$skillId success=true")
        } else {
            // 安装失败由后端业务码 0/200 + data=false 表达，应升级为 error 等级便于 oncall 检索
            XLog.e(TAG, "installSkill: skillId=$skillId success=false (后端业务失败)")
        }
        return Result.OkBoolean(success)
    }

    /**
     * 保存技能（新建或更新）
     *
     * @param req 技能保存请求
     * @return Result.OkStringId (新建返回 id) 或 Result.OkBoolean (更新返回 true)
     * @throws Exception on network failure
     */
    suspend fun saveSkill(req: ClawAppSkillSaveReqVO): Result {
        XLog.d(TAG, "saveSkill: id=${req.id} skillName=${req.skillName}")
        val resp = try {
            api.saveSkill(req)
        } catch (e: Exception) {
            XLog.e(TAG, "saveSkill: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        // 新建返回 String id，更新返回 Boolean
        val result = when (val data = body.data) {
            is String -> {
                XLog.i(TAG, "saveSkill: created new skill id=$data")
                Result.OkStringId(data)
            }
            is Boolean -> {
                XLog.i(TAG, "saveSkill: updated skill success=$data")
                Result.OkBoolean(data)
            }
            else -> {
                XLog.w(TAG, "saveSkill: unexpected data type ${data?.javaClass}")
                Result.OkBoolean(false)
            }
        }
        return result
    }

    /**
     * 删除技能
     *
     * @param id 技能 ID
     * @return Result.OkBoolean on success, Result.Rejected on error
     * @throws Exception on network failure
     */
    suspend fun removeSkill(id: String): Result {
        XLog.d(TAG, "removeSkill: id=$id")
        val resp = try {
            api.removeSkill(id)
        } catch (e: Exception) {
            XLog.e(TAG, "removeSkill: network exception id=$id", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        val success = (body.data as? Boolean) ?: false
        if (success) {
            XLog.i(TAG, "removeSkill: id=$id success=true")
        } else {
            // 业务失败：通常意味着 skillId 不存在或已被删除，warn 等级以便排查
            XLog.w(TAG, "removeSkill: id=$id success=false (skillId 可能不存在)")
        }
        return Result.OkBoolean(success)
    }

    /**
     * 批量更新技能状态
     *
     * @param ids 技能 ID 列表
     * @param enable 是否启用
     * @return Result.OkBoolean on success, Result.Rejected on error
     * @throws Exception on network failure
     */
    suspend fun batchUpdateStatus(ids: List<String>, enable: Boolean): Result {
        XLog.d(TAG, "batchUpdateStatus: ids=${ids.size} enable=$enable")
        val resp = try {
            api.batchUpdateStatus(BatchSkillStatusReqVO(ids, enable))
        } catch (e: Exception) {
            XLog.e(TAG, "batchUpdateStatus: network exception", e)
            throw e
        }
        if (!resp.isSuccessful) {
            return Result.Rejected("HTTP ${resp.code()}")
        }
        val body = resp.body() ?: return Result.Rejected("empty body")
        if (body.code != 0 && body.code != 200) {
            return Result.Rejected("biz code=${body.code} msg=${body.msg}")
        }
        val success = (body.data as? Boolean) ?: false
        XLog.i(TAG, "batchUpdateStatus: success=$success")
        return Result.OkBoolean(success)
    }
}