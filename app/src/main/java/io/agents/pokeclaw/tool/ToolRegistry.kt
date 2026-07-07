// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

import io.agents.pokeclaw.agent.knowledge.*
import io.agents.pokeclaw.reliability.action.ActionValidator
import io.agents.pokeclaw.reliability.action.ReliableAction
import io.agents.pokeclaw.reliability.action.ReliableActionResult
import io.agents.pokeclaw.reliability.trace.ExecutionTrace
import io.agents.pokeclaw.tool.impl.*
import io.agents.pokeclaw.tool.impl.mobile.*
import io.agents.pokeclaw.tool.impl.tv.*

object ToolRegistry {

    enum class DeviceType { TV, MOBILE }

    private val tools = LinkedHashMap<String, BaseTool>()
    var deviceType: DeviceType = DeviceType.TV
        private set

    @JvmStatic
    fun getInstance(): ToolRegistry = this

    fun registerAllTools(type: DeviceType = DeviceType.TV) {
        deviceType = type
        tools.clear()
        registerCommonTools()
        when (type) {
            DeviceType.TV -> registerTvTools()
            DeviceType.MOBILE -> registerMobileTools()
        }
    }

    private fun registerCommonTools() {
        register(GetScreenInfoTool())
        register(FindNodeInfoTool())
        register(InputTextTool())
        register(SystemKeyTool())
        register(OpenAppTool())
        register(GetInstalledAppsTool())
        register(TakeScreenshotTool())
        register(WaitTool())
        register(RepeatActionsTool())
        register(ClipboardTool())
        register(SendFileTool())
        register(GetDeviceInfoTool())
        register(GetNotificationsTool())
        register(MakeCallTool())
        register(FinishTool())
        // Knowledge Base tools — shared vault available in all modes
        register(KbWriteTool())
        register(KbReadTool())
        register(KbSearchTool())
        register(KbAppendTool())
        register(KbAddTodoTool())
    }

    private fun registerTvTools() {
        register(DpadUpTool())
        register(DpadDownTool())
        register(DpadLeftTool())
        register(DpadRightTool())
        register(DpadCenterTool())
        register(VolumeUpTool())
        register(VolumeDownTool())
        register(PressMenuTool())
        register(PressPowerTool())
    }

    private fun registerMobileTools() {
        register(TapTool())
        register(TapNodeTool())
        register(LongPressTool())
        register(SwipeTool())
        register(ScrollToFindTool())
        register(FindAndTapTool())
        register(SendMessageTool())
        register(AutoReplyTool())
    }

    fun register(tool: BaseTool) {
        tools[tool.getName()] = tool
    }

    fun getTool(name: String): BaseTool? = tools[name]

    fun getDisplayName(name: String): String = tools[name]?.getDisplayName() ?: name

    fun getAllTools(): List<BaseTool> = tools.values.toList()

    fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        // Reliability stage 1: describe the action, validate it, and trace the whole execution.
        // A hard-invalid action (unknown tool / missing required param / bad wait_after) is
        // blocked here so the real tool is never called with a malformed action.
        val action = ReliableAction.fromToolCall(name, params)
        val tool = tools[name]
        val validation = ActionValidator.validate(action, tool)
        ExecutionTrace.recordValidation(action, validation.isValid, validation.message, validation.errorType)
        if (!validation.isValid) {
            io.agents.pokeclaw.utils.XLog.w("ToolRegistry", "Reliable action validation blocked '${action.toolName}': ${validation.message}")
            val result = ToolResult.error(validation.message, validation.errorType)
            ExecutionTrace.recordResult(ReliableActionResult.fromToolResult(action, result, action.createdAtMs))
            return result
        }

        val startedAtMs = System.currentTimeMillis()
        ExecutionTrace.recordExecutionStart(action)
        val result = try {
            tool!!.executeWithWaitAfter(params)
        } catch (e: Exception) {
            io.agents.pokeclaw.utils.XLog.e("ToolRegistry", "Tool '$name' execution failed with params=$params", e)
            ToolResult.error("Tool execution failed: ${e.message}", ToolResult.ErrorType.TOOL_EXCEPTION)
        }
        ExecutionTrace.recordResult(ReliableActionResult.fromToolResult(action, result, startedAtMs))
        return result
    }
}
