package io.agents.pokeclaw.reliability.action

import io.agents.pokeclaw.tool.BaseTool
import io.agents.pokeclaw.tool.ToolParameter
import io.agents.pokeclaw.tool.ToolRegistry
import io.agents.pokeclaw.tool.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    @Test
    fun validateMissingRequiredParameterBlocksAction() {
        val action = ReliableAction.fromToolCall("fake_required", emptyMap())
        val result = ActionValidator.validate(action, FakeTool("fake_required"))

        assertFalse(result.isValid)
        assertEquals(ToolResult.ErrorType.INVALID_ACTION, result.errorType)
        assertTrue(result.message.contains("missing required"))
    }

    @Test
    fun executeToolDoesNotCallRealToolWhenActionInvalid() {
        val tool = FakeTool("fake_reliable_block")
        ToolRegistry.register(tool)

        val result = ToolRegistry.executeTool("fake_reliable_block", emptyMap())

        assertFalse(result.isSuccess)
        assertEquals(ToolResult.ErrorType.INVALID_ACTION, result.errorType)
        assertEquals(0, tool.executeCount)
    }

    @Test
    fun executeToolClassifiesUnknownTool() {
        val result = ToolRegistry.executeTool("fake_missing_tool_for_reliability", emptyMap())

        assertFalse(result.isSuccess)
        assertEquals(ToolResult.ErrorType.UNKNOWN_TOOL, result.errorType)
    }

    @Test
    fun executeToolRecordsSuccessWhenActionValid() {
        val tool = FakeTool("fake_reliable_success")
        ToolRegistry.register(tool)

        val result = ToolRegistry.executeTool("fake_reliable_success", mapOf("text" to "hello"))

        assertTrue(result.isSuccess)
        assertEquals("ok", result.data)
        assertEquals(1, tool.executeCount)
    }

    private class FakeTool(private val name: String) : BaseTool() {
        var executeCount: Int = 0

        override fun getName(): String = name

        override fun getParameters(): List<ToolParameter> = listOf(
            ToolParameter("text", "string", "required text", true)
        )

        override fun execute(params: Map<String, Any>): ToolResult {
            executeCount++
            return ToolResult.success("ok")
        }

        override fun getDescriptionEN(): String = "fake"
        override fun getDescriptionCN(): String = "测试"
    }
}
