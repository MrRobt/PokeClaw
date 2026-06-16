package io.agents.pokeclaw.utils

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNoiseFilterUtilsTest {
    private val rootLeft = 0
    private val rootTop = 0
    private val rootRight = 1000
    private val rootBottom = 2000

    @Test
    fun `timestamp-like labels are filtered without English words`() {
        assertTrue(ChatNoiseFilterUtils.isLikelyTimestampLike("7:47"))
        assertTrue(ChatNoiseFilterUtils.isLikelyTimestampLike("07.47"))
        assertTrue(ChatNoiseFilterUtils.isLikelyTimestampLike("23/04"))
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("bring 2 bottles"))
    }

    @Test
    fun `centered short labels are treated as system separators`() {
        assertTrue(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(380, 420, 620, 470, rootLeft, rootTop, rootRight, rootBottom, "Today"))
        assertTrue(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(380, 420, 620, 470, rootLeft, rootTop, rootRight, rootBottom, "今天"))
    }

    @Test
    fun `left aligned chat bubble is not treated as system label`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(40, 700, 500, 840, rootLeft, rootTop, rootRight, rootBottom, "Bring wine"))
        assertFalse(ChatNoiseFilterUtils.isLikelyNonMessageLabel(40, 700, 500, 840, rootLeft, rootTop, rootRight, rootBottom, "Bring wine"))
    }

    @Test
    fun `wide upper banner is filtered as non-message noise`() {
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(120, 360, 900, 470, rootLeft, rootTop, rootRight, rootBottom, "Messages are end-to-end encrypted"))
    }

    // --- isLikelyTimestampLike ---

    @Test
    fun `timestamp returns false for null`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike(null))
    }

    @Test
    fun `timestamp returns false for empty or blank`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike(""))
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("   "))
    }

    @Test
    fun `timestamp returns false when text has no digits`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("Today"))
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("hello world"))
    }

    @Test
    fun `timestamp returns false for strings longer than 14 chars`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("123456789012345"))
    }

    @Test
    fun `timestamp matches full date formats`() {
        // The regex requires 1-2 digits at start, so year-first formats are NOT detected.
        assertTrue(ChatNoiseFilterUtils.isLikelyTimestampLike("23/04/2026"))
        assertTrue(ChatNoiseFilterUtils.isLikelyTimestampLike("12.31.2024"))
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("2026-04-23"))
    }

    @Test
    fun `timestamp rejects digits without separator`() {
        // 12345 has no separator, so not a timestamp
        assertFalse(ChatNoiseFilterUtils.isLikelyTimestampLike("12345"))
    }

    // --- isLikelyCenteredSystemLabel: text length boundary ---

    @Test
    fun `centered label returns false for text longer than 32 chars`() {
        val longText = "a".repeat(33)
        assertFalse(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(380, 420, 620, 470, rootLeft, rootTop, rootRight, rootBottom, longText))
    }

    @Test
    fun `centered label returns false for null text`() {
        // null text disables the shortLabel path; bounds aren't a wide banner so we bail to false.
        assertFalse(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(380, 420, 620, 470, rootLeft, rootTop, rootRight, rootBottom, null))
    }

    @Test
    fun `centered narrow pill with empty text is still detected as system label`() {
        // Empty text: text != null && trim().length() <= 32 is true, so narrowPill path matches.
        assertTrue(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(380, 420, 620, 470, rootLeft, rootTop, rootRight, rootBottom, ""))
    }

    // --- isLikelyCenteredSystemLabel: rootWidth edge cases ---

    @Test
    fun `centered label returns false when root width is zero`() {
        // root width = 0 should bail out
        assertFalse(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(100, 100, 200, 200, 100, 100, 100, 200, "X"))
    }

    // --- Rect-based overloads ---

    @Test
    fun `isLikelyTimestampLike Rect overload returns true for null rootBounds`() {
        // When bounds is null, isLikelyNonMessageLabel returns true for null/blank text
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(null as Rect?, null as Rect?, null))
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(null as Rect?, null as Rect?, ""))
    }

    @Test
    fun `isLikelyNonMessageLabel Rect overload returns false for non-message text with valid bounds`() {
        val bounds = Rect(40, 700, 500, 840)
        val root = Rect(rootLeft, rootTop, rootRight, rootBottom)
        assertFalse(ChatNoiseFilterUtils.isLikelyNonMessageLabel(bounds, root, "Bring wine"))
    }

    @Test
    fun `isLikelyCenteredSystemLabel Rect overload returns false for null bounds`() {
        assertFalse(ChatNoiseFilterUtils.isLikelyCenteredSystemLabel(null as Rect?, null as Rect?, "x"))
    }

    @Test
    fun `isLikelyNonMessageLabel Rect overload with null bounds and empty text returns true`() {
        // Source: when bounds is null, returns true if text is null or empty.
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(null as Rect?, null as Rect?, null))
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(null as Rect?, null as Rect?, ""))
    }

    // --- isLikelyNonMessageLabel: composition of timestamp + centered ---

    @Test
    fun `isLikelyNonMessageLabel returns true for null text when bounds valid`() {
        // text null + valid bounds falls through to the bool composition
        // The Rect overload: null text or empty trimmed -> true
        val bounds = Rect(0, 0, 100, 100)
        val root = Rect(rootLeft, rootTop, rootRight, rootBottom)
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(bounds, root, null))
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(bounds, root, "  "))
    }

    @Test
    fun `isLikelyNonMessageLabel returns true for timestamp-like text even if not centered`() {
        // timestamp check short-circuits before centered check
        assertTrue(ChatNoiseFilterUtils.isLikelyNonMessageLabel(40, 700, 500, 840, rootLeft, rootTop, rootRight, rootBottom, "7:47"))
    }
}
