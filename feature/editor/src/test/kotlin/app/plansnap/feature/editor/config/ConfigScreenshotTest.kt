package app.plansnap.feature.editor.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.feature.editor.navigation.EditorContent
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.shape.almostRectangle
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorUiState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Baselines of the whole editor in every window (UX section 2): top bar, plan of an open room with the hint that a
 * first user sees, and the input bar with all its keys. In the low windows they show how little room is left for the
 * plan.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
internal class ConfigScreenshotTest(
    private val device: DeviceConfig,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `given a window when the editor is shown then it matches its baseline`() {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        val state = openRoomState()
        compose.setContent {
            PlanSnapTheme {
                EditorContent(
                    state = state,
                    effects = emptyFlow(),
                    onIntent = {},
                    actions = EditorNavigationActions(onBack = {}, onOpenExport = {}),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()

        compose.onRoot().captureRoboImage("config-${device.name.lowercase().replace('_', '-')}.png")
    }

    /** An open room of four walls, drawn by the real scene builder, with a typed value. */
    private fun openRoomState(): EditorUiState {
        val room = almostRectangle(3030).copy(isClosed = false)
        val state = stateOf(room) { copy(inputText = "345") }
        val project = checkNotNull(state.project)
        return state.copy(scene = DefaultSceneBuilder().build(project, state.evaluations, SceneOptions()))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun devices(): List<DeviceConfig> = DeviceConfig.entries
    }
}
