package app.plansnap.feature.editor.input

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.shape.CornerTarget
import app.plansnap.feature.editor.shape.ShapeIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h1200dp")
class InputBarShapeKeysTest {
    @get:Rule
    val compose = createComposeRule()

    private val log = IntentLog()

    private fun show(
        state: EditorUiState,
        extraKeys: List<ExtraKey> = emptyList(),
    ) {
        compose.setContent {
            PlanSnapTheme {
                val (current, onIntent) = rememberFakeEditor(state, log)
                InputBar(state = current, onIntent = onIntent, extraKeys = extraKeys)
            }
        }
    }

    private fun sent(): List<EditorIntent> = log.intents

    @Test
    fun `given a room with walls when the angle key is tapped then the angle sheet opens for the next corner`() {
        show(editorState(walls = 2))
        compose.onNodeWithContentDescription("Enter corner angle").assertIsEnabled().performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.OpenCorner(CornerTarget.Next)), sent())
    }

    @Test
    fun `given a room without walls then the angle key is disabled because the first corner has no angle`() {
        show(editorState())
        compose.onNodeWithContentDescription("Enter corner angle").assertIsNotEnabled()
    }

    @Test
    fun `given a closed room then the angle key is disabled`() {
        show(editorState(walls = 3, closed = true))
        compose.onNodeWithContentDescription("Enter corner angle").assertIsNotEnabled()
    }

    @Test
    fun `given a measured next corner then the angle key shows the angle and says so`() {
        val measured =
            editorState(walls = 2).copy(nextCorner = CornerSpec.Measured(TurnDirection.RIGHT, Angle(75_500)))
        show(measured)
        compose.onNodeWithContentDescription("Corner angle 75.5 degrees, change").assertIsEnabled()
        compose.onNode(hasText("∠ 75.5°")).assertExists()
    }

    @Test
    fun `given the angle key then it is at least 56 dp`() {
        show(editorState(walls = 2))
        compose
            .onNodeWithContentDescription("Enter corner angle")
            .assertHeightIsAtLeast(56.dp)
            .assertWidthIsAtLeast(56.dp)
    }

    @Test
    fun `given fewer than three walls then there is no close key`() {
        show(editorState(walls = 2))
        compose.onNodeWithContentDescription("Close room").assertDoesNotExist()
    }

    @Test
    fun `given three walls when the close key is tapped then the room is closed`() {
        show(editorState(walls = 3))
        compose.onNodeWithContentDescription("Close room").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(listOf<EditorIntent>(ShapeIntent.CloseRoom), sent())
    }

    @Test
    fun `given a closed room then there is no close key`() {
        show(editorState(walls = 3, closed = true))
        compose.onNodeWithContentDescription("Close room").assertDoesNotExist()
    }

    @Test
    fun `given the extra keys D and Diag then they are keys of at least 56 dp with spoken labels`() {
        var thickness = 0
        var diagonal = 0
        show(
            editorState(walls = 2),
            listOf(
                ExtraKey("D", "Wall thickness") { thickness++ },
                ExtraKey("Diag", "Add diagonal") { diagonal++ },
            ),
        )
        compose.onNodeWithContentDescription("Wall thickness").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Add diagonal").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNode(hasText("Diag") and hasClickAction()).assertExists()
        assertEquals(1 to 1, thickness to diagonal)
    }
}
