package app.plansnap.feature.settings.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.feature.settings.R

/**
 * Public entry point of the settings feature: the screen frame of docs/UX.md section 1.
 *
 * The settings screen has no empty state. Its controls (unit, tolerance, grid, hints) follow with T-SET-001.
 *
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    PlanSnapIconButton(
                        icon = PlanSnapIcons.Back,
                        contentDescription = stringResource(R.string.settings_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}
