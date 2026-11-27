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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.details.DetailsHost
import app.plansnap.feature.editor.details.rememberRoomTypeName
import app.plansnap.feature.editor.hints.HintsHost
import app.plansnap.feature.editor.input.ExtraKey
import app.plansnap.feature.editor.opening.OpeningHost
import app.plansnap.feature.editor.opening.openingTapIntent
import app.plansnap.feature.editor.rooms.roomGestureCallbacks
import app.plansnap.feature.editor.rooms.roomsCanvasPreview
import app.plansnap.feature.editor.rooms.roomsShowGrid
import app.plansnap.feature.editor.rooms.roomsTapIntent
import app.plansnap.feature.editor.screen.EditorActions
import app.plansnap.feature.editor.screen.EditorScreen
import app.plansnap.feature.editor.screen.PLAN_MIN_HEIGHT
import app.plansnap.feature.editor.shape.ShapeIntent
import app.plansnap.feature.editor.shape.ShapeSheetHost
import app.plansnap.feature.editor.shape.ShapeStatusCard
import app.plansnap.feature.editor.shape.StatusCardToggle
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

private const val STATUS_CARD_MAX_FRACTION = 0.35f
private val STATUS_CARD_MIN_HEIGHT = 64.dp

/** Height of the expanded header at normal font size; it grows with the font scale. */
private val STATUS_CARD_EXPANDED_HEADER_HEIGHT = 88.dp

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
        OpeningHost(state, onIntent)
        DetailsHost(state, onIntent)
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

/**
 * The plan with the status card of the selected room below it, so the card never covers part of the plan. The
 * overlays of [PlanOverlays] lie above both.
 */
@Composable
private fun EditorCanvas(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier,
) {
    val room = state.selectedRoom
    var userExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val roomGestures = remember(onIntent) { roomGestureCallbacks(onIntent) }
    var planHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = modifier) {
        val cardPadding = PlanSnapTheme.spacing.s
        val cardMaxHeight = statusCardMaxHeight(maxHeight, cardPadding * 2)
        val expanded = userExpanded ?: statusCardStartsExpanded(cardMaxHeight, LocalDensity.current.fontScale)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { planHeightPx = it.height }) {
                PlanCanvas(
                    scene = state.scene,
                    view = state.view,
                    onViewChange = { onIntent(CoreIntent.ViewChanged(it)) },
                    onTap = { tap -> planTapIntents(tap, state).forEach(onIntent) },
                    modifier = Modifier.fillMaxSize(),
                    preview = state.preview,
                    lengthUnit = state.settings.lengthUnit,
                    roomTypeName = rememberRoomTypeName(),
                    roomGestures = roomGestures,
                    roomPreview = roomsCanvasPreview(state),
                    showGrid = roomsShowGrid(state),
                )
                HintsHost(state, onIntent, Modifier.align(Alignment.TopCenter))
            }
            ShapeStatusCard(
                room = room,
                result = room?.let { evaluationOf(state, it.id) },
                unit = state.settings.lengthUnit,
                onIntent = onIntent,
                toggle = StatusCardToggle(expanded) { userExpanded = it },
                modifier =
                    Modifier
                        .padding(cardPadding)
                        .heightIn(max = cardMaxHeight),
            )
        }
        PlanOverlays(state, onIntent, with(LocalDensity.current) { planHeightPx.toDp() })
    }
}

/**
 * What a tap on the plan means, in order. While the host of a dock is being chosen the tap belongs to the rooms
 * function alone. Otherwise the tapped room is selected first, so it receives the input, and then the opening or wall
 * that was hit opens its sheet.
 */
internal fun planTapIntents(
    tap: PlanTap,
    state: EditorUiState,
): List<EditorIntent> =
    roomsTapIntent(tap, state)?.let(::listOf)
        ?: listOfNotNull(
            tap.target?.let { CoreIntent.SelectRoom(it.roomId) },
            openingTapIntent(tap, state),
        )

/** The evaluation of [roomId], or why there is none, `null` while the evaluation is pending. */
private fun evaluationOf(
    state: EditorUiState,
    roomId: RoomId,
): DomainResult<RoomEvaluation>? =
    state.evaluationErrors[roomId]?.let { DomainResult.Failure(it) }
        ?: state.evaluations[roomId]?.let { DomainResult.Success(it) }

/**
 * The most the status card may take of a plan area of [slot] with [padding] around the card: 35 percent of it, and
 * never so much that the plan falls below [PLAN_MIN_HEIGHT]. The card keeps [STATUS_CARD_MIN_HEIGHT], enough for its
 * collapsed header at 200 percent font, and scrolls inside itself when that is all it gets.
 */
internal fun statusCardMaxHeight(
    slot: Dp,
    padding: Dp,
): Dp = minOf(slot * STATUS_CARD_MAX_FRACTION, slot - PLAN_MIN_HEIGHT - padding).coerceAtLeast(STATUS_CARD_MIN_HEIGHT)

/**
 * Whether the status card starts expanded when it may be [cardMaxHeight] high: only if status, name and area of the
 * expanded header fit at [fontScale]. Otherwise it starts collapsed until the user expands it.
 */
internal fun statusCardStartsExpanded(
    cardMaxHeight: Dp,
    fontScale: Float,
): Boolean = cardMaxHeight >= STATUS_CARD_EXPANDED_HEADER_HEIGHT * fontScale
