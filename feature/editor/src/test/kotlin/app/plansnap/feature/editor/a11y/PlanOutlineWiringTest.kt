package app.plansnap.feature.editor.a11y

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.navigation.EditorContent
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.state.EditorIntent
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanOutlineWiringTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the menu opens the list`() {
        val intents = mutableListOf<EditorIntent>()
        compose.setContent {
            PlanSnapTheme {
                EditorContent(A11yStates.confirmed, emptyFlow(), { intents += it }, EditorNavigationActions({}, {}))
            }
        }
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Plan as a list").performScrollTo().performClick()

        assertEquals(PlanOutlineIntent.Open, intents.last())
    }
}
