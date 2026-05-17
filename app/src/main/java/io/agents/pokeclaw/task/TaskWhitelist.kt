// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// 手机控制任务白名单定义 — 对齐 task-whitelist-and-error-handling-spec.md

package io.agents.pokeclaw.task

/**
 * 任务白名单定义。
 *
 * 设计原则：
 * - 低风险优先：优先支持只读、查询类操作
 * - 云端可控：任务执行前必须经云端指令确认
 * - 可回滚：支持的操作应有明确取消/回退路径
 */
object TaskWhitelist {

    /**
     * 任务风险等级
     */
    enum class RiskLevel(val code: Int, val description: String) {
        LOW(0, "低风险"),
        MEDIUM(1, "中等风险"),
        HIGH(2, "高风险"),
        CRITICAL(3, "极高风险")
    }

    /**
     * 任务类别
     */
    enum class TaskCategory(val description: String) {
        INFO_QUERY("信息查询"),
        SYSTEM_SETTING_QUERY("系统设置查询"),
        UI_OPERATION("UI操作"),
        APP_CONTROL("应用控制"),
        INPUT_OPERATION("输入操作"),
        SYSTEM_SETTING_MODIFY("系统设置修改"),
        FILE_OPERATION("文件操作")
    }

    /**
     * 白名单任务定义
     */
    data class WhitelistTask(
        val action: String,              // 动作标识
        val category: TaskCategory,      // 任务类别
        val riskLevel: RiskLevel,        // 风险等级
        val requiresConfirmation: Boolean,  // 是否需要用户确认
        val description: String,         // 描述
        val parameters: List<String>,    // 支持的参数列表
        val maxExecutionTimeMs: Long     // 最大执行时间（毫秒）
    )

    /**
     * P0 级白名单任务（低风险，无需确认）
     */
    val P0_TASKS = listOf(
        // 信息查询类
        WhitelistTask(
            action = "query_battery_level",
            category = TaskCategory.INFO_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询设备电量百分比",
            parameters = listOf(),
            maxExecutionTimeMs = 5000
        ),
        WhitelistTask(
            action = "query_storage_space",
            category = TaskCategory.INFO_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询设备存储空间（可用/总空间）",
            parameters = listOf("type"),  // type: internal|external|all
            maxExecutionTimeMs = 5000
        ),
        WhitelistTask(
            action = "query_network_status",
            category = TaskCategory.INFO_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询网络连接状态",
            parameters = listOf(),
            maxExecutionTimeMs = 3000
        ),
        WhitelistTask(
            action = "query_installed_apps",
            category = TaskCategory.INFO_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询已安装应用列表",
            parameters = listOf("includeSystem"),  // includeSystem: true|false
            maxExecutionTimeMs = 10000
        ),
        WhitelistTask(
            action = "query_device_info",
            category = TaskCategory.INFO_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询设备基本信息（型号、版本、屏幕等）",
            parameters = listOf(),
            maxExecutionTimeMs = 3000
        ),

        // 系统设置查询类
        WhitelistTask(
            action = "query_brightness",
            category = TaskCategory.SYSTEM_SETTING_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询屏幕亮度",
            parameters = listOf(),
            maxExecutionTimeMs = 3000
        ),
        WhitelistTask(
            action = "query_volume",
            category = TaskCategory.SYSTEM_SETTING_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询音量设置",
            parameters = listOf("type"),  // type: ring|media|alarm|all
            maxExecutionTimeMs = 3000
        ),
        WhitelistTask(
            action = "query_wifi_status",
            category = TaskCategory.SYSTEM_SETTING_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询 WiFi 连接状态",
            parameters = listOf(),
            maxExecutionTimeMs = 3000
        ),
        WhitelistTask(
            action = "query_bluetooth_status",
            category = TaskCategory.SYSTEM_SETTING_QUERY,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "查询蓝牙状态",
            parameters = listOf(),
            maxExecutionTimeMs = 3000
        )
    )

