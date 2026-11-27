package app.plansnap.feature.settings.a11y

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.a11y.AccessibilityAudit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.settings.R
import app.plansnap.feature.settings.ui.SettingsActions
import app.plansnap.feature.settings.ui.SettingsScreen
import app.plansnap.feature.settings.ui.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Automatic accessibility checks of every state of the settings (REQ-UX-008), in English and German at normal and at
 * 200 percent font: every control has a spoken label and 48 dp, no text is cut off and no word is broken.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SettingsAccessibilityTest(
    private val language: String,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val defaults =
        SettingsUiState.Loaded(
            unit = LengthUnit.CENTIMETER,
            absoluteToleranceText = "5",
            absoluteToleranceError = null,
            relativeToleranceText = "1",
            relativeToleranceError = null,
            gridEnabled = true,
            snapEnabled = false,
            dismissedHintCount = 2,
            saveError = null,
        )

    private val rejected =
        defaults.copy(
            absoluteToleranceText = "0",
            absoluteToleranceError = InvalidValueReason.TOO_SMALL,
            relativeToleranceText = "150",
            relativeToleranceError = InvalidValueReason.TOO_LARGE,
            saveError = DomainError.Storage(StorageProblem.WRITE_FAILED),
        )

    private fun show(state: SettingsUiState) {
        RuntimeEnvironment.setQualifiers("+$language-w360dp-h640dp-port-notnight-mdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            PlanSnapTheme {
                SettingsScreen(state = state, actions = SettingsActions({}, {}, {}, {}, {}, {}, {}), onBack = {})
            }
        }
    }

    private fun check(state: SettingsUiState) {
        show(state)
        compose.waitForIdle()
        val merged = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        val unmerged = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val findings = AccessibilityAudit.controls(merged) + AccessibilityAudit.texts(unmerged)
        assertEquals("", findings.joinToString("\n") { "${it.rule}: ${it.description}" })
    }

    @Test
    fun loaded() = check(defaults)

    @Test
    fun `rejected input and save failure`() = check(rejected)

    @Test
    fun loading() = check(SettingsUiState.Loading)

    @Test
    fun `loading is spoken`() {
        show(SettingsUiState.Loading)

        compose.onNodeWithContentDescription(context.getString(R.string.settings_a11y_loading)).assertExists()
    }

    @Test
    fun `last control can be scrolled to`() {
        show(rejected)

        compose.onNodeWithText(context.getString(R.string.settings_hints_reset)).performScrollTo()
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
