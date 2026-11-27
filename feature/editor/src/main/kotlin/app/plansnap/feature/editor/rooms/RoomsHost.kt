package app.plansnap.feature.editor.rooms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.a11y.PlanSnapDragHandle
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The surfaces of the rooms function above the editor: the dock sheet, or, while the host is chosen on the plan, a
 * small hint bar instead of the sheet so the plan stays tappable (route those taps through [roomsTapIntent]).
 *
 * The bar takes no more than the height it gets: its text scrolls and the cancel button stays reachable. While it is
 * shown, the system back action returns to the sheet.
 *
 * The sheet closes itself if its room or the project is gone. Without a dock in progress the composable emits
 * nothing, so it can stay in the screen permanently.
 *
 * @param state state of the editor.
 * @param onIntent receives the taps.
 * @param modifier places the hint bar. Put the host in a `Box` that covers the plan, with `Alignment.TopCenter`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.section(RoomsState::class)?.dock ?: return
    val known = state.project?.rooms?.any { it.id == draft.guestId } == true
    if (!known) {
        LaunchedEffect(Unit) { onIntent(RoomsIntent.Dismiss) }
        return
    }
    if (draft.picking) {
        PickingBar(onIntent, modifier)
    } else {
        ModalBottomSheet(
            onDismissRequest = { onIntent(RoomsIntent.Dismiss) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { PlanSnapDragHandle() },
        ) {
            DockSheetContent(draft, state, onIntent)
        }
    }
}

@Composable
private fun PickingBar(
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier,
) {
    // Back ends only the selection on the plan, not the editor.
    BackHandler { onIntent(RoomsIntent.StopPicking) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(PlanSnapTheme.spacing.s),
        shape = MaterialTheme.shapes.large,
        tonalElevation = BAR_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(PlanSnapTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rooms_dock_picking_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
            PlanSnapOutlinedButton(
                text = stringResource(R.string.rooms_dock_cancel),
                onClick = { onIntent(RoomsIntent.StopPicking) },
            )
        }
    }
}

private val BAR_ELEVATION = 6.dp
