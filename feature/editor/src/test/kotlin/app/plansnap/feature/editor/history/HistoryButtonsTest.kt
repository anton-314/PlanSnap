package app.plansnap.feature.editor.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class HistoryButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<EditorIntent>()
    private var state by mutableStateOf(editorState())

    private fun show(initial: EditorUiState) {
        state = initial
        compose.setContent {
            PlanSnapTheme { HistoryButtons(state = state, onIntent = { intents += it }) }
        }
    }

    private fun button(label: String): SemanticsNodeInteraction = compose.onNodeWithContentDescription(label)

    private fun SemanticsNodeInteraction.assertEnabled(enabled: Boolean) {
        if (enabled) assertIsEnabled() else assertIsNotEnabled()
    }

    @Test
    fun `given any history depth then each button is enabled exactly while its direction has a step`() {
        show(editorState())

        listOf(0 to 0, 3 to 0, 0 to 2, 1 to 1, 0 to 0).forEach { (undo, redo) ->
            state = editorState().copy(undoDepth = undo, redoDepth = redo)

            button("Undo").assertEnabled(undo > 0)
            button("Redo").assertEnabled(redo > 0)
        }
    }

    @Test
    fun `given a project that is not loaded then both buttons are disabled`() {
        show(EditorUiState())

        button("Undo").assertIsNotEnabled()
        button("Redo").assertIsNotEnabled()
    }

    @Test
    fun `given steps in both directions when the buttons are tapped then undo and redo are sent`() {
        show(editorState().copy(undoDepth = 1, redoDepth = 1))

        button("Undo").performClick()
        button("Redo").performClick()

        assertEquals(listOf<Any>(CoreIntent.Undo, CoreIntent.Redo), intents)
    }

    @Test
    fun `given no step when the disabled buttons are tapped then nothing is sent`() {
        show(editorState())

        button("Undo").performClick()
        button("Redo").performClick()

        assertEquals(emptyList<Any>(), intents)
    }

    @Test
    fun `given the buttons then each has a touch target of at least 48 dp`() {
        show(editorState().copy(undoDepth = 1))

        listOf("Undo", "Redo").forEach {
            button(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp")
    fun `given german then the buttons have german spoken labels`() {
        show(editorState().copy(undoDepth = 1, redoDepth = 1))

        button("Rückgängig").performClick()
        button("Wiederholen").performClick()

        assertEquals(listOf<Any>(CoreIntent.Undo, CoreIntent.Redo), intents)
    }
}
