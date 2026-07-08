package io.agents.pokeclaw.explore

import io.agents.pokeclaw.vision.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StateHasherTest {

    private fun node(id: String, role: String, fine: String, x: Int, y: Int, text: String? = null) =
        UiNode(
            className = fine, role = role, fineRole = fine, text = text, resourceId = id,
            left = x, top = y, right = x + 100, bottom = y + 50, clickable = true,
        )

    @Test
    fun sameStructureSameHash_ignoringDynamicText() {
        val a = listOf(node("btn", "interactable", "button", 100, 100, text = "Buy now"))
        val b = listOf(node("btn", "interactable", "button", 100, 100, text = "Sold out"))
        assertEquals(
            StateHasher.hash("app", "Home", a),
            StateHasher.hash("app", "Home", b),
        )
    }

    @Test
    fun differentStructureDifferentHash() {
        val home = listOf(node("btnA", "interactable", "button", 100, 100))
        val settings = listOf(node("btnX", "interactable", "button", 100, 100))
        assertNotEquals(
            StateHasher.hash("app", "Home", home),
            StateHasher.hash("app", "Settings", settings),
        )
    }

    @Test
    fun differentActivitySameNodesDiffers() {
        val nodes = listOf(node("btn", "interactable", "button", 100, 100))
        assertNotEquals(
            StateHasher.hash("app", "A", nodes),
            StateHasher.hash("app", "B", nodes),
        )
    }
}
