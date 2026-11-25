package app.plansnap.feature.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction

/** Renders the open [dialog] of the project list; nothing for [ProjectsDialog.None]. */
@Composable
internal fun ProjectsDialogs(
    dialog: ProjectsDialog,
    actions: ProjectsScreenActions,
) {
    when (dialog) {
        ProjectsDialog.None -> {}

        is ProjectsDialog.Create -> {
            NameDialog(
                title = R.string.projects_dialog_create_title,
                confirm = R.string.projects_dialog_create_confirm,
                name = dialog.name,
                actions = actions,
            )
        }

        is ProjectsDialog.Rename -> {
            NameDialog(
                title = R.string.projects_dialog_rename_title,
                confirm = R.string.projects_dialog_rename_confirm,
                name = dialog.name,
                actions = actions,
            )
        }

        is ProjectsDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = actions.onDialogDismissed,
                title = { Text(stringResource(R.string.projects_dialog_delete_title)) },
                text = { Text(stringResource(R.string.projects_dialog_delete_message, dialog.name)) },
                confirmButton = {
                    TextButton(onClick = actions.onDialogConfirmed) {
                        Text(stringResource(R.string.projects_dialog_delete_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDialogDismissed) {
                        Text(stringResource(R.string.projects_dialog_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun NameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    name: String,
    actions: ProjectsScreenActions,
) {
    val focusRequester = remember { FocusRequester() }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = actions.onDialogDismissed,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = actions.onNameChanged,
                label = { Text(stringResource(R.string.projects_dialog_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) actions.onDialogConfirmed() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onDialogConfirmed, enabled = valid) { Text(stringResource(confirm)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDialogDismissed) { Text(stringResource(R.string.projects_dialog_cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
