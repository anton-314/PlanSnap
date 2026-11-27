package app.plansnap.feature.editor.details

import androidx.compose.runtime.Immutable
import app.plansnap.core.domain.report.AreaMetrics
import app.plansnap.core.domain.report.AreaReportCalculator
import app.plansnap.core.domain.report.DefaultAreaReportCalculator
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * One room of the area overview.
 *
 * @property status status of the evaluation, `null` if the room has no evaluation (it could not be evaluated).
 * @property metrics unrounded metrics, `null` for a room without a displayed shape. Such a room is not in the totals.
 * @property pending whether the room is still being evaluated, so it has neither a status nor an error yet.
 */
@Immutable
internal data class OverviewRow(
    val roomId: RoomId,
    val name: String,
    val typeKey: RoomTypeKey?,
    val status: ShapeStatus?,
    val factor: AreaFactor,
    val metrics: AreaMetrics?,
    val pending: Boolean = false,
)

/**
 * The area overview of the project (REQ-MOD-009, REQ-MOD-010).
 *
 * @property rows all rooms in project order.
 * @property totals sums of the unrounded metrics of the rows that have a shape.
 */
@Immutable
internal data class AreaOverview(
    val rows: ImmutableList<OverviewRow>,
    val totals: AreaMetrics,
) {
    /** Number of rooms that are missing in [totals] because they have no shape. */
    val omitted: Int get() = rows.count { it.metrics == null }
}

/**
 * Builds the overview from the project and the current evaluations of [state], `null` while no project is loaded. The
 * numbers come unrounded from [calculator]; a room without an evaluation has none and counts as missing in the totals,
 * either as pending or, if the evaluation failed, as a room without shape.
 */
internal fun areaOverviewOf(
    state: EditorUiState,
    calculator: AreaReportCalculator = DefaultAreaReportCalculator(),
): AreaOverview? {
    val project = state.project ?: return null
    val evaluated = project.copy(rooms = project.rooms.filter { it.id in state.evaluations }.toImmutableList())
    val report = calculator.report(evaluated, state.evaluations.toImmutableMap())
    val entries = report.entries.associateBy { it.roomId }
    val rows =
        project.rooms
            .map { room ->
                val entry = entries[room.id]
                OverviewRow(
                    roomId = room.id,
                    name = room.name,
                    typeKey = room.typeKey,
                    status = entry?.status,
                    factor = room.areaFactor,
                    metrics = entry?.metrics,
                    pending = entry == null && room.id !in state.evaluationErrors,
                )
            }.toImmutableList()
    return AreaOverview(rows, report.totals)
}
