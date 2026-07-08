package io.agents.pokeclaw.vision

/**
 * Derives control nodes + weak-label detection boxes from the accessibility full
 * tree (`ClawAccessibilityService.getScreenTreeFull()`).
 *
 * This is (a) the on-device label generator for collected samples and (b) the
 * fallback "detector" used when no trained YOLO weight is loaded. It is pure — no
 * Android dependency — so it runs and is verified off-device.
 *
 * Per-node line format emitted by the service:
 * ```
 * [ClassSimple] text="..." desc="..." id="res-id" pkg="pkg" [clickable] [editable] ... bounds=[l,t][r,b]
 * ```
 */
object WeakLabelDetector {
    private val CLASS_RE = Regex("""^\s*\[([A-Za-z0-9_$]+)]""")
    private val BOUNDS_RE = Regex("""bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
    private val TEXT_RE = Regex("""(?<![A-Za-z])text="([^"]*)"""")
    private val ID_RE = Regex("""\sid="([^"]*)"""")
    private val PKG_RE = Regex("""\spkg="([^"]*)"""")

    fun parseNodes(tree: String?): List<UiNode> {
        if (tree.isNullOrBlank()) return emptyList()
        val nodes = ArrayList<UiNode>()
        for (line in tree.lineSequence()) {
            val cls = CLASS_RE.find(line)?.groupValues?.get(1) ?: continue
            val b = BOUNDS_RE.find(line) ?: continue
            val l = b.groupValues[1].toInt()
            val t = b.groupValues[2].toInt()
            val r = b.groupValues[3].toInt()
            val bo = b.groupValues[4].toInt()
            if (r <= l || bo <= t) continue          // skip zero-area
            if (line.contains("[invisible]")) continue
            val clickable = line.contains("[clickable]")
            val editable = line.contains("[editable]")
            val scrollable = line.contains("[scrollable]")
            val checkable = line.contains("[checked]") || line.contains("[unchecked]")
            nodes.add(
                UiNode(
                    className = cls,
                    role = AndroidRoleMapper.coarseRole(cls, clickable, editable, checkable),
                    fineRole = AndroidRoleMapper.fineRole(cls, clickable, editable, checkable, scrollable),
                    text = TEXT_RE.find(line)?.groupValues?.get(1),
                    resourceId = ID_RE.find(line)?.groupValues?.get(1),
                    pkg = PKG_RE.find(line)?.groupValues?.get(1),
                    left = l, top = t, right = r, bottom = bo,
                    clickable = clickable, editable = editable,
                    scrollable = scrollable, checkable = checkable,
                )
            )
        }
        return nodes
    }

    /** Weak-label detection boxes for a screen of the given pixel size. */
    fun detect(tree: String?, screenW: Int, screenH: Int): List<DetectionBox> =
        parseNodes(tree).map { it.toBox(screenW, screenH) }
}
