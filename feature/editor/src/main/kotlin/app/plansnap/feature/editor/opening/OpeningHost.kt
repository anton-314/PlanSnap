package app.plansnap.feature.editor.opening

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The opening sheet that is open above the editor, if any (UX section 1), and the dialog that asks for a deletion.
 * It reads [OpeningState] from the editor state and reports its taps as [OpeningIntent]s.
 *
 * A sheet is bound to its room, wall and opening. As soon as one of them is gone (an undo removed it, a newer state
 * no longer has it), the sheet closes itself instead of showing something that cannot be stored.
 *
 * @param state state of the editor.
 * @param onIntent receives the taps of the sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    val opening = state.section(OpeningState::class)
    val sheet = opening?.sheet
    if (opening == null || sheet == null) return
    val room = state.project?.rooms?.firstOrNull { it.id == sheet.roomId }
    val wallId = if (sheet is OpeningSheet.Form) sheet.draft.wallId else (sheet as OpeningSheet.Wall).wallId
    val gone =
        room == null ||
            room.walls.none { it.id == wallId } ||
            (sheet is OpeningSheet.Form && sheet.editingId != null && room.openings.none { it.id == sheet.editingId })
    if (gone) {
        LaunchedEffect(Unit) { onIntent(OpeningIntent.Dismiss) }
        return
    }
    val unit = state.settings.lengthUnit
    val statuses = state.evaluations[room.id]?.openingStatuses
    ModalBottomSheet(
        onDismissRequest = { onIntent(OpeningIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when (sheet) {
            is OpeningSheet.Wall -> WallSheetContent(room, sheet, statuses.orEmpty(), unit, onIntent)
            is OpeningSheet.Form -> OpeningFormContent(room, sheet, opening, unit, onIntent)
        }
    }
    if (opening.confirmDelete) {
        DeleteDialog(onIntent)
    }
}

@Composable
private fun DeleteDialog(onIntent: (EditorIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(OpeningIntent.CancelDelete) },
        title = { Text(stringResource(R.string.opening_delete_title)) },
        text = { Text(stringResource(R.string.opening_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onIntent(OpeningIntent.ConfirmDelete) }) {
                Text(stringResource(R.string.opening_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(OpeningIntent.CancelDelete) }) {
                Text(stringResource(R.string.opening_cancel))
            }
        },
    )
}
