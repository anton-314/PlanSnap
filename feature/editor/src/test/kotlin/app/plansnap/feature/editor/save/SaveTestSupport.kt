package app.plansnap.feature.editor.save

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.canvas.PlanCanvas
import app.plansnap.feature.editor.screen.EditorActions
import app.plansnap.feature.editor.screen.EditorScreen
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.SaveStatus
import kotlinx.coroutines.CompletableDeferred

internal val WRITE_FAILED = DomainError.Storage(StorageProblem.WRITE_FAILED)
internal val NO_SPACE = DomainError.Storage(StorageProblem.NO_SPACE)
internal val FAILED = SaveStatus.Failed(WRITE_FAILED)

/** Tag of the plan area, the slot that the editor screen gives to the canvas. */
internal const val PLAN_AREA_TAG = "save-plan-area"

/** Saver that holds every call while [gate] is set, so a test can look at the editor during a running save. */
internal class GatedSaver(
    private val delegate: ProjectSaver,
) : ProjectSaver {
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun save(
        project: Project,
        changedRooms: Set<RoomId>,
    ): DomainResult<Long> {
        gate?.await()
        return delegate.save(project, changedRooms)
    }
}

/**
 * The editor screen with the real plan canvas and [SaveHost] mounted the way the host has to mount it: on top of the
 * plan area, aligned to its upper edge.
 */
@Composable
internal fun SaveEditorHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    fontScale: Float = 1f,
    darkTheme: Boolean = false,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        PlanSnapTheme(darkTheme = darkTheme) {
            EditorScreen(state = state, actions = EditorActions(onIntent, onBack = {}, onOpenExport = {})) { slot ->
                Box(modifier = slot.testTag(PLAN_AREA_TAG)) {
                    PlanCanvas(
                        scene = state.scene,
                        view = state.view,
                        onViewChange = {},
                        onTap = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                    SaveHost(state, onIntent, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}
