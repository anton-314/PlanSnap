package app.plansnap.feature.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme

/** One action of the top bar, shown as a text button or as an entry of the overflow menu. */
private class TopBarAction(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * Top bar of the project list. The title always keeps one line; the actions that do not fit beside it at the current
 * width and font scale move into an overflow menu, the import before the settings.
 *
 * @param isImporting Whether an import runs, which disables the import action.
 * @param actions Callbacks for user actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsTopBar(
    isImporting: Boolean,
    actions: ProjectsScreenActions,
) {
    val title = stringResource(R.string.projects_title)
    // Ordered so that the first entry is the first to move into the overflow menu.
    val barActions =
        listOf(
            TopBarAction(stringResource(R.string.projects_import), !isImporting, actions.onImport),
            TopBarAction(stringResource(R.string.projects_open_settings), true, actions.onOpenSettings),
        )
    // Title and actions share the title slot: only there is the width that both have to fit into known.
    TopAppBar(title = { TitleWithActions(title, barActions) })
}

@Composable
private fun TitleWithActions(
    title: String,
    actions: List<TopBarAction>,
) {
    val titleGap = with(LocalDensity.current) { PlanSnapTheme.spacing.s.roundToPx() }
    val titleWidth = textWidth(title, LocalTextStyle.current)
    val buttonWidths = actions.map { textButtonWidth(it.label) }
    val overflowWidth = with(LocalDensity.current) { PlanSnapTheme.sizes.minTouchTarget.roundToPx() }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(end = PlanSnapTheme.spacing.xs)) {
        val available = constraints.maxWidth - titleWidth - titleGap
        val inline = inlineActionCount(buttonWidths, overflowWidth, available)
        val overflowing = actions.dropLast(inline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            actions.takeLast(inline).forEach { action ->
                TextButton(onClick = action.onClick, enabled = action.enabled) { Text(action.label) }
            }
            if (overflowing.isNotEmpty()) {
                OverflowMenu(overflowing)
            }
        }
    }
}

/**
 * Number of actions, counted from the end of the list, that fit into [available] pixels. As long as one action is left
 * over, the overflow button needs room as well.
 */
private fun inlineActionCount(
    widths: List<Int>,
    overflowWidth: Int,
    available: Int,
): Int =
    (widths.size downTo 1).firstOrNull { count ->
        val overflow = if (count < widths.size) overflowWidth else 0
        widths.takeLast(count).sum() + overflow <= available
    } ?: 0

@Composable
private fun textWidth(
    text: String,
    style: TextStyle,
): Int =
    rememberTextMeasurer()
        .measure(text = text, style = style, softWrap = false, maxLines = 1)
        .size
        .width

/** Width in pixels that a Material text button with [label] takes. */
@Composable
private fun textButtonWidth(label: String): Int {
    val labelWidth = textWidth(label, MaterialTheme.typography.labelLarge)
    val padding = ButtonDefaults.TextButtonContentPadding
    val direction = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        val horizontal =
            padding.calculateLeftPadding(direction).roundToPx() + padding.calculateRightPadding(direction).roundToPx()
        maxOf(ButtonDefaults.MinWidth.roundToPx(), labelWidth + horizontal)
    }
}

@Composable
private fun OverflowMenu(actions: List<TopBarAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        PlanSnapIconButton(
            icon = MoreIcon,
            contentDescription = stringResource(R.string.projects_overflow),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                    enabled = action.enabled,
                    modifier = Modifier.sizeIn(minHeight = PlanSnapTheme.sizes.minTouchTarget),
                )
            }
        }
    }
}
