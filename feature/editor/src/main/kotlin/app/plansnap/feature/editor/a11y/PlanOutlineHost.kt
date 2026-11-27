package app.plansnap.feature.editor.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.plansnap.core.designsystem.a11y.PlanSnapDragHandle
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.component.PlanSnapStatusBadge
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.ShapeStatus
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.ImmutableList
import app.plansnap.core.designsystem.component.ShapeStatus as BadgeStatus

/**
 * The list of rooms and walls above the editor, if it is open: the semantic alternative to the plan (UX section 7).
 * Activating an entry closes the list and does what a tap on that element of the plan does. Without an open list the
 * composable emits nothing, so it can stay in the screen permanently.
 *
 * @param state state of the editor.
 * @param onIntent receives the taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanOutlineHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    if (state.section(PlanOutlineState::class)?.open != true || state.project == null) return
    val resources = rememberResources()
    val entries =
        remember(state.project, state.evaluations, state.evaluationErrors, state.settings, state.selectedRoomId) {
            planOutline(state, resources)
        }
    ModalBottomSheet(
        onDismissRequest = { onIntent(PlanOutlineIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { PlanSnapDragHandle() },
    ) {
        PlanOutlineContent(entries, onIntent)
    }
}

/**
 * Content of the plan outline. It is a lazy list, so a project with many walls stays fluid.
 *
 * @param entries rooms with their walls, openings and diagonals.
 * @param onIntent receives the taps.
 */
@Composable
internal fun PlanOutlineContent(
    entries: ImmutableList<OutlineEntry>,
    onIntent: (EditorIntent) -> Unit,
) {
    val spacing = PlanSnapTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = spacing.l, end = spacing.l, bottom = spacing.l),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.a11y_outline_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = spacing.s).semantics { heading() },
            )
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.details_overview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(entries, key = { it.key }) { entry ->
            OutlineRow(entry) {
                onIntent(PlanOutlineIntent.Dismiss)
                entry.intents.forEach(onIntent)
            }
        }
        item(key = "close") {
            PlanSnapOutlinedButton(
                text = stringResource(R.string.a11y_outline_close),
                onClick = { onIntent(PlanOutlineIntent.Dismiss) },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.m),
            )
        }
    }
}

/**
 * One entry, read by a screen reader as one sentence. A room is a heading, so the user can jump from room to room; its
 * walls, openings and diagonals are indented below it.
 */
@Composable
private fun OutlineRow(
    entry: OutlineEntry,
    onClick: () -> Unit,
) {
    val spacing = PlanSnapTheme.spacing
    val room = entry.kind == OutlineKind.ROOM
    val action = actionRes(entry.kind)?.let { stringResource(it) }
    val clickable =
        if (action == null) {
            Modifier
        } else {
            Modifier.clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlanSnapTheme.sizes.minTouchTarget)
                .then(clickable)
                .semantics(mergeDescendants = true) {
                    if (room) {
                        heading()
                        selected = entry.selected
                    }
                }.padding(start = if (room) spacing.xs else spacing.l, top = spacing.s, bottom = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        entry.lines.forEachIndexed { index, line ->
            val badge = entry.status?.takeIf { index == STATUS_LINE }?.let(::badgeOf)
            if (badge != null) {
                PlanSnapStatusBadge(status = badge, label = line)
            } else {
                Text(
                    text = line,
                    style =
                        when {
                            index > 0 -> MaterialTheme.typography.bodyMedium
                            room -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                )
            }
        }
    }
}

/** Position of the status text in the lines of a room. */
private const val STATUS_LINE = 1

/** The badge of a room status, `null` for an open room, which has no shape to judge yet. */
private fun badgeOf(status: ShapeStatus): BadgeStatus? =
    when (status) {
        ShapeStatus.OPEN -> null
        ShapeStatus.CONFIRMED, ShapeStatus.ADJUSTED -> BadgeStatus.Confirmed
        ShapeStatus.NEEDS_ADJUSTMENT, ShapeStatus.PROPOSED -> BadgeStatus.Unconfirmed
        ShapeStatus.CONFLICT -> BadgeStatus.Conflicting
    }

private fun actionRes(kind: OutlineKind): Int? =
    when (kind) {
        OutlineKind.ROOM -> R.string.a11y_outline_action_select
        OutlineKind.WALL -> R.string.a11y_outline_action_wall
        OutlineKind.OPENING -> R.string.a11y_outline_action_opening
        OutlineKind.DIAGONAL -> null
    }
