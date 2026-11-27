package app.plansnap.feature.export.ui.states

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.export.R
import app.plansnap.feature.export.ui.MessageCard
import app.plansnap.feature.export.ui.errorMessage

/**
 * Progress state of the export (docs/UX.md section 1) while the project is read and evaluated.
 *
 * @param modifier modifier of the state, the space below the top bar.
 */
@Composable
internal fun ExportLoadingState(modifier: Modifier = Modifier) {
    CenteredState(modifier) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.export_loading),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * State of a project without a room that has a wall: the hint "no room yet".
 *
 * @param documentRejected whether to explain that a chosen document was discarded.
 * @param modifier modifier of the state, the space below the top bar.
 */
@Composable
internal fun ExportEmptyState(
    documentRejected: Boolean,
    modifier: Modifier = Modifier,
) {
    CenteredState(modifier) {
        Text(
            text = stringResource(R.string.export_empty_hint),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (documentRejected) {
            MessageCard(
                title = stringResource(R.string.export_error_title),
                message = stringResource(R.string.export_error_document_rejected_empty),
                action = null,
                onAction = {},
                onDismiss = null,
            )
        }
    }
}

/**
 * Error state of a project that could not be read or evaluated.
 *
 * @param error failure to explain.
 * @param onRetry reads the project again.
 * @param modifier modifier of the state, the space below the top bar.
 */
@Composable
internal fun ExportLoadFailedState(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(PlanSnapTheme.spacing.l)) {
        MessageCard(
            title = stringResource(R.string.export_error_title),
            message = stringResource(errorMessage(error)),
            action = stringResource(R.string.export_retry),
            onAction = onRetry,
            onDismiss = null,
        )
    }
}

/** Centers [content] in the available space and lets it scroll when a small window or large font does not fit it. */
@Composable
private fun CenteredState(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
