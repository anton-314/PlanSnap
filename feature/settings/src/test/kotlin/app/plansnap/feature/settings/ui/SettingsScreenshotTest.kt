package app.plansnap.feature.settings.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the settings screen: light, dark, German, 200 percent font scale and the error states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h1200dp-port-notnight-mdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val defaults =
        SettingsUiState.Loaded(
            unit = LengthUnit.CENTIMETER,
            absoluteToleranceText = "5.0",
            absoluteToleranceError = null,
            relativeToleranceText = "1",
            relativeToleranceError = null,
            gridEnabled = true,
            snapEnabled = false,
            dismissedHintCount = 2,
            saveError = null,
        )

    @Test
    fun `given default settings when light then it matches the baseline`() =
        capture(defaults, false, 1f, "settings-light.png")

    @Test
    fun `given default settings when dark then it matches the baseline`() =
        capture(defaults, true, 1f, "settings-dark.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1200dp-port-notnight-mdpi")
    fun `given default settings when german then it matches the baseline`() =
        capture(defaults.copy(absoluteToleranceText = "5,0"), false, 1f, "settings-de.png")

    @Test
    fun `given default settings when font scale is 2 then it matches the baseline`() =
        capture(defaults, false, 2f, "settings-fontscale-2.png")

    @Test
    fun `given rejected input and a save failure when shown then it matches the baseline`() =
        capture(
            defaults.copy(
                absoluteToleranceText = "0",
                absoluteToleranceError = InvalidValueReason.TOO_SMALL,
                relativeToleranceText = "150",
                relativeToleranceError = InvalidValueReason.TOO_LARGE,
                saveError = DomainError.Storage(StorageProblem.WRITE_FAILED),
            ),
            false,
            1f,
            "settings-errors.png",
        )

    private fun capture(
        state: SettingsUiState,
        darkTheme: Boolean,
        fontScale: Float,
        fileName: String,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    SettingsScreen(
                        state = state,
                        actions = NO_ACTIONS,
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        val NO_ACTIONS =
            SettingsActions({}, {}, {}, {}, {}, {}, {})

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
