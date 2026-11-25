package app.plansnap.feature.export.data

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Page that an export would choose, computed before the export so that the dialog can show it. */
fun interface PlotPreview {
    /**
     * Chooses scale, paper and orientation for the exportable rooms of [project] with the user's [selection].
     *
     * @return the plan, `NOTHING_TO_EXPORT` if no room is exportable, `PLAN_TOO_LARGE` or
     * `PLAN_DOES_NOT_FIT_SELECTION` as in `PlotLayout.layout`.
     */
    suspend fun plan(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        selection: PlotSelection,
    ): DomainResult<PlotPlan>
}

/** [PlotPreview] with the same scene bounds and layout the drawing exporters use, so that both always agree. */
class DefaultPlotPreview
    @Inject
    constructor(
        private val sceneBuilder: SceneBuilder,
        private val plotLayout: PlotLayout,
        private val dispatchers: DispatcherProvider,
    ) : PlotPreview {
        override suspend fun plan(
            project: Project,
            evaluations: ImmutableMap<RoomId, RoomEvaluation>,
            selection: PlotSelection,
        ): DomainResult<PlotPlan> =
            withContext(dispatchers.default) {
                val options = SceneOptions(exportableOnly = true)
                var bounds: WorldBounds? = null
                for (room in project.rooms) {
                    currentCoroutineContext().ensureActive()
                    val evaluation = evaluations.getValue(room.id)
                    val roomBounds =
                        evaluation
                            .takeIf { it.status.isExportable }
                            ?.let { sceneBuilder.buildRoom(room, it, options).bounds }
                            ?: continue
                    bounds = bounds?.union(roomBounds) ?: roomBounds
                }
                bounds?.let { plotLayout.layout(it, selection) }
                    ?: DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT))
            }
    }
