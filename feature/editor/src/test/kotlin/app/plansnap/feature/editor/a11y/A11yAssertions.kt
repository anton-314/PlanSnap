package app.plansnap.feature.editor.a11y

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import app.plansnap.core.designsystem.a11y.AccessibilityAudit
import org.junit.Assert.assertEquals

/** Runs every automatic accessibility check on all windows that are shown: screen, sheets, dialogs and menus. */
internal fun ComposeTestRule.assertAccessible() {
    waitForIdle()
    val merged = onAllNodes(isRoot()).fetchSemanticsNodes()
    val unmerged = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
    val findings = AccessibilityAudit.controls(merged) + AccessibilityAudit.texts(unmerged)
    assertEquals("", findings.joinToString("\n") { "${it.rule}: ${it.description}" })
}
