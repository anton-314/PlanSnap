package app.plansnap.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme

/**
 * Status indicator that never relies on color alone: it always shows a symbol and the given [label].
 *
 * The badge does not group its semantics, so inside a clickable row or header a screen reader speaks the label as
 * part of that control.
 *
 * @param status State to display; selects color and symbol.
 * @param label Visible text, supplied by the caller from string resources.
 * @param modifier Modifier for the badge.
 */
@Composable
fun PlanSnapStatusBadge(
    status: ShapeStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlanSnapTheme.colors
    val spacing = PlanSnapTheme.spacing
    val accent: Color
    val icon: ImageVector
    when (status) {
        ShapeStatus.Confirmed -> {
            accent = colors.success
            icon = PlanSnapIcons.Check
        }

        ShapeStatus.Unconfirmed -> {
            accent = colors.warning
            icon = PlanSnapIcons.Warning
        }

        ShapeStatus.Conflicting -> {
            accent = colors.conflict
            icon = PlanSnapIcons.Conflict
        }
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(width = BORDER_WIDTH, color = accent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(PlanSnapTheme.sizes.statusIcon),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val BORDER_WIDTH = 2.dp
