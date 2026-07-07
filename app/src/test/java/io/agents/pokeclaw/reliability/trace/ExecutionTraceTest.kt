package io.agents.pokeclaw.reliability.trace

import io.agents.pokeclaw.reliability.action.ReliableAction
import io.agents.pokeclaw.reliability.action.ReliableActionResult
import io.agents.pokeclaw.tool.ToolResult
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionTraceTest {

    @Test
    fun traceRecordsLifecycleAndSummarizesFailures() {
        ExecutionTrace.startTask("demo task", "msg-1")

        val ok = ReliableAction.fromToolCall("tap", mapOf("x" to "1", "y" to "2"))
        ExecutionTrace.recordValidation(ok, true, "OK", ToolResult.ErrorType.NONE)
        ExecutionTrace.recordExecutionStart(ok)
        ExecutionTrace.recordResult(
            ReliableActionResult.fromToolResult(ok, ToolResult.success("done"), System.currentTimeMillis())
        )

        val bad = ReliableAction.fromToolCall("open_app", mapOf("name" to "nope"))
        ExecutionTrace.recordValidation(bad, true, "OK", ToolResult.ErrorType.NONE)
        ExecutionTrace.recordResult(
            ReliableActionResult.fromToolResult(bad, ToolResult.error("not found"), System.currentTimeMillis())
        )

        ExecutionTrace.finishTask("failed", "one action failed")

        val snapshot = ExecutionTrace.snapshot()
        assertTrue(snapshot.any { it.phase == ExecutionTrace.Phase.TASK_START })
        assertTrue(snapshot.any { it.phase == ExecutionTrace.Phase.TASK_END })

        val summary = ExecutionTrace.summary()
        assertTrue("summary should count 2 actions: $summary", summary.contains("actions=2"))
        assertTrue("summary should count 1 failure: $summary", summary.contains("failed=1"))
    }
}
