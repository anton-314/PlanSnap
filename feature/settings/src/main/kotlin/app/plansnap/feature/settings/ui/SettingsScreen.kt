package app.plansnap.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.settings.R

/**
 * Stateless settings screen of docs/UX.md section 1. Every change is reported at once through [actions].
 *
 * @param state What to show.
 * @param actions Reactions to user input.
 * @param onBack Returns to the previous screen.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
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
        when (state) {
            SettingsUiState.Loading -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {}
            is SettingsUiState.Loaded -> SettingsContent(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Loaded,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val spacing = PlanSnapTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        state.saveError?.let {
            SaveErrorBanner(
                error = it,
                onDismiss = actions.onSaveErrorDismissed,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.l, vertical = spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            UnitSection(state.unit, actions.onUnitSelected)
            ToleranceSection(state, actions)
            GridSection(state, actions)
            HintsSection(state.dismissedHintCount, actions.onResetHints)
        }
    }
}

@Composable
internal fun Section(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
internal fun Description(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SaveErrorBanner(
    error: DomainError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PlanSnapTheme.spacing
    val color = PlanSnapTheme.colors.warning
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(spacing.m),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = PlanSnapIcons.Warning, contentDescription = null, tint = color)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_save_failed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
                Text(text = stringResource(saveErrorMessage(error)), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
            ) { Text(stringResource(R.string.settings_save_failed_dismiss)) }
        }
    }
}

@StringRes
private fun saveErrorMessage(error: DomainError): Int =
    when {
        error is DomainError.Storage && error.problem == StorageProblem.NO_SPACE -> {
            R.string.settings_save_failed_no_space
        }

        error is DomainError.Storage -> {
            R.string.settings_save_failed_storage
        }

        error is DomainError.InvalidValue -> {
            R.string.settings_save_failed_rejected
        }

        else -> {
            R.string.settings_save_failed_generic
        }
    }
