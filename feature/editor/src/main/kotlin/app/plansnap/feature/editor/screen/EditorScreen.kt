package app.plansnap.feature.editor.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.input.ExtraKey
import app.plansnap.feature.editor.input.InputBar
import app.plansnap.feature.editor.input.InputIcons
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.ReloadProject

private const val INPUT_AREA_MAX_FRACTION = 0.65f

/**
 * Taps of the editor screen.
 *
 * @property onIntent receives every intent of the input and the history.
 * @property onBack leaves the editor.
 * @property onOpenExport opens the export dialog.
 * @property extraKeys up to two further keys of the keypad, contributed by sub-functions of the editor.
 */
class EditorActions(
    val onIntent: (EditorIntent) -> Unit,
    val onBack: () -> Unit,
    val onOpenExport: () -> Unit,
    val extraKeys: List<ExtraKey> = emptyList(),
)

/**
 * Stateless frame of the editor in portrait (UX section 2): the top bar with back, undo, redo and export, the plan
 * in the upper part and the input bar in the thumb zone at the bottom. The input bar scrolls when it does not fit,
 * for example at a font scale of 200 percent.
 *
 * @param state state of the editor.
 * @param actions receives the taps of the screen.
 * @param modifier modifier of the screen.
 * @param snackbarHost host of one-time messages such as rejected commands.
 * @param canvas the drawing area; it receives the modifier that gives it the remaining space.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    canvas: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            EditorTopBar(state = state, actions = actions)
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Surface(modifier = Modifier.padding(padding).fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when (state.load) {
                EditorLoadState.Loading -> {
                    LoadingBody()
                }

                is EditorLoadState.Failed -> {
                    FailedBody(
                        onBack = actions.onBack,
                        onRetry = { actions.onIntent(ReloadProject) },
                    )
                }

                EditorLoadState.Loaded -> {
                    LoadedBody(state, actions.onIntent, actions.extraKeys, canvas)
                }
            }
        }
    }
}

@Composable
private fun LoadedBody(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    extraKeys: List<ExtraKey>,
    canvas: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            canvas(Modifier.weight(1f).fillMaxWidth())
            HorizontalDivider()
            InputBar(
                state = state,
                onIntent = onIntent,
                extraKeys = extraKeys,
                modifier =
                    Modifier
                        .heightIn(max = this@BoxWithConstraints.maxHeight * INPUT_AREA_MAX_FRACTION)
                        .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.editor_loading),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = PlanSnapTheme.spacing.l),
        )
    }
}

@Composable
private fun FailedBody(
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PlanSnapTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.editor_load_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        PlanSnapButton(text = stringResource(R.string.editor_retry), onClick = onRetry)
        TextButton(onClick = onBack) { Text(stringResource(R.string.editor_back_to_list)) }
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    actions: EditorActions,
) {
    val title = state.project?.name ?: stringResource(R.string.editor_title)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PlanSnapTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanSnapIconButton(
                icon = PlanSnapIcons.Back,
                contentDescription = stringResource(R.string.editor_back),
                onClick = actions.onBack,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() },
            )
            PlanSnapIconButton(
                icon = InputIcons.Undo,
                contentDescription = stringResource(R.string.editor_undo),
                onClick = { actions.onIntent(CoreIntent.Undo) },
                enabled = state.canUndo,
            )
            PlanSnapIconButton(
                icon = InputIcons.Redo,
                contentDescription = stringResource(R.string.editor_redo),
                onClick = { actions.onIntent(CoreIntent.Redo) },
                enabled = state.canRedo,
            )
            TextButton(onClick = actions.onOpenExport) { Text(stringResource(R.string.editor_open_export)) }
        }
    }
}
