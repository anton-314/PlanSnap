package app.plansnap.feature.editor.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.feature.editor.shape.almostRectangle
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.emptyFlow

/**
 * A room of four walls with a gap, drawn by the real scene builder, and a typed value, with or without the status
 * card of the evaluated gap.
 */
internal fun layoutState(withCard: Boolean): EditorUiState {
    val state = stateOf(almostRectangle(3030)) { copy(inputText = "345") }
    val open =
        state.project!!
            .rooms
            .single()
            .copy(isClosed = false)
    val project = state.project!!.copy(rooms = persistentListOf(open))
    val scene = DefaultSceneBuilder().build(project, state.evaluations, SceneOptions())
    val shown = state.copy(project = project, scene = scene)
    return if (withCard) shown else shown.copy(evaluations = persistentMapOf())
}

/** The editor content at [fontScale] with the real plan canvas and no-op callbacks. */
@Composable
internal fun LayoutHost(
    state: EditorUiState,
    fontScale: Float,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        PlanSnapTheme {
            EditorContent(state, emptyFlow(), {}, EditorNavigationActions({}, {}))
        }
    }
}
