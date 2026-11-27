package app.plansnap.feature.editor.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.input.Keypad
import app.plansnap.feature.editor.input.KeypadActions
import app.plansnap.feature.editor.input.KeypadText
import app.plansnap.feature.editor.shape.SheetTitle
import app.plansnap.feature.editor.shape.lengthText
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.shape.unitRes
import app.plansnap.feature.editor.shape.unitSpokenRes
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import java.text.DecimalFormatSymbols
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * Content of the dock sheet (UX section 3, CR-W2-03): the host room, its wall or opening, the own wall, the direction
 * of the host wall and the offset with its keypad. The offset starts at 0, flush with the start of the host wall or
 * opening, and counts in the measuring direction of the host wall, which the sheet states in words.
 *
 * @param draft what the user chose so far.
 * @param state the editor state, which supplies rooms, evaluations and the unit.
 * @param onIntent receives the taps.
 */
@Composable
internal fun DockSheetContent(
    draft: DockDraft,
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    val rooms = state.project?.rooms.orEmpty()
    val guest = rooms.firstOrNull { it.id == draft.guestId } ?: return
    val resources = rememberResources()
    val host = rooms.firstOrNull { it.id == draft.hostRoomId }
    val unit = state.settings.lengthUnit
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        SheetTitle(
            title = stringResource(R.string.rooms_dock_title),
            subtitle = stringResource(R.string.rooms_dock_guest, resources.roomLabel(guest, rooms.indexOf(guest))),
        )
        HostSection(draft, state, onIntent)
        if (host != null) {
            DirectionNote(draft, host, unit)
        }
        OwnWallSection(draft, guest, unit, onIntent)
        OffsetSection(draft, unit, onIntent)
        DockMessages(draft, state, host, guest)
        DockKeypad(draft, onIntent)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun HostSection(
    draft: DockDraft,
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    val rooms = state.project?.rooms.orEmpty()
    val unit = state.settings.lengthUnit
    val host = rooms.firstOrNull { it.id == draft.hostRoomId }
    SectionLabel(stringResource(R.string.rooms_dock_host_room))
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        items(DockDrafts.hostsFor(state, draft.guestId), key = { it.id.value }) { room ->
            SelectableChip(
                label = resources.roomLabel(room, rooms.indexOf(room)),
                selected = room.id == draft.hostRoomId,
                description = null,
                onClick = { onIntent(RoomsIntent.SelectHostRoom(room.id)) },
            )
        }
    }
    if (host != null) {
        SectionLabel(stringResource(R.string.rooms_dock_host_wall))
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        ) {
            items(host.walls.size, key = { host.walls[it].id.value }) { index ->
                val wall = host.walls[index]
                val length = resources.lengthText(wall.length.mm.toDouble(), unit)
                SelectableChip(
                    label = stringResource(R.string.rooms_dock_wall, index + 1, length),
                    selected = wall.id == draft.hostWallId && draft.hostOpeningId == null,
                    description = stringResource(R.string.rooms_dock_wall_description, index + 1, length),
                    onClick = { onIntent(RoomsIntent.SelectHost(host.id, wall.id)) },
                )
            }
            items(host.openings, key = { "o" + it.id.value }) { opening ->
                val wallNumber = host.walls.indexOfFirst { it.id == opening.wallId } + 1
                val start = resources.lengthText(opening.offset.mm.toDouble(), unit)
                SelectableChip(
                    label = resources.openingLabel(opening, wallNumber, start),
                    selected = opening.id == draft.hostOpeningId,
                    description = null,
                    onClick = { onIntent(RoomsIntent.SelectHost(host.id, opening.wallId, opening.id)) },
                )
            }
        }
    }
    PlanSnapOutlinedButton(
        text = stringResource(R.string.rooms_dock_pick),
        onClick = { onIntent(RoomsIntent.StartPicking) },
    )
}

@Composable
private fun OwnWallSection(
    draft: DockDraft,
    guest: Room,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    SectionLabel(stringResource(R.string.rooms_dock_own_wall))
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        items(guest.walls.size, key = { guest.walls[it].id.value }) { index ->
            val wall = guest.walls[index]
            val length = resources.lengthText(wall.length.mm.toDouble(), unit)
            SelectableChip(
                label = stringResource(R.string.rooms_dock_own_wall_chip, index + 1),
                selected = wall.id == draft.ownWallId,
                description = stringResource(R.string.rooms_dock_own_wall_description, index + 1, length),
                onClick = { onIntent(RoomsIntent.SelectOwnWall(wall.id)) },
            )
        }
    }
}

