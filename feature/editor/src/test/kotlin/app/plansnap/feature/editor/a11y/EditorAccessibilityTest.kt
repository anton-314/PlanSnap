package app.plansnap.feature.editor.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.navigation.EditorContent
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Automatic accessibility checks of every state of the editor (REQ-UX-008), in English and German at normal and at
 * 200 percent font: every control has a spoken label and 48 dp, no text is cut off and no word is broken.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class EditorAccessibilityTest(
    private val language: String,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$language-w360dp-h640dp-port-notnight-mdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { PlanSnapTheme { content() } }
    }

    private fun showEditor(state: EditorUiState) =
        show { EditorContent(state, emptyFlow(), {}, EditorNavigationActions({}, {})) }

    private fun check(state: () -> EditorUiState) {
        // The state is built after the locale is set, as the app builds it.
        RuntimeEnvironment.setQualifiers("+$language-w360dp-h640dp-port-notnight-mdpi")
        showEditor(state())
        compose.assertAccessible()
    }

    @Test
    fun `room that needs an adjustment`() = check { A11yStates.needsAdjustment }

    @Test
    fun `confirmed room`() = check { A11yStates.confirmed }

    @Test
    fun `proposed room`() = check { A11yStates.proposed }

    @Test
    fun `conflicting room`() = check { A11yStates.conflict }

    @Test
    fun `first wall with a hint`() = check { A11yStates.firstWall }

    @Test
    fun `measured corner angle`() = check { A11yStates.measuredCorner }

    @Test
    fun loading() = check { EditorUiState() }

    @Test
    fun `load failed`() =
        check { EditorUiState(load = EditorLoadState.Failed(DomainError.Storage(StorageProblem.READ_FAILED))) }

    @Test
    fun `failed save`() = check { A11yStates.failedSave }

    @Test
    fun `picking a dock host`() = check { A11yStates.picking }

    @Test
    fun `overflow menu`() {
        showEditor(A11yStates.confirmed)
        val menu = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.editor_menu)
        compose.onNodeWithContentDescription(menu).performClick()
        compose.assertAccessible()
    }

    @Test
    fun `angle sheet`() = check { A11yStates.cornerSheet() }

    @Test
    fun `diagonal sheet`() = check { A11yStates.diagonalSheet() }

    @Test
    fun `thickness sheet`() = check { A11yStates.thicknessSheet() }

    @Test
    fun `adjustment sheet`() = check { A11yStates.adjustmentSheet() }

    @Test
    fun `large correction dialog`() = check { A11yStates.explicitDialog() }

    @Test
    fun `wall sheet`() = check { A11yStates.wallSheet() }

    @Test
    fun `opening form`() = check { A11yStates.openingForm() }

    @Test
    fun `delete opening dialog`() = check { A11yStates.deleteDialog() }

    @Test
    fun `room details sheet`() = check { A11yStates.roomDetails() }

    @Test
    fun `area overview sheet`() = check { A11yStates.areaOverview() }

    @Test
    fun `dock sheet`() = check { A11yStates.dockSheet }

    @Test
    fun `plan outline`() {
        RuntimeEnvironment.setQualifiers("+$language-w360dp-h640dp-port-notnight-mdpi")
        val state = A11yStates.confirmed.withSection(PlanOutlineState::class, PlanOutlineState(open = true))
        show { PlanOutlineHost(state) {} }
        compose.assertAccessible()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, font scale {1}")
        fun variants(): List<Array<Any>> =
            listOf("en-rUS", "de-rDE").flatMap { language ->
                listOf(1f, 2f).map { arrayOf<Any>(language, it) }
            }
    }
}
