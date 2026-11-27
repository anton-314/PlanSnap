package app.plansnap.feature.editor.history

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.input.editorState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the active states of the history buttons, from top to bottom: no step, only undo, only redo, both.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class HistoryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given every active state when light then it matches the baseline`() =
        capture(false, "history-states-light.png")

    @Test
    fun `given every active state when dark then it matches the baseline`() = capture(true, "history-states-dark.png")

    private fun capture(
        darkTheme: Boolean,
        fileName: String,
    ) {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.testTag(TAG)) {
                    Column {
                        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).forEach { (undo, redo) ->
                            HistoryButtons(editorState().copy(undoDepth = undo, redoDepth = redo), onIntent = {})
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag(TAG).captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        const val TAG = "history-states"

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
