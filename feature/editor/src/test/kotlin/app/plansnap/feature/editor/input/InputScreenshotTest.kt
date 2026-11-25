package app.plansnap.feature.editor.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.state.EditorUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the input bar: light, dark, German, 200 percent font scale and the error state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h1200dp-port-notnight-mdpi")
class InputScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val running = editorState(input = "345", walls = 3)

    @Test
    fun `given a running input when light then it matches the baseline`() =
        capture(running, false, 1f, "input-light.png")

    @Test
    fun `given a running input when dark then it matches the baseline`() = capture(running, true, 1f, "input-dark.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1200dp-port-notnight-mdpi")
    fun `given a running input when german then it matches the baseline`() =
        capture(editorState(input = "3,45", walls = 3), false, 1f, "input-de.png")

    @Test
    fun `given a running input when font scale is 2 then it matches the baseline`() =
        capture(running, false, 2f, "input-fontscale-2.png")

    @Test
    fun `given an invalid input when shown then it matches the baseline`() =
        capture(editorState(input = "3,4,", walls = 3, error = malformedLength()), false, 1f, "input-error.png")

    @Test
    fun `given an empty room when shown then it matches the baseline`() =
        capture(editorState(), false, 1f, "input-empty.png")

    @Test
    fun `given a measured angle and three walls when shown then it matches the baseline`() =
        capture(
            editorState(input = "300", walls = 3)
                .copy(nextCorner = CornerSpec.Measured(TurnDirection.RIGHT, Angle(75_500))),
            false,
            1f,
            "input-measured-angle.png",
        )

    @Test
    fun `given the extra keys when shown then they match the baseline`() =
        capture(editorState(input = "300", walls = 3), false, 1f, "input-extra-keys.png", withExtraKeys = true)

    private fun capture(
        state: EditorUiState,
        darkTheme: Boolean,
        fontScale: Float,
        fileName: String,
        withExtraKeys: Boolean = false,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    Surface {
                        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            InputBar(
                                state = state,
                                onIntent = {},
                                extraKeys =
                                    if (withExtraKeys) {
                                        listOf(ExtraKey("D", "Wall thickness") {}, ExtraKey("Diag", "Add diagonal") {})
                                    } else {
                                        emptyList()
                                    },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