/** States in which direction the host wall runs and what the offset counts from (DOMAIN section 10). */
@Composable
private fun DirectionNote(
    draft: DockDraft,
    host: Room,
    unit: LengthUnit,
) {
    val index = host.walls.indexOfFirst { it.id == draft.hostWallId }
    if (index < 0) return
    val resources = rememberResources()
    val from = index + 1
    // The end of the last wall of a closed room is corner 1 again, an open chain has one more corner.
    val to = if (host.isClosed) (index + 1) % host.walls.size + 1 else index + 2
    val opening = host.openings.firstOrNull { it.id == draft.hostOpeningId }
    val direction = stringResource(R.string.rooms_dock_direction_description, from, to)
    Column(verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.rooms_dock_direction_arrow, from, to),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            // The arrow reads badly, so a screen reader gets the direction in words.
            modifier = Modifier.semantics { contentDescription = direction },
        )
        Text(
            text = stringResource(R.string.rooms_dock_direction, index + 1, from, to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (opening != null) {
            Text(
                text =
                    stringResource(
                        R.string.rooms_dock_direction_opening,
                        resources.lengthText(opening.offset.mm.toDouble(), unit),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OffsetSection(
    draft: DockDraft,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val title = stringResource(R.string.rooms_dock_offset_title)
    val forward = stringResource(R.string.rooms_dock_offset_forward)
    val backward = stringResource(R.string.rooms_dock_offset_backward)
    val unitSpoken = stringResource(unitSpokenRes(unit))
    val shown = draft.offsetText.ifEmpty { DockDraft.DEFAULT_OFFSET_TEXT }
    val spoken =
        stringResource(
            R.string.rooms_dock_offset_spoken,
            shown,
            unitSpoken,
            if (draft.offsetNegative) backward else forward,
        )
    SectionLabel(title)
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        items(listOf(false, true)) { negative ->
            SelectableChip(
                label = if (negative) backward else forward,
                selected = draft.offsetNegative == negative,
                description = null,
                onClick = { onIntent(RoomsIntent.OffsetNegative(negative)) },
            )
        }
    }
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text =
                stringResource(
                    if (draft.offsetNegative) {
                        R.string.rooms_dock_offset_negative_value
                    } else {
                        R.string.rooms_dock_offset_positive_value
                    },
                    shown,
                ),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = stringResource(unitRes(unit)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DockMessages(
    draft: DockDraft,
    state: EditorUiState,
    host: Room?,
    guest: Room,
) {
    val resources = rememberResources()
    val problem =
        when {
            draft.offsetInvalid -> stringResource(R.string.rooms_dock_offset_invalid)
            draft.error != null -> resources.dockErrorMessage(draft.error)
            else -> null
        }
    if (problem != null) {
        Row(
            modifier =
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(PlanSnapIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val hint =
        when {
            !draft.isComplete -> stringResource(R.string.rooms_dock_incomplete)
            host != null && !hasShape(state, host, guest) -> stringResource(R.string.rooms_dock_pending)
            else -> stringResource(R.string.rooms_dock_offset_hint)
        }
    Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun hasShape(
    state: EditorUiState,
    host: Room,
    guest: Room,
): Boolean = state.evaluations[host.id]?.shape != null && state.evaluations[guest.id]?.shape != null

@Composable
private fun DockKeypad(
    draft: DockDraft,
    onIntent: (EditorIntent) -> Unit,
) {
    val locale = ComposeLocale.current.platformLocale
    val separator = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    Keypad(
        separator = separator,
        enabled = true,
        canConfirm = draft.canConfirm,
        extraKeys = emptyList(),
        actions =
            KeypadActions(
                onKey = { key ->
                    val next = KeypadText.apply(draft.offsetText, key, separator)
                    if (next != draft.offsetText) onIntent(RoomsIntent.OffsetChanged(next))
                },
                onConfirm = { onIntent(RoomsIntent.ConfirmDock) },
                confirmDescription = stringResource(R.string.rooms_dock_confirm),
            ),
    )
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    description: String?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon =
            if (selected) {
                { Icon(imageVector = PlanSnapIcons.Check, contentDescription = null) }
            } else {
                null
            },
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
    )
}
