package app.plansnap.feature.projects.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.projects.R

/**
 * Public entry point of the project list feature: the screen frame of docs/UX.md section 1 with the empty state.
 *
 * Project content, creation and import follow with the list implementation (T-PRJ-001); the import file dialog is an
 * ActivityResult contract and not a destination.
 *
 * @param actions Navigation callbacks supplied by the app.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsEntry(
    actions: ProjectsNavigationActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                actions = {
                    TextButton(onClick = actions.onOpenSettings) {
                        Text(stringResource(R.string.projects_open_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(PlanSnapTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.projects_empty_explanation),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            // Creating a project belongs to the list implementation (T-PRJ-001), so the action is not yet available.
            PlanSnapButton(
                text = stringResource(R.string.projects_new_project),
                onClick = {},
                enabled = false,
            )
        }
    }
}
