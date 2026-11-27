package app.plansnap.feature.editor.history

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.input.InputIcons
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The undo and redo buttons of the top bar (REQ-UX-005, UX section 2).
 *
 * Each button is enabled exactly while the history of the session has a step in its direction, as reported by
 * [EditorUiState.canUndo] and [EditorUiState.canRedo]. Both are disabled while the project is not loaded. A tap sends
 * [CoreIntent.Undo] or [CoreIntent.Redo]; restoring the project, drawing it and saving it is done by the editor.
 *
 * @param state state of the editor.
 * @param onIntent receives the taps.
 * @param modifier modifier of the row that holds both buttons.
 */
@Composable
fun HistoryButtons(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        PlanSnapIconButton(
            icon = InputIcons.Undo,
            contentDescription = stringResource(R.string.history_undo),
            onClick = { onIntent(CoreIntent.Undo) },
            enabled = state.canUndo,
        )
        PlanSnapIconButton(
            icon = InputIcons.Redo,
            contentDescription = stringResource(R.string.history_redo),
            onClick = { onIntent(CoreIntent.Redo) },
            enabled = state.canRedo,
        )
    }
}
