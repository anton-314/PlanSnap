package app.plansnap.feature.editor.ui.states

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.navigation.EditorContent
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the whole editor in its empty and failed state, and of its progress state in dark theme. The light
 * progress state is covered by `screen-loading.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class EditorStatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val failed =
        EditorUiState(load = EditorLoadState.Failed(DomainError.Storage(StorageProblem.READ_FAILED)))

    @Test
    fun `given a room without walls when shown then it matches the empty baseline`() =
        capture(editorState(), "states-editor-empty.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h740dp-port-notnight-mdpi")
    fun `given a room without walls when dark and german then it matches the baseline`() =
        capture(editorState(), "states-editor-empty-dark-de.png", darkTheme = true)

    @Test
    fun `given a loading project when dark then it matches the baseline`() =
        capture(EditorUiState(), "states-editor-loading-dark.png", darkTheme = true)

    @Test
    fun `given a failed load when shown then it matches the baseline`() = capture(failed, "states-editor-failed.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h740dp-port-notnight-mdpi")
    fun `given a failed load when dark and german then it matches the baseline`() =
        capture(failed, "states-editor-failed-dark-de.png", darkTheme = true)

    @Test
    fun `given a failed load when the font scale is 2 then it matches the baseline`() =
        capture(failed, "states-editor-failed-fontscale-2.png", fontScale = 2f)

    private fun capture(
        state: EditorUiState,
        fileName: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme(darkTheme = darkTheme) {
                    EditorContent(state, emptyFlow(), {}, EditorNavigationActions({}, {}))
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
