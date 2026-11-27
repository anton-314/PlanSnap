package app.plansnap.feature.export.a11y

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.a11y.AccessibilityAudit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.export.ExportFormat
import app.plansnap.feature.export.R
import app.plansnap.feature.export.ui.ExportAction
import app.plansnap.feature.export.ui.ExportActions
import app.plansnap.feature.export.ui.ExportNotice
import app.plansnap.feature.export.ui.ExportScreen
import app.plansnap.feature.export.ui.ExportScreenStates
import app.plansnap.feature.export.ui.ExportUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Automatic accessibility checks of every state of the export screen (REQ-UX-008), in English and German at normal
 * and at 200 percent font: every control has a spoken label and 48 dp, no text is cut off and no word is broken.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExportAccessibilityTest(
    private val language: String,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun show(state: ExportUiState) {
        RuntimeEnvironment.setQualifiers("+$language-w360dp-h640dp-port-notnight-mdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            PlanSnapTheme(darkTheme = false) {
                ExportScreen(state = state, actions = ExportActions({}, {}, {}, {}, {}, {}, {}, {}, {}), onBack = {})
            }
        }
    }

    private fun check(state: ExportUiState) {
        show(state)
        compose.waitForIdle()
        val merged = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        val unmerged = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val findings = AccessibilityAudit.controls(merged) + AccessibilityAudit.texts(unmerged)
        assertEquals("", findings.joinToString("\n") { "${it.rule}: ${it.description}" })
    }

    @Test
    fun `ready to export`() = check(ExportScreenStates.standard)

    @Test
    fun `preflight warnings`() = check(ExportScreenStates.warnings)

    @Test
    fun `empty drawing`() = check(ExportScreenStates.emptyDrawing)

    @Test
    fun `plan does not fit`() = check(ExportScreenStates.doesNotFit)

    @Test
    fun running() = check(ExportScreenStates.standard.copy(running = ExportAction.SAVE))

    @Test
    fun failed() = check(ExportScreenStates.standard.copy(error = DomainError.Export(ExportProblem.WRITE_FAILED)))

    @Test
    fun saved() = check(ExportScreenStates.standard.copy(notice = ExportNotice.Saved(ExportFormat.PDF)))

    @Test
    fun loading() = check(ExportUiState.Loading)

    @Test
    fun `no room yet`() = check(ExportUiState.Empty("Flat"))

    @Test
    fun `load failed`() = check(ExportUiState.Failed(DomainError.Storage(StorageProblem.READ_FAILED)))

    @Test
    fun `automatic choices name their row`() {
        show(ExportScreenStates.standard)

        listOf(
            R.string.export_scale_label to R.string.export_scale_auto,
            R.string.export_paper_label to R.string.export_paper_auto,
        ).forEach { (label, automatic) ->
            val spoken =
                context.getString(R.string.export_a11y_choice, context.getString(label), context.getString(automatic))
            compose.onNodeWithContentDescription(spoken).assertExists()
        }
    }

    @Test
    fun `save and share stay on screen`() {
        show(ExportScreenStates.warnings)

        compose.onNodeWithText(context.getString(R.string.export_action_save)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.export_action_share)).assertIsDisplayed()
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
