package app.plansnap.feature.editor.shape

import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.a11y.PlanSnapDragHandle
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The sheet that is open above the editor, if any (UX section 1): angle, diagonal, thickness or adjustment, and the
 * dialog for a large correction above the adjustment. All of them read [ShapeState] from the editor state and report
 * their taps as intents.
 *
 * The adjustment sheet is bound to a proposal. As soon as the selected room has none (it was accepted, or a newer
 * evaluation no longer needs one), the sheet closes itself instead of showing a stale proposal.
 *
 * @param state state of the editor.
 * @param onIntent receives the taps of the sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeSheetHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    val shape = state.section(ShapeState::class)
    val sheet = shape?.sheet
    val room = state.selectedRoom
    val evaluation = room?.let { state.evaluations[it.id] as? RoomEvaluation.NeedsAdjustment }
    val unit = state.settings.lengthUnit
    if (shape == null || sheet == null) return
    if (room == null || (sheet == ShapeSheet.Adjustment && evaluation == null)) {
        LaunchedEffect(Unit) { onIntent(ShapeIntent.Dismiss) }
        return
    }
    ModalBottomSheet(
        onDismissRequest = { onIntent(ShapeIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { PlanSnapDragHandle() },
    ) {
        when (sheet) {
            is ShapeSheet.Corner -> CornerSheetContent(sheet, room, shape, onIntent)
            is ShapeSheet.Diagonal -> DiagonalSheetContent(sheet, room, unit, shape, onIntent)
            is ShapeSheet.Thickness -> ThicknessSheetContent(sheet, unit, shape, onIntent)
            ShapeSheet.Adjustment -> if (evaluation != null) AdjustmentSheetContent(room, evaluation, unit, onIntent)
        }
    }
    if (shape.explicitConfirmation && evaluation != null) {
        BasicAlertDialog(onDismissRequest = { onIntent(ShapeIntent.CancelExplicitConfirmation) }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = DIALOG_ELEVATION) {
                ExplicitConfirmationContent(room, evaluation, unit, onIntent)
            }
        }
    }
}

private val DIALOG_ELEVATION = 6.dp
