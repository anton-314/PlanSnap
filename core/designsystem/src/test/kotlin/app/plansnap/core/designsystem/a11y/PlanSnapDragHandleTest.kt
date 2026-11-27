package app.plansnap.core.designsystem.a11y

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Proves that a bottom sheet with [PlanSnapDragHandle] has a labelled handle of at least 48 dp (REQ-UX-008). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanSnapDragHandleTest {
    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `given a bottom sheet with the handle when audited then nothing is found`() {
        compose.setContent {
            PlanSnapTheme {
                ModalBottomSheet(
                    onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    dragHandle = { PlanSnapDragHandle() },
                ) { Text("Content") }
            }
        }
        compose.waitForIdle()
        val findings = AccessibilityAudit.controls(compose.onAllNodes(isRoot()).fetchSemanticsNodes())

        assertEquals(emptyList<AccessibilityFinding>(), findings)
        compose.onNodeWithContentDescription("Resize or close the sheet").assertExists()
    }
}
