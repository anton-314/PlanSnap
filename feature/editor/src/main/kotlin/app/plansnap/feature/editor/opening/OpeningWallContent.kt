package app.plansnap.feature.editor.opening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.component.PlanSnapStatusBadge
import app.plansnap.core.designsystem.component.ShapeStatus
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.SheetTitle
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Content of the wall sheet: the openings of one wall, each opens its form, and the actions that add a door, a
 * window or a passage.
 *
 * @param room room of the wall.
 * @param sheet the open sheet.
 * @param statuses status of every opening from the evaluation of the room, empty if it has none.
 * @param unit unit of the shown lengths.
 * @param onIntent receives the taps.
 */
@Composable
internal fun WallSheetContent(
    room: Room,
    sheet: OpeningSheet.Wall,
    statuses: Map<OpeningId, OpeningStatus>,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val number = room.walls.indexOfFirst { it.id == sheet.wallId } + 1
    val openings = room.openings.filter { it.wallId == sheet.wallId }.sortedBy { it.offset }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        SheetTitle(stringResource(R.string.opening_wall_title, number), stringResource(R.string.opening_wall_subtitle))
        if (openings.isEmpty()) {
            Text(
                text = stringResource(R.string.opening_wall_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        openings.forEach { opening ->
            OpeningRow(opening, statuses[opening.id], unit) { onIntent(OpeningIntent.Edit(room.id, opening.id)) }
        }
        OpeningType.entries.forEach { type ->
            PlanSnapOutlinedButton(
                text = stringResource(addLabel(type)),
                onClick = { onIntent(OpeningIntent.Add(room.id, sheet.wallId, type)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OpeningRow(
    opening: Opening,
    status: OpeningStatus?,
    unit: LengthUnit,
    onClick: () -> Unit,
) {
    val resources = rememberResources()
    val summary =
        stringResource(
            R.string.opening_row_summary,
            stringResource(typeRes(opening.type)),
            resources.lengthLabel(opening.width, unit),
            resources.lengthLabel(opening.offset, unit),
        )
    val edit = stringResource(R.string.opening_row_edit)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_ROW_HEIGHT)
                .clickable(onClickLabel = edit, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = PlanSnapTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
    ) {
        Text(text = summary, style = MaterialTheme.typography.bodyLarge)
        if (status != null) FitBadge(status)
    }
}

/** Whether the opening fits, as symbol and text (UX section 6). */
@Composable
private fun FitBadge(status: OpeningStatus) {
    when (status) {
        OpeningStatus.VALID -> {
            PlanSnapStatusBadge(ShapeStatus.Confirmed, stringResource(R.string.opening_status_valid))
        }

        OpeningStatus.OUT_OF_BOUNDS -> {
            PlanSnapStatusBadge(ShapeStatus.Conflicting, stringResource(R.string.opening_status_out_of_bounds))
        }
    }
}

private val MIN_ROW_HEIGHT = 56.dp
