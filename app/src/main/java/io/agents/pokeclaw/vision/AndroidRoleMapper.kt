package io.agents.pokeclaw.vision

/**
 * Maps an Android widget class + interaction flags to a coarse gxe role and a
 * richer fine role. The coarse roles are exactly gxe's `schema.ROLES`
 * (interactable/icon/text/input/image) so on-device weak labels line up with the
 * generic-GUI YOLO model's label space.
 */
object AndroidRoleMapper {
    const val INTERACTABLE = "interactable"
    const val ICON = "icon"
    const val TEXT = "text"
    const val INPUT = "input"
    const val IMAGE = "image"

    fun coarseRole(className: String, clickable: Boolean, editable: Boolean, checkable: Boolean): String {
        val c = className.substringAfterLast('.').lowercase()
        return when {
            editable || c.contains("edittext") || c.contains("autocomplete") || c.contains("textinput") -> INPUT
            c.contains("imagebutton") -> ICON
            c.contains("imageview") || c.contains("image") -> if (clickable) ICON else IMAGE
            c.contains("button") || c.contains("checkbox") || c.contains("switch") ||
                c.contains("radio") || c.contains("tab") || c.contains("chip") || checkable -> INTERACTABLE
            c.contains("textview") || c.contains("text") -> if (clickable) INTERACTABLE else TEXT
            clickable -> INTERACTABLE
            else -> TEXT
        }
    }

    fun fineRole(
        className: String,
        clickable: Boolean,
        editable: Boolean,
        checkable: Boolean,
        scrollable: Boolean,
    ): String {
        val c = className.substringAfterLast('.').lowercase()
        return when {
            editable || c.contains("edittext") || c.contains("textinput") -> "edittext"
            c.contains("tablayout") || c.contains("tabitem") || c.contains("tab") -> "tab"
            c.contains("switch") -> "switch"
            c.contains("checkbox") || (checkable && c.contains("check")) -> "checkbox"
            c.contains("radio") -> "radio"
            c.contains("imagebutton") -> "icon"
            c.contains("button") -> "button"
            c.contains("imageview") -> "image"
            c.contains("recyclerview") || c.contains("listview") ||
                c.contains("scrollview") || scrollable -> "list"
            c.contains("textview") -> "text"
            clickable -> "button"
            else -> "container"
        }
    }
}
