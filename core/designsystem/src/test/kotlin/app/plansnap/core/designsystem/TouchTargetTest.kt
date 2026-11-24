package app.plansnap.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapNumericKey
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Proves the touch target sizes of docs/UX.md section 6 (REQ-UX-008): 48 dp, numeric keys 56 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class TouchTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given minimal content when rendered then every interactive component is at least 48 dp`() {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = false) {
                Column {
                    PlanSnapButton("A", onClick = {})
                    PlanSnapOutlinedButton("B", onClick = {})
                    PlanSnapIconButton(PlanSnapIcons.Check, "icon", onClick = {})
                }
            }
        }

        listOf(composeRule.onNodeWithText("A"), composeRule.onNodeWithText("B")).forEach {
            it.assertHeightIsAtLeast(48.dp)
            it.assertWidthIsAtLeast(48.dp)
        }
        composeRule.onNodeWithContentDescription("icon").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `given a single digit when rendered then the numeric key is at least 56 dp`() {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = true) {
                PlanSnapNumericKey("1", onClick = {})
            }
        }

        composeRule.onNodeWithText("1").assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(56.dp)
    }
}
