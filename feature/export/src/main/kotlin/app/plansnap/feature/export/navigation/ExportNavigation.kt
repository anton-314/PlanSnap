package app.plansnap.feature.export.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.export.R

/**
 * Public entry point of the export feature: the screen frame of docs/UX.md section 1 with the empty state.
 *
 * The project to export is read from the navigation arguments by the export ViewModel (T-EXP-002).
 *
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    PlanSnapIconButton(
                        icon = PlanSnapIcons.Back,
                        contentDescription = stringResource(R.string.export_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(PlanSnapTheme.spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.export_empty_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
