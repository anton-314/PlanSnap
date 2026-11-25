package app.plansnap.feature.export.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plansnap.core.export.ExportFormat
import app.plansnap.feature.export.R
import app.plansnap.feature.export.data.ShareIntents
import app.plansnap.feature.export.data.mimeType
import app.plansnap.feature.export.ui.ExportActions
import app.plansnap.feature.export.ui.ExportEvent
import app.plansnap.feature.export.ui.ExportScreen
import app.plansnap.feature.export.ui.ExportUiState
import app.plansnap.feature.export.ui.ExportViewModel

/**
 * Public entry point of the export feature (docs/UX.md section 1): format, scale and paper, warnings before the export,
 * sharing and saving.
 *
 * The project to export comes from the navigation argument `projectId`, which the export view model reads.
 *
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@Composable
fun ExportEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.export_share_chooser_title)
    val format = (state as? ExportUiState.Ready)?.format ?: ExportFormat.PDF
    val documentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) {
            viewModel.onDocumentChosen(it)
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Share -> context.startActivity(ShareIntents.chooser(event.request, chooserTitle))
                is ExportEvent.ChooseDocument -> documentPicker.launch(event.fileName)
            }
        }
    }
    ExportScreen(
        state = state,
        actions =
            ExportActions(
                onFormatSelected = viewModel::onFormatSelected,
                onScaleSelected = viewModel::onScaleSelected,
                onPaperSelected = viewModel::onPaperSelected,
                onShare = viewModel::onShare,
                onSave = viewModel::onSave,
                onRetry = viewModel::onRetry,
                onRetryLoad = viewModel::onRetryLoad,
                onErrorDismissed = viewModel::onErrorDismissed,
                onNoticeDismissed = viewModel::onNoticeDismissed,
            ),
        onBack = onBack,
        modifier = modifier,
    )
}
