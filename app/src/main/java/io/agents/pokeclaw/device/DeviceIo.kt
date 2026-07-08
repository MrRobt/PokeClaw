package io.agents.pokeclaw.device

import io.agents.pokeclaw.explore.ExploreAction

/** Raw capture of the current screen (screenshot + full UI tree + foreground app). */
data class DeviceObservation(
    val pkg: String?,
    val activity: String?,
    val uiXml: String?,          // ClawAccessibilityService.getScreenTreeFull() dump
    val screenshotB64: String?,
    val width: Int,
    val height: Int,
) {
    companion object {
        val EMPTY = DeviceObservation(null, null, null, null, 0, 0)
    }
}

/** Observes the current screen — local (accessibility) or remote (cloud phone). */
interface DeviceObserver {
    fun observe(captureScreenshot: Boolean = true): DeviceObservation
}

/** Executes actions on a device — local (accessibility) or remote (cloud phone). */
interface DeviceActuator {
    fun tap(x: Int, y: Int): String
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 400): String
    fun input(text: String): String
    fun back(): String
    fun home(): String
    fun launch(pkg: String): String

    /** Dispatch an [ExploreAction]; returns a result token: ok | changed | no_change | error. */
    fun perform(action: ExploreAction): String = when (action.type) {
        "tap" -> tap(action.x, action.y)
        "input" -> input(action.inputText ?: "pokeclaw")
        "swipe", "scroll" -> swipe(action.x, action.y, action.x2, action.y2)
        "back" -> back()
        "home" -> home()
        "launch" -> launch(action.targetText ?: "")
        else -> "no_change"
    }
}
