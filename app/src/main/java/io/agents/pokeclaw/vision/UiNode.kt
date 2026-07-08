package io.agents.pokeclaw.vision

/**
 * A parsed UI control node from the accessibility full tree
 * (`ClawAccessibilityService.getScreenTreeFull()`). Bounds are pixels.
 */
data class UiNode(
    val className: String,
    val role: String,      // coarse gxe role: interactable | icon | text | input | image
    val fineRole: String,  // richer: button | edittext | tab | checkbox | switch | image | icon | text | list | container
    val text: String? = null,
    val resourceId: String? = null,
    val pkg: String? = null,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** Worth acting on during exploration. */
    val actionable: Boolean
        get() = clickable || editable || role == AndroidRoleMapper.INTERACTABLE ||
            role == AndroidRoleMapper.INPUT || fineRole == "tab"

    fun toBox(screenW: Int, screenH: Int): DetectionBox {
        val sw = if (screenW > 0) screenW.toFloat() else 1f
        val sh = if (screenH > 0) screenH.toFloat() else 1f
        return DetectionBox(
            cls = role,
            cx = (left + right) / 2f / sw,
            cy = (top + bottom) / 2f / sh,
            w = (right - left) / sw,
            h = (bottom - top) / sh,
            conf = 1f,
            source = DetectionBox.SOURCE_WEAK,
        )
    }
}
