package app.plansnap.feature.editor.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.designsystem.a11y.AccessibilityAudit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.navigation.EditorContent
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A large font and a narrow window: the input area scrolls to every key and no word on it breaks (UX section 7). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
class FontScaleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun showEditor(
        fontScale: Float,
        state: () -> EditorUiState,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val built = state()
        show { EditorContent(built, emptyFlow(), {}, EditorNavigationActions({}, {})) }
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlanSnapTheme { content() } }

    private fun assertNoBrokenOrClippedText() {
        compose.waitForIdle()
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("", AccessibilityAudit.texts(roots).joinToString("\n") { it.description })
    }

    /** Every key of the keypad, by its spoken label. */
    private fun keys(): List<String> =
        listOf(
            string(R.string.input_key_backspace),
            string(R.string.input_key_thickness),
            string(R.string.input_key_diagonal),
            string(R.string.input_key_comma),
            string(R.string.input_key_confirm),
            string(R.string.input_key_angle),
        )

    @Test
    fun `at 200 percent every key can be scrolled to and keeps its size`() {
        // A third wall exists, so that the angle key is enabled as well.
        showEditor(2f) { A11yStates.needsAdjustment }

        keys().forEach { key ->
            compose
                .onNodeWithContentDescription(key)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(56.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `german wall number does not break at 200 percent after a failed save`() {
        showEditor(2f) { A11yStates.failedSave }

        assertNoBrokenOrClippedText()
    }

    @Test
    @Config(qualifiers = "de-rDE-w280dp-h640dp-port-notnight-mdpi")
    fun `no key word breaks on a window 280 dp wide`() {
        showEditor(1f) { A11yStates.needsAdjustment }

        assertNoBrokenOrClippedText()
    }

    @Test
    @Config(qualifiers = "de-rDE-w280dp-h640dp-port-notnight-mdpi")
    fun `no key word breaks on a window 280 dp wide at 130 percent`() {
        showEditor(1.3f) { A11yStates.needsAdjustment }

        assertNoBrokenOrClippedText()
    }

    @Test
    @Config(qualifiers = "en-rUS-w280dp-h640dp-port-notnight-mdpi")
    fun `no key word breaks on a window 280 dp wide in english`() {
        showEditor(1f) { A11yStates.needsAdjustment }

        assertNoBrokenOrClippedText()
    }
}
