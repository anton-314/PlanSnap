package app.plansnap.core.domain.report

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList

/**
 * Pure implementation of [AreaReportCalculator] (DOMAIN section 7).
 *
 * Area and perimeter are taken unrounded from the displayed shape of each evaluation. The volume is `A · h` with the
 * room height or else the project default, the credited area is `A · f / 100`. Rooms that are `OPEN` or `CONFLICT`
 * have no metrics and do not contribute to the totals. Nothing is rounded here: rounding to two decimals happens at
 * the display edge, after summing.
 */
class DefaultAreaReportCalculator : AreaReportCalculator {
    /**
     * @throws IllegalArgumentException if [evaluations] lacks a room of [project].
     */
    override fun report(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    ): AreaReport {
        val entries: ImmutableList<RoomAreaEntry> =
            project.rooms
                .map { room ->
                    val evaluation =
                        requireNotNull(evaluations[room.id]) { "Missing evaluation of room ${room.id.value}" }
                    entryOf(room, evaluation, project)
                }.toImmutableList()
        return AreaReport(entries, entries.mapNotNull { it.metrics }.fold(AreaMetrics.ZERO, ::plus))
    }

    private fun entryOf(
        room: Room,
        evaluation: RoomEvaluation,
        project: Project,
    ): RoomAreaEntry {
        val shape = evaluation.shape
        val metrics =
            shape?.let {
                val heightMm = room.effectiveHeight(project.defaultHeight).mm.toDouble()
                AreaMetrics(
                    areaMm2 = it.areaMm2,
                    creditedAreaMm2 = it.areaMm2 * room.areaFactor.percent / PERCENT,
                    perimeterMm = it.perimeterMm,
                    volumeMm3 = it.areaMm2 * heightMm,
                )
            }
        return RoomAreaEntry(room.id, room.name, room.typeKey, evaluation.status, metrics)
    }

    private fun plus(
        a: AreaMetrics,
        b: AreaMetrics,
    ): AreaMetrics =
        AreaMetrics(
            areaMm2 = a.areaMm2 + b.areaMm2,
            creditedAreaMm2 = a.creditedAreaMm2 + b.creditedAreaMm2,
            perimeterMm = a.perimeterMm + b.perimeterMm,
            volumeMm3 = a.volumeMm3 + b.volumeMm3,
        )

    private companion object {
        const val PERCENT = 100.0
    }
}
