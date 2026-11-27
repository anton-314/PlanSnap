package app.plansnap.feature.editor.hints

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.feature.editor.canvas.PlanCanvas
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.navigation.statusCardMaxHeight
import app.plansnap.feature.editor.navigation.statusCardStartsExpanded
import app.plansnap.feature.editor.save.SaveHost
import app.plansnap.feature.editor.screen.EditorActions
import app.plansnap.feature.editor.screen.EditorScreen
import app.plansnap.feature.editor.shape.ShapeStatusCard
import app.plansnap.feature.editor.shape.StatusCardToggle
import app.plansnap.feature.editor.shape.almostRectangle
import app.plansnap.feature.editor.shape.evaluate
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.shape.underdeterminedRoom
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentMapOf

/** Tags of the areas a hint must stay in or leave uncovered. */
internal const val PLAN_TAG = "hints-plan"
internal const val CARD_TAG = "hints-card"

/** The situations of the editor that have a hint, as states with the real evaluation and scene. */
internal enum class Situation {
    EMPTY,
    ONE_WALL,
    THREE_WALLS,
    PROPOSAL,
    GAP,
    ;

    fun state(): EditorUiState {
        val state =
            when (this) {
                EMPTY -> evaluated(editorState())
                ONE_WALL -> evaluated(editorState(walls = 1))
                THREE_WALLS -> evaluated(editorState(walls = 3))
                PROPOSAL -> stateOf(underdeterminedRoom())
                GAP -> stateOf(almostRectangle(3030))
            }
        val scene = DefaultSceneBuilder().build(state.project!!, state.evaluations, SceneOptions())
        return state.copy(scene = scene)
    }

    private fun evaluated(state: EditorUiState): EditorUiState {
        val room = state.selectedRoom!!
        return state.copy(evaluations = persistentMapOf(room.id to evaluate(room)))
    }
}

/**
 * The editor screen with [HintsHost] mounted the way the host has to mount it: over a box that holds the plan canvas
 * only, above the status card, with the failed-save message over the whole plan area.
 */
@Composable
internal fun HintsEditorHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    fontScale: Float = 1f,
    darkTheme: Boolean = false,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        PlanSnapTheme(darkTheme = darkTheme) {
            EditorScreen(state = state, actions = EditorActions(onIntent, onBack = {}, onOpenExport = {})) { slot ->
                Box(modifier = slot) {
                    PlanWithCard(state, onIntent, Modifier.fillMaxSize())
                    SaveHost(state, onIntent, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun PlanWithCard(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier,
) {
    val room = state.selectedRoom
    BoxWithConstraints(modifier = modifier) {
        val cardPadding = PlanSnapTheme.spacing.s
        val cardMaxHeight = statusCardMaxHeight(maxHeight, cardPadding * 2)
        val expanded = statusCardStartsExpanded(cardMaxHeight, LocalDensity.current.fontScale)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().testTag(PLAN_TAG)) {
                PlanCanvas(
                    scene = state.scene,
                    view = state.view,
                    onViewChange = {},
                    onTap = {},
                    modifier = Modifier.fillMaxSize(),
                )
                HintsHost(state, onIntent, Modifier.align(Alignment.TopCenter))
            }
            ShapeStatusCard(
                room = room,
                result = room?.let { state.evaluations[it.id] }?.let { DomainResult.Success(it) },
                unit = state.settings.lengthUnit,
                onIntent = onIntent,
                toggle = StatusCardToggle(expanded) {},
                modifier = Modifier.padding(cardPadding).heightIn(max = cardMaxHeight).testTag(CARD_TAG),
            )
        }
    }
}
