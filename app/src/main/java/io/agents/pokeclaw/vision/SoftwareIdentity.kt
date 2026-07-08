package io.agents.pokeclaw.vision

/**
 * Signals used to identify the current target software and route its YOLO model.
 * Any subset may be present (packageName is the strongest signal).
 */
data class SoftwareIdentity(
    val packageName: String? = null,
    val activity: String? = null,
    val windowTitle: String? = null,
    val taskGoal: String? = null,
    val category: String? = null,
) {
    /** The software_key used cloud-side; package name is the convention. */
    fun softwareKey(): String? = packageName?.takeIf { it.isNotBlank() }

    fun isEmpty(): Boolean =
        packageName.isNullOrBlank() && windowTitle.isNullOrBlank() && taskGoal.isNullOrBlank()
}
