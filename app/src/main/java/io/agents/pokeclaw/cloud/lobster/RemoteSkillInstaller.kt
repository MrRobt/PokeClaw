// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.cloud.lobster

import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.agent.skill.Skill
import io.agents.pokeclaw.agent.skill.SkillCategory
import io.agents.pokeclaw.agent.skill.SkillRegistry
import io.agents.pokeclaw.agent.skill.SkillStep
import io.agents.pokeclaw.cloud.CloudClientFactory
import io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore
import io.agents.pokeclaw.cloud.lobster.client.SkillMarketplaceClient
import io.agents.pokeclaw.cloud.lobster.model.ClawAppSkillMarketRespVO
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * P0-2 远程 skill 安装接线。
 *
 * 打通 dyq skill marketplace → 端侧 [SkillRegistry],让"加载远程下发安装的 skill"真正可用:
 * 1. 用 deviceToken 拉取云端 skill 列表([SkillMarketplaceClient.listSkills])
 * 2. 解析每个 skill 的可执行定义(steps JSON;优先 `definition` 字段,回退 `description`)
 * 3. 转成本地 [Skill] 并 [SkillRegistry.register] 注入 → 本地 agent 路由可用
 * 4. [SkillMarketplaceClient.installSkill] 回执云端标记已安装
 *
 * 定义 JSON 约定:
 * ```json
 * {"steps":[{"tool":"open_app","params":{"package_name":"Settings"}},
 *           {"tool":"wait","params":{"duration_ms":"2000"}}],
 *  "triggers":["run remote demo"], "category":"NAVIGATION",
 *  "desc":"…", "fallbackGoal":"…"}
 * ```
 *
 * 注:当前 [SkillRegistry] 仅持久化统计、不持久化 skill 定义,故动态安装的 skill 在进程存续期有效;
 * 生产可扩展一个 RemoteSkillStore 落盘 + 启动时重放(见 BACKLOG P1)。
 */
object RemoteSkillInstaller {

    private const val TAG = "RemoteSkillInstaller"

    /** 阻塞式入口(供 debug/后台线程调用)。返回成功安装数。 */
    fun syncRemoteSkillsBlocking(): Int = runBlocking { syncRemoteSkills() }

    suspend fun syncRemoteSkills(): Int {
        val baseUrl = KVUtils.getString("cloud_base_url").takeIf { it.isNotBlank() }
        if (baseUrl == null) {
            XLog.w(TAG, "syncRemoteSkills: cloud_base_url 未配置,跳过")
            return 0
        }
        val tokenStore = AndroidKeystoreCloudDeviceTokenStore(ClawApplication.instance)
        val client = SkillMarketplaceClient(
            CloudClientFactory.buildSkillMarketplaceApi(baseUrl, tokenStore)
        )
        XLog.i(TAG, "syncRemoteSkills: 拉取云端 skill 列表, baseUrl=$baseUrl")
        val result = client.listSkills()
        if (result !is SkillMarketplaceClient.Result.OkList) {
            XLog.w(TAG, "syncRemoteSkills: 拉取失败: $result")
            return 0
        }
        XLog.i(TAG, "syncRemoteSkills: 云端返回 ${result.skills.size} 个 skill")
        var installed = 0
        for (dto in result.skills) {
            val skill = toLocalSkill(dto)
            if (skill == null) {
                XLog.w(TAG, "syncRemoteSkills: skill ${dto.skillId} 无可执行定义,跳过")
                continue
            }
            SkillRegistry.register(skill)
            val ack = client.installSkill(dto.skillId)
            XLog.i(
                TAG,
                "syncRemoteSkills: ✅ 已安装远程 skill id=${skill.id} name=${skill.name} " +
                    "steps=${skill.steps.size} triggers=${skill.triggerPatterns} installAck=$ack"
            )
            installed++
        }
        XLog.i(
            TAG,
            "syncRemoteSkills: 完成,新装 $installed 个;SkillRegistry 现有 ${SkillRegistry.getAll().size} 个 skill"
        )
        return installed
    }

    /** 云端 skill DTO → 本地可执行 [Skill]。定义优先取 `definition`,回退 `description`(需为 JSON)。 */
    private fun toLocalSkill(dto: ClawAppSkillMarketRespVO): Skill? {
        val defJson = dto.definition?.takeIf { it.isNotBlank() }
            ?: dto.description?.takeIf { it.trimStart().startsWith("{") }
            ?: return null
        return parseSkillDefinition(defJson, fallbackId = dto.skillId, fallbackName = dto.skillName)
    }

    /**
     * 解析 skill 定义 JSON → 本地可执行 [Skill]。JSON 可自带 `id`/`name`(优先),否则用 fallback。
     * 供两条路复用:marketplace(app-api)与远程下发(设备任务通道 `install_skill:<JSON>`)。
     */
    fun parseSkillDefinition(defJson: String, fallbackId: String = "", fallbackName: String = ""): Skill? {
        return try {
            val obj = JSONObject(defJson)
            val stepsArr = obj.optJSONArray("steps") ?: return null
            val steps = ArrayList<SkillStep>()
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.getJSONObject(i)
                val tool = s.optString("tool").ifBlank { s.optString("toolName") }
                if (tool.isBlank()) continue
                val params = LinkedHashMap<String, String>()
                s.optJSONObject("params")?.let { p ->
                    val keys = p.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        params[k] = p.get(k).toString()
                    }
                }
                steps.add(
                    SkillStep(
                        toolName = tool,
                        params = params,
                        description = s.optString("description"),
                        optional = s.optBoolean("optional", false),
                        retries = s.optInt("retries", 1),
                    )
                )
            }
            if (steps.isEmpty()) return null
            val triggers = ArrayList<String>()
            obj.optJSONArray("triggers")?.let { t ->
                for (i in 0 until t.length()) triggers.add(t.getString(i))
            }
            val category = runCatching {
                SkillCategory.valueOf(obj.optString("category", "GENERAL").uppercase())
            }.getOrDefault(SkillCategory.GENERAL)
            val id = obj.optString("id", fallbackId).ifBlank { return null }
            val name = obj.optString("name", fallbackName).ifBlank { id }
            Skill(
                id = id,
                name = name,
                description = obj.optString("desc", name),
                category = category,
                steps = steps,
                triggerPatterns = triggers,
                fallbackGoal = obj.optString("fallbackGoal", ""),
                userFacing = false,
            )
        } catch (e: Exception) {
            XLog.e(TAG, "parseSkillDefinition: 解析 skill 定义失败", e)
            null
        }
    }
}
