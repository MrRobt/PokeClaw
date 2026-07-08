package io.agents.pokeclaw.device

import io.agents.pokeclaw.tool.ToolRegistry
import io.agents.pokeclaw.tool.ToolResult
import io.agents.pokeclaw.utils.XLog

/**
 * Local actuator: drives the on-device screen through the existing tool harness
 * (accessibility gestures + input + global actions), so every action still flows
 * through reliability validation + the execution trace.
 */
class AccessibilityActuator : DeviceActuator {

    private fun run(name: String, params: Map<String, Any>): String {
        val r: ToolResult = ToolRegistry.getInstance().executeTool(name, params)
        XLog.d(TAG, "$name($params) -> ${if (r.isSuccess) "ok" else "error:${r.error}"}")
        return if (r.isSuccess) "ok" else "error"
    }

    override fun tap(x: Int, y: Int) = run("tap", mapOf("x" to x, "y" to y))

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) =
        run("swipe", mapOf("start_x" to x1, "start_y" to y1, "end_x" to x2, "end_y" to y2, "duration_ms" to durationMs.toInt()))

    override fun input(text: String) = run("input_text", mapOf("text" to text))

    override fun back() = run("system_key", mapOf("key" to "back"))

    override fun home() = run("system_key", mapOf("key" to "home"))

    override fun launch(pkg: String) = run("open_app", mapOf("package_name" to pkg))

    companion object {
        private const val TAG = "PokeClaw/A11yActuator"
    }
}