    /**
     * P1 级白名单任务（中等风险，建议确认）
     */
    val P1_TASKS = listOf(
        // UI 操作类
        WhitelistTask(
            action = "ui_swipe",
            category = TaskCategory.UI_OPERATION,
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            description = "滑动屏幕",
            parameters = listOf("direction", "distance"),  // direction: up|down|left|right
            maxExecutionTimeMs = 5000
        ),
        WhitelistTask(
            action = "ui_tap",
            category = TaskCategory.UI_OPERATION,
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            description = "点击指定坐标",
            parameters = listOf("x", "y"),
            maxExecutionTimeMs = 5000
        ),
        WhitelistTask(
            action = "ui_press_back",
            category = TaskCategory.UI_OPERATION,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "按返回键",
            parameters = listOf(),
            maxExecutionTimeMs = 2000
        ),
        WhitelistTask(
            action = "ui_press_home",
            category = TaskCategory.UI_OPERATION,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "按主页键",
            parameters = listOf(),
            maxExecutionTimeMs = 2000
        ),
        WhitelistTask(
            action = "ui_press_recent",
            category = TaskCategory.UI_OPERATION,
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            description = "按最近任务键",
            parameters = listOf(),
            maxExecutionTimeMs = 2000
        ),

        // 应用控制类
        WhitelistTask(
            action = "app_launch",
            category = TaskCategory.APP_CONTROL,
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            description = "启动指定应用",
            parameters = listOf("packageName"),
            maxExecutionTimeMs = 10000
        ),
        WhitelistTask(
            action = "app_switch",
            category = TaskCategory.APP_CONTROL,
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            description = "切换到指定后台应用",
            parameters = listOf("packageName"),
            maxExecutionTimeMs = 5000
        )
    )

    /**
     * P2 级白名单任务（中等风险，必须确认）
     */
    val P2_TASKS = listOf(
        WhitelistTask(
            action = "input_text",
            category = TaskCategory.INPUT_OPERATION,
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            description = "在已聚焦输入框中输入文本",
            parameters = listOf("text"),
            maxExecutionTimeMs = 5000
        )
    )

    /**
     * P3 级白名单任务（较高风险，必须确认）
     */
    val P3_TASKS = listOf(
        // 系统设置修改（限范围）
        WhitelistTask(
            action = "set_brightness",
            category = TaskCategory.SYSTEM_SETTING_MODIFY,
            riskLevel = RiskLevel.HIGH,
            requiresConfirmation = true,
            description = "调整屏幕亮度（范围限制）",
            parameters = listOf("level"),  // level: 10-100
            maxExecutionTimeMs = 5000
        ),
        WhitelistTask(
            action = "set_volume",
            category = TaskCategory.SYSTEM_SETTING_MODIFY,
            riskLevel = RiskLevel.HIGH,
            requiresConfirmation = true,
            description = "调整音量（范围限制）",
            parameters = listOf("type", "level"),  // type: ring|media|alarm, level: 0-100
            maxExecutionTimeMs = 5000
        ),

        // 文件操作（限沙箱内）
        WhitelistTask(
            action = "file_read",
            category = TaskCategory.FILE_OPERATION,
            riskLevel = RiskLevel.HIGH,
            requiresConfirmation = true,
            description = "读取指定路径文件内容（限应用沙箱内）",
            parameters = listOf("path"),
            maxExecutionTimeMs = 10000
        )
    )

    /**
     * 禁止任务列表（红线）
     */
    val BLOCKED_ACTIONS = setOf(
        // 支付类
        "payment_confirm",
        "payment_fill_password",
        "payment_transfer",
        "payment_scan",

        // 敏感信息类
        "read_sms",
        "read_sms_code",
        "read_bank_info",
        "read_chat_history",
        "read_contacts",
        "read_call_log",

        // 系统级修改
        "modify_system_partition",
        "flash_device",
        "root_device",
        "uninstall_system_app",
        "modify_security_settings",

        // 批量操作
        "batch_delete_contacts",
        "batch_uninstall_apps",
        "batch_send_messages",

        // 未知来源
        "install_apk",
        "enable_unknown_sources",
        "modify_install_sources",

        // 远程控制
        "auto_answer_call",
        "auto_send_sms",
        "auto_make_call",
        "auto_record_call",

        // 高风险操作
        "factory_reset",
        "wipe_data",
        "enable_usb_debugging",
        "modify_developer_options"
    )

    /**
     * 获取所有白名单任务
     */
    fun getAllWhitelistedTasks(): List<WhitelistTask> {
        return P0_TASKS + P1_TASKS + P2_TASKS + P3_TASKS
    }

    /**
     * 检查任务是否在白名单中
     */
    fun isWhitelisted(action: String): Boolean {
        return getAllWhitelistedTasks().any { it.action == action }
    }

    /**
     * 获取任务定义
     */
    fun getTaskDefinition(action: String): WhitelistTask? {
        return getAllWhitelistedTasks().find { it.action == action }
    }

    /**
     * 检查任务是否在禁止列表中
     */
    fun isBlocked(action: String): Boolean {
        return BLOCKED_ACTIONS.contains(action)
    }

    /**
     * 验证任务是否可执行
     *
     * @return Pair<是否可执行, 原因>
     */
    fun validateTask(action: String): Pair<Boolean, String> {
        if (isBlocked(action)) {
            return Pair(false, "任务 '$action' 在禁止列表中，无法执行")
        }
        if (!isWhitelisted(action)) {
            return Pair(false, "任务 '$action' 不在白名单中，无法执行")
        }
        return Pair(true, "任务验证通过")
    }
}
