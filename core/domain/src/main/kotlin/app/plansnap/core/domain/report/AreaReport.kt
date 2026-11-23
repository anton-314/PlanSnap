package app.plansnap.core.domain.report

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Unrounded metrics of a room or a sum (DOMAIN section 7). All values are finite and not negative.
 *
 * @property areaMm2 measured inner area.
 * @property creditedAreaMm2 area after the credit factor.
 * @property perimeterMm perimeter.
 * @property volumeMm3 volume.
 */
data class AreaMetrics(
    val areaMm2: Double,
    val creditedAreaMm2: Double,
    val perimeterMm: Double,
    val volumeMm3: Double,
) {
    init {
        listOf(areaMm2, creditedAreaMm2, perimeterMm, volumeMm3).forEach {
            require(it.isFinite() && it >= 0.0) { "Invalid metric: $it" }
        }
    }

    /** Constants. */
    companion object {
        /** Sum of no rooms. */
        val ZERO: AreaMetrics = AreaMetrics(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * One line of the area report.
 *
 * @property metrics metrics of the room, `null` for `OPEN` and `CONFLICT`.
 */
data class RoomAreaEntry(
    val roomId: RoomId,
    val name: String,
    val status: ShapeStatus,
    val metrics: AreaMetrics?,
) {
    init {
        val hasNoShape = status == ShapeStatus.OPEN || status == ShapeStatus.CONFLICT
        require(hasNoShape == (metrics == null)) { "Metrics are absent exactly for OPEN and CONFLICT" }
    }
}

/**
 * Area report of a project.
 *
 * @property entries rooms in project order.
 * @property totals sums of the unrounded metrics of all entries with metrics.
 */
data class AreaReport(
    val entries: ImmutableList<RoomAreaEntry>,
    val totals: AreaMetrics,
)

/** Computes the area report (REQ-MOD-009, REQ-MOD-010). */
fun interface AreaReportCalculator {
    /** Reports [project] using [evaluations], which must contain every room. */
    fun report(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    ): AreaReport
}
