package app.plansnap.feature.export.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.export.R

/**
 * Stateless export dialog of docs/UX.md section 1: format, scale, paper, preflight warnings, share and save.
 *
 * @param state What to show.
 * @param actions Reactions to user input.
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    actions: ExportActions,
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
        bottomBar = { if (state is ExportUiState.Ready) ActionBar(state, actions) },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when (state) {
            ExportUiState.Loading -> Centered(content) { CircularProgressIndicator() }
            is ExportUiState.Empty -> Centered(content) { Hint(stringResource(R.string.export_empty_hint)) }
            is ExportUiState.Failed -> FailedContent(state, actions, content)
            is ExportUiState.Ready -> ReadyContent(state, actions, content)
        }
    }
}

@Composable
private fun Centered(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(PlanSnapTheme.spacing.l),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun FailedContent(
    state: ExportUiState.Failed,
    actions: ExportActions,
    modifier: Modifier,
) {
    Column(modifier = modifier.padding(PlanSnapTheme.spacing.l)) {
        MessageCard(
            title = stringResource(R.string.export_error_title),
            message = stringResource(errorMessage(state.error)),
            action = stringResource(R.string.export_retry),
            onAction = actions.onRetryLoad,
            onDismiss = null,
        )
    }
}

@Composable
private fun ReadyContent(
    state: ExportUiState.Ready,
    actions: ExportActions,
    modifier: Modifier,
) {
    val spacing = PlanSnapTheme.spacing
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.l, vertical = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        state.error?.let {
            MessageCard(
                title = stringResource(R.string.export_error_title),
                message = stringResource(errorMessage(it)),
                action = stringResource(R.string.export_retry),
                onAction = actions.onRetry,
                onDismiss = actions.onErrorDismissed,
            )
        }
        state.notice?.let { notice ->
            when (notice) {
                is ExportNotice.Saved -> {
                    MessageCard(
                        title = null,
                        message = stringResource(R.string.export_saved, stringResource(formatLabel(notice.format))),
                        action = null,
                        onAction = {},
                        onDismiss = actions.onNoticeDismissed,
                        icon = PlanSnapIcons.Check,
                        color = PlanSnapTheme.colors.success,
                    )
                }
            }
        }
        FormatSection(state.format, !state.isBusy, actions.onFormatSelected)
        if (state.isDrawingFormat) {
            PageSection(state, actions)
            WarningsSection(state.findings)
        }
    }
}

@Composable
private fun ActionBar(
    state: ExportUiState.Ready,
    actions: ExportActions,
) {
    val spacing = PlanSnapTheme.spacing
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            if (state.running != null) {
                Row(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(PlanSnapTheme.sizes.icon), strokeWidth = 2.dp)
                    Text(stringResource(R.string.export_running), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.fillMaxWidth()) {
                PlanSnapOutlinedButton(
                    text = stringResource(R.string.export_action_save),
                    onClick = actions.onSave,
                    enabled = state.canExport,
                    modifier = Modifier.weight(1f),
                )
                PlanSnapButton(
                    text = stringResource(R.string.export_action_share),
                    onClick = actions.onShare,
                    enabled = state.canExport,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
