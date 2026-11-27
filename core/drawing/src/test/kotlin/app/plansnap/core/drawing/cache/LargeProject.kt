package app.plansnap.core.drawing.cache

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.generators.RoomGenerator
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** The reference load of REQ-NFR-001: 30 rooms with 200 walls on a grid, 20 rooms with 6 and 10 with 8 walls. */
internal object LargeProject {
    const val ROOMS = 30
    const val WALLS = 200
    private const val SIX_WALL_ROOMS = 20
    private const val COLUMNS_PER_ROW = 6
    private const val PITCH_MM = 12_000.0
    private const val SEED = 4_201L

    fun project(): Project {
        val generator = RoomGenerator(SEED)
        val rooms =
            List(ROOMS) { index ->
                val columns = if (index < SIX_WALL_ROOMS) 2 else 3
                val placement =
                    RoomPlacement(
                        (index % COLUMNS_PER_ROW) * PITCH_MM,
                        (index / COLUMNS_PER_ROW) * PITCH_MM,
                        0.0,
                        null,
                    )
                generator.histogram(columns).room.copy(name = "Room $index", placement = placement)
            }
        return Project(ProjectId("large"), "Large", Length(2500), rooms.toImmutableList(), 0L, 0L)
    }

    fun evaluations(rooms: List<Room>): ImmutableMap<RoomId, RoomEvaluation> =
        rooms.associate { it.id to evaluate(it) }.toImmutableMap()
}
