package app.plansnap.feature.export.ui.states

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.export.ui.ExportActions
import app.plansnap.feature.export.ui.ExportScreen
import app.plansnap.feature.export.ui.ExportUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the progress and load error state of the export, and of its states in dark theme, German and at
 * 200 percent font scale. The light "no room" state is covered by `export-no-room.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class ExportStatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val failed = ExportUiState.Failed(DomainError.Storage(StorageProblem.READ_FAILED))

    @Test
    fun `given the project is being read when shown then it matches the progress baseline`() =
        capture(ExportUiState.Loading, "states-export-loading.png")

    @Test
    fun `given a load failure when shown then it matches the baseline`() =
        capture(failed, "states-export-load-failed.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a load failure when dark and german then it matches the baseline`() =
        capture(failed, "states-export-load-failed-dark-de.png", darkTheme = true)

    @Test
    fun `given a load failure when the font scale is 2 then it matches the baseline`() =
        capture(failed, "states-export-load-failed-fontscale-2.png", fontScale = 2f)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given no room when dark, german and font scale 2 then it matches the baseline`() =
        capture(ExportUiState.Empty("Flat"), "states-export-no-room-dark-de-fontscale-2.png", true, 2f)

    private fun capture(
        state: ExportUiState,
        fileName: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    ExportScreen(state = state, actions = NO_ACTIONS, onBack = {})
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        val NO_ACTIONS = ExportActions({}, {}, {}, {}, {}, {}, {}, {}, {})

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
