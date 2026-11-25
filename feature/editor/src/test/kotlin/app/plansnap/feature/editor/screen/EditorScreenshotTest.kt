package app.plansnap.feature.editor.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.input.malformedLength
import app.plansnap.feature.editor.state.EditorUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the editor frame in portrait with a stand-in for the canvas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class EditorScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given a running input when light then it matches the baseline`() =
        capture(editorState(input = "345", walls = 3).copy(undoDepth = 3), false, 1f, "screen-light.png")

    @Test
    fun `given a running input when dark then it matches the baseline`() =
        capture(editorState(input = "345", walls = 3).copy(undoDepth = 3), true, 1f, "screen-dark.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h740dp-port-notnight-mdpi")
    fun `given a running input when german then it matches the baseline`() =
        capture(editorState(input = "3,45", walls = 3).copy(undoDepth = 3), false, 1f, "screen-de.png")

    @Test
    fun `given a running input when font scale is 2 then it matches the baseline`() =
        capture(editorState(input = "345", walls = 3).copy(undoDepth = 3), false, 2f, "screen-fontscale-2.png")

    @Test
    fun `given an invalid input when shown then it matches the baseline`() =
        capture(editorState(input = "3,4,", walls = 3, error = malformedLength()), false, 1f, "screen-error.png")

    @Test
    fun `given a loading project when shown then it matches the baseline`() =
        capture(EditorUiState(), false, 1f, "screen-loading.png")

    private fun capture(
        state: EditorUiState,
        darkTheme: Boolean,
        fontScale: Float,
        fileName: String,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    EditorScreen(
                        state = state,
                        actions = EditorActions({}, {}, {}),
                        canvas = { modifier ->
                            Box(
                                modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Text("Canvas") }
                        },
                    )
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
