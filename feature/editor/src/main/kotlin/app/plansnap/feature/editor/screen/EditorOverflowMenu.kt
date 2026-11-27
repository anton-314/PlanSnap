package app.plansnap.feature.editor.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.a11y.PlanOutlineMenu
import app.plansnap.feature.editor.details.DetailsIntent
import app.plansnap.feature.editor.details.DetailsMenu
import app.plansnap.feature.editor.input.InputIcons
import app.plansnap.feature.editor.opening.openingMenuEntries
import app.plansnap.feature.editor.rooms.roomsMenuItems
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The overflow menu of the top bar (UX section 2): openings, rooms, room details, area overview and export. The
 * entries of the sub-functions come from them as data; while the project is not loaded only export is offered.
 */
@Composable
internal fun EditorOverflowMenu(
    state: EditorUiState,
    actions: EditorActions,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        PlanSnapIconButton(
            icon = InputIcons.More,
            contentDescription = stringResource(R.string.editor_menu),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val select: (() -> Unit) -> Unit = { action ->
                open = false
                action()
            }
            if (state.project != null) {
                openingMenuEntries(state).forEach { entry ->
                    MenuEntry(entry.label, enabled = entry.enabled) { select { actions.onIntent(entry.intent) } }
                }
                HorizontalDivider()
                roomsMenuItems(state).forEach { item ->
                    MenuEntry(
                        item.label,
                        enabled = item.enabled,
                        checked = item.checked,
                    ) { select { actions.onIntent(item.intent) } }
                }
                HorizontalDivider()
                DetailsMenu.entries.forEach { entry ->
                    MenuEntry(
                        label = entry.label,
                        enabled = entry.intent !is DetailsIntent.OpenRoomDetails || state.selectedRoom != null,
                        modifier = Modifier.testTag(entry.testTag),
                    ) { select { actions.onIntent(entry.intent) } }
                }
                HorizontalDivider()
            }
            MenuEntry(R.string.editor_open_export) { select(actions.onOpenExport) }
            if (state.project != null) {
                MenuEntry(PlanOutlineMenu.label) { select { actions.onIntent(PlanOutlineMenu.intent) } }
            }
        }
    }
}

/** One entry of the menu; [checked] other than `null` makes it a switch that shows a check mark while on. */
@Composable
private fun MenuEntry(
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val toggle =
        if (checked == null) {
            Modifier
        } else {
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
        }
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        enabled = enabled,
        trailingIcon =
            if (checked == true) {
                { Icon(PlanSnapIcons.Check, contentDescription = null) }
            } else {
                null
            },
        modifier = modifier.then(toggle),
    )
}
