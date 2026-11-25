package app.plansnap.feature.editor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.canvas.PlanCanvas
import app.plansnap.feature.editor.input.ExtraKey
import app.plansnap.feature.editor.screen.EditorActions
import app.plansnap.feature.editor.screen.EditorScreen
import app.plansnap.feature.editor.shape.ShapeIntent
import app.plansnap.feature.editor.shape.ShapeSheetHost
import app.plansnap.feature.editor.shape.ShapeStatusCard
import app.plansnap.feature.editor.shape.errorMessage
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.EditorViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val STATUS_CARD_MAX_FRACTION = 0.5f

/**
 * Public entry point of the editor feature: the real editor with the plan, the input bar, the shape sheets and the
 * messages for rejected commands (docs/UX.md sections 1 to 3).
 *
 * The project to edit is read from the navigation arguments by [EditorViewModel]. The sheets inside the editor are
 * not destinations.
 *
 * @param actions Navigation callbacks supplied by the app.
 * @param modifier Modifier for the screen.
 */
@Composable
fun EditorEntry(
    actions: EditorNavigationActions,
    modifier: Modifier = Modifier,
) {
    val viewModel: EditorViewModel = hiltViewModel()
    EditorRoute(viewModel.state, viewModel.effects, viewModel::onIntent, actions, modifier)
}

/** The editor wired to its state and effects. [EditorEntry] supplies the view model, tests supply their own. */
@Composable
internal fun EditorRoute(
    state: StateFlow<EditorUiState>,
    effects: Flow<EditorEffect>,
    onIntent: (EditorIntent) -> Unit,
    actions: EditorNavigationActions,
    modifier: Modifier = Modifier,
) {
    val current by state.collectAsStateWithLifecycle()
    EditorContent(current, effects, onIntent, actions, modifier)
}

/** Stateless editor: screen frame, plan with status card, sheets and the snackbar for rejected commands. */
@Composable
internal fun EditorContent(
    state: EditorUiState,
    effects: Flow<EditorEffect>,
    onIntent: (EditorIntent) -> Unit,
    actions: EditorNavigationActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ErrorMessages(effects, snackbar)
    val extraKeys = rememberExtraKeys(onIntent)
    val editorActions =
        remember(onIntent, actions, extraKeys) {
            EditorActions(
                onIntent = onIntent,
                onBack = actions.onBack,
                onOpenExport = actions.onOpenExport,
                extraKeys = extraKeys,
            )
        }
    Box(modifier = modifier) {
        EditorScreen(
            state = state,
            actions = editorActions,
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { canvasModifier ->
            EditorCanvas(state, onIntent, canvasModifier)
        }
        ShapeSheetHost(state, onIntent)
    }
}

/** Shows every [EditorEffect.Error] as a snackbar with a message the user can act on, while the screen is visible. */
@Composable
private fun ErrorMessages(
    effects: Flow<EditorEffect>,
    snackbar: SnackbarHostState,
) {
    val resources by rememberUpdatedState(rememberResources())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                if (effect is EditorEffect.Error) {
                    launch { snackbar.showSnackbar(resources.errorMessage(effect.error)) }
                }
            }
        }
    }
}

/** The keys `D` and `Diag` in the right column of the keypad (UX section 2). */
@Composable
private fun rememberExtraKeys(onIntent: (EditorIntent) -> Unit): List<ExtraKey> {
    val thicknessLabel = stringResource(R.string.input_key_thickness_label)
    val thickness = stringResource(R.string.input_key_thickness)
    val diagonalLabel = stringResource(R.string.input_key_diagonal_label)
    val diagonal = stringResource(R.string.input_key_diagonal)
    return remember(onIntent, thicknessLabel, thickness, diagonalLabel, diagonal) {
        listOf(
            ExtraKey(thicknessLabel, thickness) { onIntent(ShapeIntent.OpenThickness) },
            ExtraKey(diagonalLabel, diagonal) { onIntent(ShapeIntent.OpenDiagonal()) },
        )
    }
}

/** The plan with the status card of the selected room below it, so the card never covers part of the plan. */
@Composable
private fun EditorCanvas(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier,
) {
    val room = state.selectedRoom
    BoxWithConstraints(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlanCanvas(
                scene = state.scene,
                view = state.view,
                onViewChange = { onIntent(CoreIntent.ViewChanged(it)) },
                onTap = { tap -> tap.target?.let { onIntent(CoreIntent.SelectRoom(it.roomId)) } },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                preview = state.preview,
                lengthUnit = state.settings.lengthUnit,
            )
            ShapeStatusCard(
                room = room,
                result = room?.let { evaluationOf(state, it.id) },
                unit = state.settings.lengthUnit,
                onIntent = onIntent,
                modifier =
                    Modifier
                        .padding(PlanSnapTheme.spacing.s)
                        .heightIn(max = this@BoxWithConstraints.maxHeight * STATUS_CARD_MAX_FRACTION),
            )
        }
    }
}

/** The evaluation of [roomId], or why there is none, `null` while the evaluation is pending. */
private fun evaluationOf(
    state: EditorUiState,
    roomId: RoomId,
): DomainResult<RoomEvaluation>? =
    state.evaluationErrors[roomId]?.let { DomainResult.Failure(it) }
        ?: state.evaluations[roomId]?.let { DomainResult.Success(it) }
