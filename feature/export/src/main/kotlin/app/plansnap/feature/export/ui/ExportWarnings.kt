package app.plansnap.feature.export.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.export.R

@Composable
internal fun WarningsSection(
    findings: PreflightFindings,
    modifier: Modifier = Modifier,
) {
    if (!findings.hasFindings && !findings.isDrawingEmpty) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s)) {
        SectionTitle(stringResource(R.string.export_section_warnings))
        if (findings.isDrawingEmpty) {
            MessageCard(
                title = null,
                message = stringResource(R.string.export_warning_empty),
                action = null,
                onAction = {},
                onDismiss = null,
            )
        }
        if (findings.omittedRooms.isNotEmpty()) {
            val count = findings.omittedRooms.size
            Warning(
                pluralStringResource(R.plurals.export_warning_omitted, count, count, roomList(findings.omittedRooms)),
            )
        }
        if (findings.unconfirmedRooms.isNotEmpty()) {
            val count = findings.unconfirmedRooms.size
            Warning(
                pluralStringResource(
                    R.plurals.export_warning_unconfirmed,
                    count,
                    count,
                    roomList(findings.unconfirmedRooms),
                ),
            )
        }
        if (findings.outOfBoundsOpenings.isNotEmpty()) {
            val total = findings.outOfBoundsOpenings.sumOf { it.count }
            val rooms =
                findings.outOfBoundsOpenings
                    .map { stringResource(R.string.export_warning_opening_room, roomName(it.room), it.count) }
                    .joinToString(stringResource(R.string.export_room_list_separator))
            Warning(pluralStringResource(R.plurals.export_warning_openings, total, total, rooms))
        }
    }
}

@Composable
private fun roomName(room: RoomRef): String =
    room.name.ifBlank { stringResource(R.string.export_room_unnamed, room.position) }

@Composable
private fun roomList(rooms: List<RoomRef>): String =
    rooms.map { roomName(it) }.joinToString(stringResource(R.string.export_room_list_separator))

@Composable
private fun Warning(text: String) {
    MessageCard(title = null, message = text, action = null, onAction = {}, onDismiss = null)
}

/**
 * Bordered message with an icon, so that its meaning never rests on color alone.
 *
 * @param title optional headline.
 * @param message explanation.
 * @param action label of the optional action button.
 * @param onAction called by the action button.
 * @param onDismiss called by the dismiss button, `null` for a message that cannot be closed.
 */
@Suppress("LongParameterList") // Compose API: texts, actions and the optional look of the message.
@Composable
internal fun MessageCard(
    title: String?,
    message: String,
    action: String?,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = PlanSnapIcons.Warning,
    color: Color = PlanSnapTheme.colors.warning,
) {
    val spacing = PlanSnapTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.Top) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) Text(text = title, style = MaterialTheme.typography.titleSmall, color = color)
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (action != null || onDismiss != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onDismiss != null) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
                        ) { Text(stringResource(R.string.export_dismiss)) }
                    }
                    if (action != null) {
                        TextButton(
                            onClick = onAction,
                            modifier = Modifier.heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
                        ) { Text(action) }
                    }
                }
            }
        }
    }
}
