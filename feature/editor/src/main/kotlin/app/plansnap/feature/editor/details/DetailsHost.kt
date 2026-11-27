package app.plansnap.feature.editor.details

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The details sheet or the area overview that is open above the editor, if any (UX section 1). Both read
 * [DetailsState] and the editor state and report their taps as intents.
 *
 * A room that disappears while its sheet is open (for example by undo) closes the sheet instead of editing a room that
 * is gone.
 *
 * @param state state of the editor.
 * @param onIntent receives the edits and taps of the sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    val sheet = state.section(DetailsState::class)?.sheet ?: return
    val project = state.project
    val room =
        (sheet as? DetailsSheet.RoomDetails)?.let { open ->
            project?.rooms?.firstOrNull { it.id == open.roomId }
        }
    if (project == null || (sheet is DetailsSheet.RoomDetails && room == null)) {
        LaunchedEffect(Unit) { onIntent(DetailsIntent.Dismiss) }
        return
    }
    ModalBottomSheet(
        onDismissRequest = { onIntent(DetailsIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when (sheet) {
            is DetailsSheet.RoomDetails -> {
                RoomDetailsContent(sheet, state.settings.lengthUnit, project.defaultHeight, onIntent)
            }

            is DetailsSheet.Overview -> {
                val overview =
                    remember(state.project, state.evaluations, state.evaluationErrors) { areaOverviewOf(state) }
                if (overview != null) AreaOverviewContent(overview, sheet, state.settings.lengthUnit, onIntent)
            }
        }
    }
}
