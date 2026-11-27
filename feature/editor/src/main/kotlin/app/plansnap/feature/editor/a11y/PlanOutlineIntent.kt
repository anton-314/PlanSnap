package app.plansnap.feature.editor.a11y

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorSectionState

/**
 * Intents of the plan outline, the list of rooms and walls that replaces the plan for screen reader users (UX section
 * 7, REQ-UX-008). A [PlanOutlineHandler] consumes them.
 */
sealed interface PlanOutlineIntent : EditorIntent {
    /** Opens the list of rooms and walls. */
    data object Open : PlanOutlineIntent

    /** Closes the list. */
    data object Dismiss : PlanOutlineIntent
}

/**
 * State of the plan outline, stored in `EditorUiState.sections`.
 *
 * @property open whether the list is shown.
 */
@Immutable
data class PlanOutlineState(
    val open: Boolean = false,
) : EditorSectionState

/** The entry of the top bar menu that opens the plan outline. */
object PlanOutlineMenu {
    /** String resource of the visible text and the spoken label. */
    @StringRes
    val label: Int = R.string.a11y_outline_menu

    /** What the entry sends. */
    val intent: PlanOutlineIntent = PlanOutlineIntent.Open
}
