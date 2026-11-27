package app.plansnap.feature.editor.a11y

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.input.InputBar
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.opening.OpeningSheet
import app.plansnap.feature.editor.opening.WallSheetContent
import app.plansnap.feature.editor.shape.ShapeStatusCard
import app.plansnap.feature.editor.shape.StatusCardToggle
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val NO_HAPTIC = -1

/** What a screen reader hears from controls that show more than their label, and the haptic of `OK`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class SpokenStateTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    private fun showInputBar(state: EditorUiState) {
        compose.setContent {
            view = LocalView.current
            PlanSnapTheme { InputBar(state, {}) }
        }
    }

    /** The haptic feedback that the view of the screen or one of its parents performed. */
    private fun haptics(): List<Int> =
        generateSequence(view) { it.parent as? View }
            .map { shadowOf(it).lastHapticFeedbackPerformed() }
            .filter { it != NO_HAPTIC }
            .toList()

    private fun hasStateDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `the typed value is spoken with the wall thickness`() {
        showInputBar(editorState(input = "345", walls = 3))

        val value = string(R.string.input_value_spoken, string(R.string.input_wall_label, 4), "345", "centimeters")
        compose
            .onNodeWithContentDescription(value)
            .assert(hasStateDescription(string(R.string.a11y_input_thickness_spoken, "12.0", "centimeters")))
    }

    @Test
    fun `the hint of an empty room is spoken with the value`() {
        showInputBar(editorState())

        val value = string(R.string.input_value_empty_spoken, string(R.string.input_wall_label, 1))
        val state =
            string(
                R.string.a11y_input_state_with_hint,
                string(R.string.a11y_input_thickness_spoken, "12.0", "centimeters"),
                string(R.string.editor_empty_hint),
            )
        compose.onNodeWithContentDescription(value).assert(hasStateDescription(state))
    }

    @Test
    fun `OK gives the confirm haptic of the system`() {
        showInputBar(editorState(input = "345"))
        assertEquals(emptyList<Int>(), haptics())

        compose.onNodeWithContentDescription(string(R.string.input_key_confirm)).performClick()

        // Feedback through the view follows the haptic setting of the system; no vibrator is driven directly.
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics())
        assertTrue(view.isHapticFeedbackEnabled)
    }

    @Test
    fun `other keys give no haptic`() {
        showInputBar(editorState(input = "345"))

        compose.onNodeWithContentDescription(string(R.string.input_key_backspace)).performClick()

        assertEquals(emptyList<Int>(), haptics())
    }

    @Test
    fun `the status is part of the button that folds the status card`() {
        val state = A11yStates.needsAdjustment
        val room = checkNotNull(state.selectedRoom)
        compose.setContent {
            PlanSnapTheme {
                ShapeStatusCard(
                    room = room,
                    result = DomainResult.Success(state.evaluations.getValue(room.id)),
                    unit = LengthUnit.CENTIMETER,
                    onIntent = {},
                    toggle = StatusCardToggle(expanded = true) {},
                )
            }
        }

        compose.onNode(hasText(string(R.string.shape_status_unconfirmed)) and hasClickAction()).assertExists()
    }

    @Test
    fun `the fit of an opening is part of its row`() {
        val room = furnishedRoom()
        val state = stateOf(room)
        compose.setContent {
            PlanSnapTheme {
                WallSheetContent(
                    room = room,
                    sheet = OpeningSheet.Wall(room.id, WallId("w1")),
                    statuses = state.evaluations.getValue(room.id).openingStatuses,
                    unit = LengthUnit.CENTIMETER,
                    onIntent = {},
                )
            }
        }

        compose.onNode(hasText(string(R.string.opening_status_out_of_bounds)) and hasClickAction()).assertExists()
    }
}
