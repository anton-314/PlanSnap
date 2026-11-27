package app.plansnap.feature.editor.shape

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/**
 * Asserts that the text containing [text] is readable as a whole: no line is cut off or ellipsized, and no part of
 * its node is clipped by a parent or the screen.
 */
internal fun ComposeTestRule.assertTextComplete(text: String) {
    val node = onNodeWithText(text, substring = true, useUnmergedTree = true)
    node.assertIsDisplayed()
    val semantics = node.fetchSemanticsNode()
    val visible = semantics.boundsInRoot
    assertEquals("'$text' is clipped in height", semantics.size.height.toFloat(), visible.height, 0.5f)
    assertEquals("'$text' is clipped in width", semantics.size.width.toFloat(), visible.width, 0.5f)
    val results = mutableListOf<TextLayoutResult>()
    semantics.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    val layout = results.single()
    assertFalse("'$text' overflows its height", layout.didOverflowHeight)
    for (line in 0 until layout.lineCount) {
        assertFalse("'$text' is ellipsized", layout.isLineEllipsized(line))
    }
    val shown = layout.layoutInput.text.text
    assertEquals("'$text' ends early", shown.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
}
