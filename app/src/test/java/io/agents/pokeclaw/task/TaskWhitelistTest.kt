// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// TaskWhitelist 单测 — 枚举稳定性、P0-P3 任务数 / 唯一性、BLOCKED 列表、isWhitelisted / isBlocked / validateTask 路由。

package io.agents.pokeclaw.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWhitelistTest {

    // ── 枚举稳定性 (业务契约) ──

    @Test
    fun `RiskLevel 4 值稳定 codes 0-3`() {
        val values = TaskWhitelist.RiskLevel.values()
        assertEquals(4, values.size)
        assertEquals(0, TaskWhitelist.RiskLevel.LOW.code)
        assertEquals(1, TaskWhitelist.RiskLevel.MEDIUM.code)
        assertEquals(2, TaskWhitelist.RiskLevel.HIGH.code)
        assertEquals(3, TaskWhitelist.RiskLevel.CRITICAL.code)
    }

    @Test
    fun `TaskCategory 7 值稳定 (UI 契约)`() {
        val values = TaskWhitelist.TaskCategory.values().toSet()
        assertEquals(
            setOf(
                TaskWhitelist.TaskCategory.INFO_QUERY,
                TaskWhitelist.TaskCategory.SYSTEM_SETTING_QUERY,
                TaskWhitelist.TaskCategory.UI_OPERATION,
                TaskWhitelist.TaskCategory.APP_CONTROL,
                TaskWhitelist.TaskCategory.INPUT_OPERATION,
                TaskWhitelist.TaskCategory.SYSTEM_SETTING_MODIFY,
                TaskWhitelist.TaskCategory.FILE_OPERATION,
            ),
            values,
        )
    }

    // ── P0_TASKS ──

    @Test
    fun `P0_TASKS - 全部为 LOW 风险`() {
        assertTrue(TaskWhitelist.P0_TASKS.isNotEmpty())
        for (t in TaskWhitelist.P0_TASKS) {
            assertEquals(
                "P0 任务 '${t.action}' 应是 LOW 风险，实际 ${t.riskLevel}",
                TaskWhitelist.RiskLevel.LOW, t.riskLevel,
            )
            assertFalse("P0 任务 '${t.action}' 不应要求确认", t.requiresConfirmation)
        }
    }

    @Test
    fun `P0_TASKS - 包含 query_battery_level`() {
        val t = TaskWhitelist.getTaskDefinition("query_battery_level")
        assertNotNull(t)
        assertEquals(TaskWhitelist.TaskCategory.INFO_QUERY, t!!.category)
        assertEquals(0, t.parameters.size)  // 无参数
    }

    @Test
    fun `P0_TASKS - 包含 query_installed_apps 含 includeSystem 参数`() {
        val t = TaskWhitelist.getTaskDefinition("query_installed_apps")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("includeSystem"))
    }

    @Test
    fun `P0_TASKS 数量稳定 (业务契约 9 个查询)`() {
        // P0 = 5 INFO_QUERY + 4 SYSTEM_SETTING_QUERY
        assertEquals(9, TaskWhitelist.P0_TASKS.size)
    }

    // ── P1_TASKS ──

    @Test
    fun `P1_TASKS 全部为 MEDIUM 或 LOW 风险`() {
        for (t in TaskWhitelist.P1_TASKS) {
            assertTrue(
                "P1 任务 '${t.action}' 应是 MEDIUM 或 LOW，实际 ${t.riskLevel}",
                t.riskLevel == TaskWhitelist.RiskLevel.MEDIUM ||
                    t.riskLevel == TaskWhitelist.RiskLevel.LOW,
            )
        }
    }

    @Test
    fun `P1_TASKS 中 MEDIUM 风险 全部 requiresConfirmation=true`() {
        for (t in TaskWhitelist.P1_TASKS) {
            if (t.riskLevel == TaskWhitelist.RiskLevel.MEDIUM) {
                assertTrue("MEDIUM 任务 '${t.action}' 必须 requiresConfirmation", t.requiresConfirmation)
            }
        }
    }

    @Test
    fun `P1_TASKS - ui_swipe 含 direction distance 参数`() {
        val t = TaskWhitelist.getTaskDefinition("ui_swipe")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("direction"))
        assertTrue(t.parameters.contains("distance"))
    }

    @Test
    fun `P1_TASKS - ui_press_back home recent 风险为 LOW 不需确认`() {
        for (action in listOf("ui_press_back", "ui_press_home", "ui_press_recent")) {
            val t = TaskWhitelist.getTaskDefinition(action)
            assertNotNull("任务 $action 应存在", t)
            assertEquals(TaskWhitelist.RiskLevel.LOW, t!!.riskLevel)
            assertFalse(t.requiresConfirmation)
        }
    }

    // ── P2_TASKS ──

    @Test
    fun `P2_TASKS 全部为 MEDIUM 风险且 requiresConfirmation=true`() {
        assertTrue(TaskWhitelist.P2_TASKS.isNotEmpty())
        for (t in TaskWhitelist.P2_TASKS) {
            assertEquals(TaskWhitelist.RiskLevel.MEDIUM, t.riskLevel)
            assertTrue("P2 任务 '${t.action}' 必须 requiresConfirmation", t.requiresConfirmation)
        }
    }

    @Test
    fun `P2_TASKS - input_text 含 text 参数`() {
        val t = TaskWhitelist.getTaskDefinition("input_text")
        assertNotNull(t)
        assertEquals(TaskWhitelist.TaskCategory.INPUT_OPERATION, t!!.category)
        assertTrue(t.parameters.contains("text"))
    }

    // ── P3_TASKS ──

    @Test
    fun `P3_TASKS 全部为 HIGH 风险且 requiresConfirmation=true`() {
        assertTrue(TaskWhitelist.P3_TASKS.isNotEmpty())
        for (t in TaskWhitelist.P3_TASKS) {
            assertEquals(TaskWhitelist.RiskLevel.HIGH, t.riskLevel)
            assertTrue("P3 任务 '${t.action}' 必须 requiresConfirmation", t.requiresConfirmation)
        }
    }

    @Test
    fun `P3_TASKS - set_brightness set_volume file_read 存在`() {
        for (action in listOf("set_brightness", "set_volume", "file_read")) {
            val t = TaskWhitelist.getTaskDefinition(action)
            assertNotNull("任务 $action 应存在", t)
        }
    }

    @Test
    fun `P3_TASKS - file_read 属于 FILE_OPERATION category`() {
        val t = TaskWhitelist.getTaskDefinition("file_read")
        assertEquals(TaskWhitelist.TaskCategory.FILE_OPERATION, t!!.category)
    }

    // ── 唯一性 ──

    @Test
    fun `所有白名单任务 action 唯一 (无重复)`() {
        val all = TaskWhitelist.getAllWhitelistedTasks()
        val actions = all.map { it.action }
        val distinctActions = actions.toSet()
        assertEquals("白名单中存在重复 action: ${actions.groupBy { it }.filter { it.value.size > 1 }.keys}", actions.size, distinctActions.size)
    }

    @Test
    fun `所有白名单任务 maxExecutionTimeMs 为正数`() {
        for (t in TaskWhitelist.getAllWhitelistedTasks()) {
            assertTrue(
                "任务 '${t.action}' maxExecutionTimeMs 应 > 0，实际 ${t.maxExecutionTimeMs}",
                t.maxExecutionTimeMs > 0,
            )
        }
    }

    @Test
    fun `所有白名单任务 description 非空`() {
        for (t in TaskWhitelist.getAllWhitelistedTasks()) {
            assertTrue("任务 '${t.action}' description 应非空", t.description.isNotBlank())
        }
    }

    @Test
    fun `所有 requiresConfirmation=true 任务 风险 ≥ MEDIUM (防御)`() {
        for (t in TaskWhitelist.getAllWhitelistedTasks()) {
            if (t.requiresConfirmation) {
                assertTrue(
                    "任务 '${t.action}' 需确认但风险等级为 LOW，应至少 MEDIUM",
                    t.riskLevel == TaskWhitelist.RiskLevel.MEDIUM ||
                        t.riskLevel == TaskWhitelist.RiskLevel.HIGH ||
                        t.riskLevel == TaskWhitelist.RiskLevel.CRITICAL,
                )
            }
        }
    }

    // ── BLOCKED_ACTIONS ──

    @Test
    fun `BLOCKED_ACTIONS 包含支付类动作`() {
        for (action in listOf("payment_confirm", "payment_fill_password", "payment_transfer", "payment_scan")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含敏感信息读取类`() {
        for (action in listOf("read_sms", "read_sms_code", "read_bank_info", "read_chat_history", "read_contacts", "read_call_log")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含系统级修改类`() {
        for (action in listOf("modify_system_partition", "flash_device", "root_device", "uninstall_system_app", "modify_security_settings")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含批量操作类`() {
        for (action in listOf("batch_delete_contacts", "batch_uninstall_apps", "batch_send_messages")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含未知来源安装类`() {
        for (action in listOf("install_apk", "enable_unknown_sources", "modify_install_sources")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含远程控制类`() {
        for (action in listOf("auto_answer_call", "auto_send_sms", "auto_make_call", "auto_record_call")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 包含高风险系统操作类`() {
        for (action in listOf("factory_reset", "wipe_data", "enable_usb_debugging", "modify_developer_options")) {
            assertTrue("BLOCKED 应包含 $action", TaskWhitelist.isBlocked(action))
        }
    }

    @Test
    fun `BLOCKED_ACTIONS 数量稳定 (业务契约 29 个)`() {
        // 防止增删 BLOCKED 时静默修改业务红线
        assertEquals(29, TaskWhitelist.BLOCKED_ACTIONS.size)
    }

    @Test
    fun `BLOCKED 与 whitelist 不应重叠 (互斥契约)`() {
        for (blocked in TaskWhitelist.BLOCKED_ACTIONS) {
            assertFalse(
                "BLOCKED 任务 '$blocked' 不应同时在白名单中",
                TaskWhitelist.isWhitelisted(blocked),
            )
        }
    }

    // ── isWhitelisted ──

    @Test
    fun `isWhitelisted - 白名单中的动作返回 true`() {
        for (t in TaskWhitelist.getAllWhitelistedTasks()) {
            assertTrue("白名单动作 '${t.action}' 应返回 true", TaskWhitelist.isWhitelisted(t.action))
        }
    }

    @Test
    fun `isWhitelisted - 未知动作返回 false`() {
        assertFalse(TaskWhitelist.isWhitelisted("unknown_action"))
        assertFalse(TaskWhitelist.isWhitelisted(""))
        assertFalse(TaskWhitelist.isWhitelisted("query_unknown"))
    }

    @Test
    fun `isWhitelisted - case sensitive (业务契约)`() {
        // 大小写敏感：白名单用 snake_case，未转换的驼峰不应通过
        assertFalse(TaskWhitelist.isWhitelisted("QUERY_BATTERY_LEVEL"))
        assertFalse(TaskWhitelist.isWhitelisted("Query_Battery_Level"))
    }

    // ── isBlocked ──

    @Test
    fun `isBlocked - 白名单动作返回 false`() {
        assertFalse(TaskWhitelist.isBlocked("query_battery_level"))
        assertFalse(TaskWhitelist.isBlocked("ui_swipe"))
        assertFalse(TaskWhitelist.isBlocked("input_text"))
    }

    @Test
    fun `isBlocked - 未知动作返回 false (不是 blocked 而是未授权)`() {
        assertFalse(TaskWhitelist.isBlocked("unknown_action"))
    }

    // ── getTaskDefinition ──

    @Test
    fun `getTaskDefinition - 已知动作返回完整 WhitelistTask`() {
        val t = TaskWhitelist.getTaskDefinition("query_battery_level")
        assertNotNull(t)
        assertEquals("query_battery_level", t!!.action)
        assertEquals(TaskWhitelist.TaskCategory.INFO_QUERY, t.category)
        assertEquals(TaskWhitelist.RiskLevel.LOW, t.riskLevel)
    }

    @Test
    fun `getTaskDefinition - 未知动作返回 null`() {
        assertNull(TaskWhitelist.getTaskDefinition("nonexistent"))
        assertNull(TaskWhitelist.getTaskDefinition(""))
    }

    @Test
    fun `getTaskDefinition - BLOCKED 动作返回 null (不在白名单)`() {
        // 重要：getTaskDefinition 只查白名单，BLOCKED 不属于白名单
        assertNull(TaskWhitelist.getTaskDefinition("payment_confirm"))
        assertNull(TaskWhitelist.getTaskDefinition("factory_reset"))
    }

    // ── validateTask ──

    @Test
    fun `validateTask - 白名单中的合法动作 返回 (true 通过)`() {
        val (ok, reason) = TaskWhitelist.validateTask("query_battery_level")
        assertTrue("合法动作应返回 ok=true", ok)
        assertTrue("reason 应包含通过字样，实际：$reason", reason.contains("通过"))
    }

    @Test
    fun `validateTask - BLOCKED 动作 返回 false 原因含 禁止`() {
        val (ok, reason) = TaskWhitelist.validateTask("payment_confirm")
        assertFalse("BLOCKED 动作应返回 ok=false", ok)
        assertTrue("reason 应包含禁止字样，实际：$reason", reason.contains("禁止"))
    }

    @Test
    fun `validateTask - BLOCKED 优先级高于白名单 (防御)`() {
        // 即便某 BLOCKED 名字混入白名单，validateTask 应先报 BLOCKED
        // 用实际 BLOCKED 动作验证 (虽然 validateTask 已通过 isBlocked 先检查)
        val (ok, _) = TaskWhitelist.validateTask("factory_reset")
        assertFalse(ok)
    }

    @Test
    fun `validateTask - 未知动作 返回 false 原因含 不在白名单`() {
        val (ok, reason) = TaskWhitelist.validateTask("totally_unknown")
        assertFalse("未知动作应返回 ok=false", ok)
        assertTrue("reason 应包含 不在白名单，实际：$reason", reason.contains("不在白名单"))
    }

    @Test
    fun `validateTask - 空字符串 返回 false (视作未知)`() {
        val (ok, _) = TaskWhitelist.validateTask("")
        assertFalse(ok)
    }

    // ── getAllWhitelistedTasks ──

    @Test
    fun `getAllWhitelistedTasks - 包含 P0 P1 P2 P3 所有任务 无遗漏`() {
        val all = TaskWhitelist.getAllWhitelistedTasks()
        assertEquals(
            TaskWhitelist.P0_TASKS.size + TaskWhitelist.P1_TASKS.size +
                TaskWhitelist.P2_TASKS.size + TaskWhitelist.P3_TASKS.size,
            all.size,
        )
    }

    // ── WhitelistTask 数据类 ──

    @Test
    fun `WhitelistTask - 数据类 equals 基于所有字段`() {
        val a = TaskWhitelist.WhitelistTask(
            action = "x", category = TaskWhitelist.TaskCategory.INFO_QUERY,
            riskLevel = TaskWhitelist.RiskLevel.LOW, requiresConfirmation = false,
            description = "d", parameters = listOf("p"), maxExecutionTimeMs = 1000L,
        )
        val b = a.copy()
        assertEquals(a, b)
        val c = a.copy(maxExecutionTimeMs = 2000L)
        assertFalse(a == c)
    }

    // ── 参数一致性 ──

    @Test
    fun `set_brightness 参数名 level 与描述一致`() {
        val t = TaskWhitelist.getTaskDefinition("set_brightness")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("level"))
    }

    @Test
    fun `set_volume 参数 type 和 level 都在`() {
        val t = TaskWhitelist.getTaskDefinition("set_volume")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("type"))
        assertTrue(t.parameters.contains("level"))
    }

    @Test
    fun `ui_tap 参数 x 和 y 都在`() {
        val t = TaskWhitelist.getTaskDefinition("ui_tap")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("x"))
        assertTrue(t.parameters.contains("y"))
    }

    @Test
    fun `query_volume 参数 type 存在`() {
        val t = TaskWhitelist.getTaskDefinition("query_volume")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("type"))
    }

    @Test
    fun `query_storage_space 参数 type 存在`() {
        val t = TaskWhitelist.getTaskDefinition("query_storage_space")
        assertNotNull(t)
        assertTrue(t!!.parameters.contains("type"))
    }

    // ── 风险递增合理性 ──

    @Test
    fun `风险等级 code 严格递增 (防御)`() {
        val codes = TaskWhitelist.RiskLevel.values().map { it.code }
        assertEquals(listOf(0, 1, 2, 3), codes)
    }

    @Test
    fun `RiskLevel 各自 description 非空 (UI 契约)`() {
        for (level in TaskWhitelist.RiskLevel.values()) {
            assertTrue("RiskLevel.${level.name} description 应非空", level.description.isNotBlank())
        }
    }

    @Test
    fun `TaskCategory 各自 description 非空 (UI 契约)`() {
        for (cat in TaskWhitelist.TaskCategory.values()) {
            assertTrue("TaskCategory.${cat.name} description 应非空", cat.description.isNotBlank())
        }
    }
}