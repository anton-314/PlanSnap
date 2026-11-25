package app.plansnap.feature.export.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.export.ExportFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the export dialog: standard, warnings, page errors, progress, German and 200 percent font scale. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h900dp-port-notnight-mdpi")
class ExportScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given the standard state when shown then it matches the baseline`() =
        capture(ExportScreenStates.standard, 1f, "export-standard.png")

    @Test
    fun `given preflight findings when shown then it matches the baseline`() =
        capture(ExportScreenStates.warnings, 1f, "export-warnings.png")

    @Test
    fun `given an empty drawing when shown then it matches the baseline`() =
        capture(ExportScreenStates.emptyDrawing, 1f, "export-empty-drawing.png")

    @Test
    fun `given a plan that does not fit the choice when shown then it matches the baseline`() =
        capture(ExportScreenStates.doesNotFit, 1f, "export-fit-error.png")

    @Test
    fun `given a plan that is too large when shown then it matches the baseline`() =
        capture(ExportScreenStates.tooLarge, 1f, "export-too-large.png")

    @Test
    fun `given a running export when shown then it matches the baseline`() =
        capture(ExportScreenStates.standard.copy(running = ExportAction.SAVE), 1f, "export-running.png")

    @Test
    fun `given a failed export when shown then it matches the baseline`() =
        capture(
            ExportScreenStates.standard.copy(error = DomainError.Export(ExportProblem.WRITE_FAILED)),
            1f,
            "export-failed.png",
        )

    @Test
    fun `given a saved file when shown then it matches the baseline`() =
        capture(
            ExportScreenStates.standard.copy(notice = ExportNotice.Saved(ExportFormat.PDF)),
            1f,
            "export-saved.png",
        )

    @Test
    fun `given the empty project when shown then it matches the baseline`() =
        capture(ExportUiState.Empty("Flat"), 1f, "export-no-room.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h900dp-port-notnight-mdpi")
    fun `given findings when german then it matches the baseline`() =
        capture(ExportScreenStates.warnings, 1f, "export-warnings-de.png")

    @Test
    fun `given findings when the font scale is 2 then it matches the baseline`() =
        capture(ExportScreenStates.warnings, 2f, "export-warnings-fontscale-2.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h2400dp-port-notnight-mdpi")
    fun `given the choice rows when the font scale is 2 then their texts are not clipped`() =
        capture(ExportScreenStates.standard, 2f, "export-choices-fontscale-2.png")

    private fun capture(
        state: ExportUiState,
        fontScale: Float,
        fileName: String,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                // The export always uses the light plan palette (docs/UX.md section 1, colors).
                PlanSnapTheme(darkTheme = false) {
                    ExportScreen(state = state, actions = NO_ACTIONS, onBack = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        val NO_ACTIONS = ExportActions({}, {}, {}, {}, {}, {}, {}, {}, {})

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
