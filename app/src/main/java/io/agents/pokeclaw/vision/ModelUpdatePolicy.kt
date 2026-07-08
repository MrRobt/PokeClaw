package io.agents.pokeclaw.vision

/**
 * Decides whether to download/replace the on-device cached model. Honors the rule
 * "don't switch models mid-task" — an update while a task runs is deferred so the
 * next task picks it up.
 */
object ModelUpdatePolicy {
    data class Decision(val shouldDownload: Boolean, val reason: String)

    /**
     * @param cachedVersion cached version for this software (0 if none)
     * @param cachedChecksum checksum of the cached artifact (null if none)
     * @param descriptor freshly resolved descriptor
     * @param taskRunning whether a task is currently executing
     */
    fun decide(
        cachedVersion: Int,
        cachedChecksum: String?,
        descriptor: YoloModelDescriptor,
        taskRunning: Boolean,
    ): Decision {
        if (!descriptor.usable) return Decision(false, "no usable model")
        if (cachedChecksum == null || cachedVersion <= 0) {
            return if (taskRunning) Decision(false, "task running — defer first fetch")
            else Decision(true, "not cached")
        }
        val newer = descriptor.version > cachedVersion
        val changed = descriptor.checksum != null && descriptor.checksum != cachedChecksum
        if (!newer && !changed) return Decision(false, "up to date")
        if (taskRunning) return Decision(false, "task running — defer to next task")
        return Decision(true, if (newer) "new active version ${descriptor.version}" else "artifact changed")
    }
}
