package app.plansnap.feature.editor.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.ReloadProject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class EditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<EditorIntent>()
    private var backs = 0
    private var exports = 0

    private fun show(
        state: EditorUiState,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme {
                    EditorScreen(
                        state = state,
                        actions = EditorActions({ intents += it }, { backs++ }, { exports++ }),
                        canvas = { modifier -> Box(modifier) { Text("canvas-slot") } },
                    )
                }
            }
        }
    }

    @Test
    fun `given a loaded project then canvas slot and input bar are shown`() {
        show(editorState(input = "345"))

        compose.onNodeWithText("canvas-slot").assertIsDisplayed()
        compose.onNodeWithText("Wohnung Süd").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `given the top bar when its actions are tapped then back and export are called`() {
        show(editorState())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Export").performClick()

        assertEquals(1, backs)
        assertEquals(1, exports)
    }

    @Test
    fun `given undo and redo depth then the buttons are enabled and send intents`() {
        show(editorState().copy(undoDepth = 2))

        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()

        assertEquals(listOf<Any>(CoreIntent.Undo), intents)
    }

    @Test
    fun `given a loading project then progress is shown and no keypad`() {
        show(EditorUiState())

        compose.onNodeWithText("Loading project").assertIsDisplayed()
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun `given a failed load then retry and back to the list are offered`() {
        show(EditorUiState(load = EditorLoadState.Failed(DomainError.Storage(StorageProblem.READ_FAILED))))

        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Back to the project list").performClick()

        assertEquals(listOf<Any>(ReloadProject), intents)
        assertEquals(1, backs)
    }

    @Test
    fun `given a font scale of 2 when the keypad does not fit then ok is reachable by scrolling`() {
        show(editorState(input = "345"), fontScale = 2f)

        compose
            .onNodeWithText("OK")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf<Any>(CoreIntent.ConfirmWall), intents)
    }
}
