package app.plansnap.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plansnap.feature.settings.ui.SettingsActions
import app.plansnap.feature.settings.ui.SettingsScreen
import app.plansnap.feature.settings.ui.SettingsViewModel

/**
 * Public entry point of the settings feature (docs/UX.md section 1): unit, closure tolerance, grid and hints.
 * Every change is stored at once.
 *
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@Composable
fun SettingsEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions =
            SettingsActions(
                onUnitSelected = viewModel::onUnitSelected,
                onAbsoluteToleranceChanged = viewModel::onAbsoluteToleranceChanged,
                onRelativeToleranceChanged = viewModel::onRelativeToleranceChanged,
                onGridChanged = viewModel::onGridChanged,
                onSnapChanged = viewModel::onSnapChanged,
                onResetHints = viewModel::onResetHints,
                onSaveErrorDismissed = viewModel::onSaveErrorDismissed,
            ),
        onBack = onBack,
        modifier = modifier,
    )
}
