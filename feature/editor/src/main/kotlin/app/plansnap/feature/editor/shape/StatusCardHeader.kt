package app.plansnap.feature.editor.shape

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapStatusBadge
import app.plansnap.core.designsystem.component.ShapeStatus
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R

/**
 * Collapse toggle of [ShapeStatusCard].
 *
 * @property expanded whether the body is shown.
 * @property onExpandedChange called with the new state when the user taps the header.
 */
class StatusCardToggle(
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
)

/** What the header of the card shows besides the room name: status and area. */
internal class CardHeader(
    val status: ShapeStatus,
    @StringRes val label: Int,
    val areaMm2: Double?,
)

/**
 * Header of the card. Expanded, status badge, name and area flow into further lines when the font is large, so none
 * is squeezed to a sliver. Collapsed, name, area, status symbol and status text form a compact header: one line when
 * it fits, otherwise the status moves to a second line. With the small label style those two lines are 64 dp high at
 * 200 percent font, the least the card ever gets. With a toggle, the whole header is the target and at least 48 dp
 * high.
 *
 * @param hasBody whether the body follows below, which then sits closer to the expanded header.
 */
@Composable
internal fun HeaderRow(
    room: Room,
    header: CardHeader,
    toggle: StatusCardToggle?,
    hasBody: Boolean,
) {
    val expanded = toggle?.expanded ?: true
    val name = room.name.takeIf { it.isNotBlank() }
    val area = header.areaMm2?.let { stringResource(R.string.shape_status_area, areaText(it)) }
    val stateText =
        stringResource(if (expanded) R.string.shape_card_state_expanded else R.string.shape_card_state_collapsed)
    val actionLabel =
        stringResource(if (expanded) R.string.shape_card_action_collapse else R.string.shape_card_action_expand)
    val padding =
        if (expanded) {
            Modifier.padding(
                start = PlanSnapTheme.spacing.m,
                top = PlanSnapTheme.spacing.m,
                end = PlanSnapTheme.spacing.m,
                bottom = if (hasBody) PlanSnapTheme.spacing.s else PlanSnapTheme.spacing.m,
            )
        } else {
            Modifier.padding(horizontal = PlanSnapTheme.spacing.s)
        }
    Row(
        modifier =
            Modifier
                .collapseToggle(toggle, stateText, actionLabel, PlanSnapTheme.sizes.minTouchTarget)
                .fillMaxWidth()
                .then(padding),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (expanded) {
            ExpandedSummary(header, name, area, Modifier.weight(1f))
        } else {
            CollapsedSummary(header, name, area, Modifier.weight(1f))
        }
        if (toggle != null) {
            Icon(
                imageVector = if (expanded) StatusCardIcons.ChevronUp else StatusCardIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(CHEVRON_SIZE),
            )
        }
    }
}

private fun Modifier.collapseToggle(
    toggle: StatusCardToggle?,
    stateText: String,
    actionLabel: String,
    minHeight: Dp,
): Modifier =
    if (toggle == null) {
        this
    } else {
        this
            .clickable(onClickLabel = actionLabel, role = Role.Button) { toggle.onExpandedChange(!toggle.expanded) }
            .semantics { stateDescription = stateText }
            .heightIn(min = minHeight)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedSummary(
    header: CardHeader,
    name: String?,
    area: String?,
    modifier: Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PlanSnapStatusBadge(status = header.status, label = stringResource(header.label))
        if (name != null) Text(text = name, style = MaterialTheme.typography.titleSmall)
        if (area != null) Text(text = area, style = MaterialTheme.typography.titleSmall)
    }
}

/** A long name is cut off with an ellipsis; the area and the status stay complete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsedSummary(
    header: CardHeader,
    name: String?,
    area: String?,
    modifier: Modifier,
) {
    val style = MaterialTheme.typography.labelMedium
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (name != null || area != null) {
            Row {
                if (name != null) {
                    Text(
                        text = name,
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (area != null) {
                    val separator = if (name != null) stringResource(R.string.shape_card_summary_separator) else ""
                    Text(text = separator + area, style = style, maxLines = 1)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = header.status.symbol(),
                contentDescription = null,
                tint = header.status.accent(),
                modifier = Modifier.size(PlanSnapTheme.sizes.statusIcon),
            )
            Text(text = stringResource(header.label), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShapeStatus.accent(): Color =
    when (this) {
        ShapeStatus.Confirmed -> PlanSnapTheme.colors.success
        ShapeStatus.Unconfirmed -> PlanSnapTheme.colors.warning
        ShapeStatus.Conflicting -> PlanSnapTheme.colors.conflict
    }

private fun ShapeStatus.symbol(): ImageVector =
    when (this) {
        ShapeStatus.Confirmed -> PlanSnapIcons.Check
        ShapeStatus.Unconfirmed -> PlanSnapIcons.Warning
        ShapeStatus.Conflicting -> PlanSnapIcons.Conflict
    }

private val CHEVRON_SIZE = 24.dp
