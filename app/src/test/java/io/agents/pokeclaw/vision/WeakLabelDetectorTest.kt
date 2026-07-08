package io.agents.pokeclaw.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a representative ClawAccessibilityService.getScreenTreeFull() dump. */
class WeakLabelDetectorTest {

    private val tree = """
        [FrameLayout] pkg="io.claw.app" bounds=[0,0][1080,2400]
          [Button] text="Login" id="io.claw.app:id/login_btn" pkg="io.claw.app" [clickable] bounds=[100,200][400,300]
          [EditText] id="io.claw.app:id/username" pkg="io.claw.app" [editable] bounds=[100,400][980,500]
          [ImageView] desc="avatar" pkg="io.claw.app" [clickable] bounds=[900,50][980,130]
          [TextView] text="Welcome back" pkg="io.claw.app" bounds=[100,150][500,190]
          [RecyclerView] id="io.claw.app:id/list" pkg="io.claw.app" [scrollable] bounds=[0,600][1080,2000]
          [View] pkg="io.claw.app" bounds=[0,0][0,0]
          [Button] text="Ghost" pkg="io.claw.app" [clickable] [invisible] bounds=[10,10][110,110]
    """.trimIndent()

    @Test
    fun parsesVisibleNonZeroNodesOnly() {
        val nodes = WeakLabelDetector.parseNodes(tree)
        // FrameLayout, Button, EditText, ImageView, TextView, RecyclerView = 6
        // ([View] zero-area and [invisible] Ghost are dropped)
        assertEquals(6, nodes.size)
        assertTrue(nodes.none { it.text == "Ghost" })
    }

    @Test
    fun mapsRolesAndFlags() {
        val nodes = WeakLabelDetector.parseNodes(tree).associateBy { it.resourceId ?: it.className }

        val login = nodes["io.claw.app:id/login_btn"]!!
        assertEquals(AndroidRoleMapper.INTERACTABLE, login.role)
        assertEquals("button", login.fineRole)
        assertTrue(login.clickable)
        assertEquals("Login", login.text)
        assertEquals(250, login.centerX)

        val username = nodes["io.claw.app:id/username"]!!
        assertEquals(AndroidRoleMapper.INPUT, username.role)
        assertEquals("edittext", username.fineRole)
        assertTrue(username.editable)

        val avatar = nodes["ImageView"]!!                 // clickable image -> icon
        assertEquals(AndroidRoleMapper.ICON, avatar.role)

        val welcome = nodes["TextView"]!!                 // non-clickable text
        assertEquals(AndroidRoleMapper.TEXT, welcome.role)
        assertNull(welcome.resourceId)

        val list = nodes["io.claw.app:id/list"]!!
        assertTrue(list.scrollable)
        assertEquals("list", list.fineRole)
    }

    @Test
    fun detectProducesNormalizedBoxes() {
        val boxes = WeakLabelDetector.detect(tree, 1080, 2400)
        assertEquals(6, boxes.size)
        assertTrue(boxes.all { it.cx in 0f..1f && it.cy in 0f..1f && it.w in 0f..1f && it.h in 0f..1f })
        assertTrue(boxes.all { it.source == DetectionBox.SOURCE_WEAK })
        val login = boxes.first { it.cls == AndroidRoleMapper.INTERACTABLE }
        // center x 250 / 1080 ~= 0.2315
        assertEquals(0.231f, login.cx, 0.01f)
    }

    @Test
    fun emptyTreeYieldsNothing() {
        assertTrue(WeakLabelDetector.parseNodes(null).isEmpty())
        assertTrue(WeakLabelDetector.detect("", 100, 100).isEmpty())
    }
}
