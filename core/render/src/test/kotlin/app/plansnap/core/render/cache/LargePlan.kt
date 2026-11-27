package app.plansnap.core.render.cache

import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.math.PI

/**
 * The load of REQ-NFR-001, 200 walls in 30 rooms on a grid: 20 rooms with 6 walls (L shape) and 10 with 8 walls
 * (stairs). The evaluations are written down directly, so the render tests need no evaluator.
 */
internal object LargePlan {
    private const val ROOMS = 30
    private const val SIX_WALL_ROOMS = 20
    private const val COLUMNS_PER_ROW = 5
    private const val PITCH_MM = 6_000.0

    private val SIX = listOf(0 to 0, 4000 to 0, 4000 to 2000, 2000 to 2000, 2000 to 3500, 0 to 3500)
    private val EIGHT =
        listOf(0 to 0, 4500 to 0, 4500 to 1500, 3000 to 1500, 3000 to 2500, 1500 to 2500, 1500 to 3500, 0 to 3500)

    fun project(): Project {
        val rooms = List(ROOMS) { room(it, outline(it)) }
        return Project(ProjectId("large"), "Large", Length(2500), rooms.toImmutableList(), 0L, 0L)
    }

    fun evaluations(project: Project): ImmutableMap<RoomId, RoomEvaluation> =
        project.rooms
            .mapIndexed { i, room -> room.id to evaluation(room.id, outline(i)) }
            .toMap()
            .toImmutableMap()

    private fun outline(index: Int): List<Vec2> =
        (if (index < SIX_WALL_ROOMS) SIX else EIGHT).map { (x, y) -> Vec2(x.toDouble(), y.toDouble()) }

    private fun lengths(points: List<Vec2>): List<Double> =
        points.indices.map { (points[(it + 1) % points.size] - points[it]).length() }

    private fun room(
        index: Int,
        points: List<Vec2>,
    ): Room {
        val walls =
            lengths(points).mapIndexed { i, mm -> Wall(WallId("r$index-w$i"), Length(mm.toLong()), Length(120)) }
        val origin = Vec2((index % COLUMNS_PER_ROW) * PITCH_MM, (index / COLUMNS_PER_ROW) * PITCH_MM)
        return Room(
            id = RoomId("r$index"),
            name = "Room $index",
            walls = walls.toImmutableList(),
            corners =
                List(points.size) {
                    if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT)
                }.toImmutableList(),
            isClosed = true,
            placement = RoomPlacement(origin.x, origin.y, 0.0, null),
        )
    }

    private fun evaluation(
        id: RoomId,
        points: List<Vec2>,
    ): RoomEvaluation {
        val lengths = lengths(points)
        val turns =
            points.indices.map { i ->
                val incoming = points[i] - points[(i + points.size - 1) % points.size]
                val outgoing = points[(i + 1) % points.size] - points[i]
                if (incoming.cross(outgoing) > 0.0) PI / 2.0 else -PI / 2.0
            }
        val area = points.indices.sumOf { points[it].cross(points[(it + 1) % points.size]) } / 2.0
        val shape =
            EvaluatedShape(
                Polygon2(points),
                Winding.COUNTER_CLOCKWISE,
                lengths.toImmutableList(),
                turns.toImmutableList(),
                area,
                lengths.sum(),
            )
        return RoomEvaluation.Confirmed(id, shape, persistentMapOf())
    }
}
