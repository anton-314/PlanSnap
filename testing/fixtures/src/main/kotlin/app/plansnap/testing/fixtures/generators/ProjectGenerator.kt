package app.plansnap.testing.fixtures.generators

import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.random.SeededRandom
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.toImmutableList

/**
 * Seeded generator of projects with valid rooms and openings. The same seed and the same call sequence yield
 * identical projects, including ids and timestamps. Timestamps start at [FakeEpochClock.DEFAULT_START_EPOCH_MS], the
 * real clock is never read.
 *
 * Rooms are placed on a grid with 30 m pitch and a random rotation, so they never overlap and never dock. Each wall
 * of at least 2 m carries an opening with probability one half, so openings of one wall never overlap and always fit.
 *
 * @param seed seed of the random sequence.
 * @param ids source of project, room and opening ids.
 */
class ProjectGenerator(
    seed: Long,
    private val ids: IdGenerator = SequentialIdGenerator("gen"),
) {
    private val random = SeededRandom(seed)
    private val rooms = RoomGenerator(random.nextLong(), ids)

    /** Returns a project with [roomCount] rooms. */
    fun project(roomCount: Int = random.nextInt(MIN_ROOMS..MAX_ROOMS)): Project {
        require(roomCount >= 0) { "Room count must not be negative: $roomCount" }
        val projectId = ids.newId()
        val createdAt = FakeEpochClock.DEFAULT_START_EPOCH_MS + random.nextInt(DAYS_RANGE) * MILLIS_PER_DAY
        val generated = List(roomCount) { index -> placed(rooms.any().room, index) }
        return Project(
            id = ProjectId(projectId),
            name = "Project $projectId",
            defaultHeight = Length(DEFAULT_HEIGHT_MM),
            rooms = generated.toImmutableList(),
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = createdAt + random.nextInt(MINUTES_RANGE) * MILLIS_PER_MINUTE,
        )
    }

    private fun placed(
        room: Room,
        index: Int,
    ): Room {
        val placement =
            RoomPlacement(
                originX = (index % GRID_COLUMNS) * GRID_PITCH_MM,
                originY = (index / GRID_COLUMNS) * GRID_PITCH_MM,
                rotationRad = random.nextDouble() * FULL_TURN,
                dock = null,
            )
        return room.copy(
            name = "Room ${index + 1}",
            placement = placement,
            openings = openingsFor(room).toImmutableList(),
        )
    }

    private fun openingsFor(room: Room): List<Opening> =
        room.walls.mapNotNull { wall ->
            val free = wall.length.mm - 2 * MARGIN_MM
            if (wall.length.mm < MIN_WALL_FOR_OPENING_MM || !random.nextBoolean()) return@mapNotNull null
            val width = random.nextMultiple(MIN_OPENING_WIDTH_MM..MAX_OPENING_WIDTH_MM, STEP_MM)
            val offset = MARGIN_MM + random.nextInt(((free - width) / STEP_MM + 1L).toInt()) * STEP_MM
            val type = OpeningType.entries[random.nextInt(OpeningType.entries.size)]
            Opening(
                id = OpeningId(ids.newId()),
                wallId = wall.id,
                type = type,
                offset = Length(offset),
                width = Length(width.toLong()),
                height = if (type == OpeningType.PASSAGE) null else Length(openingHeight(type)),
                sillHeight = if (type == OpeningType.WINDOW) Length(SILL_HEIGHT_MM) else null,
                swing =
                    if (type ==
                        OpeningType.DOOR
                    ) {
                        DoorSwing.entries[random.nextInt(DoorSwing.entries.size)]
                    } else {
                        null
                    },
            )
        }

    private fun openingHeight(type: OpeningType): Long =
        if (type ==
            OpeningType.DOOR
        ) {
            DOOR_HEIGHT_MM
        } else {
            WINDOW_HEIGHT_MM
        }

    private companion object {
        const val MIN_ROOMS = 1
        const val MAX_ROOMS = 6
        const val DAYS_RANGE = 365
        const val MINUTES_RANGE = 10_000
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_DAY = 86_400_000L
        const val DEFAULT_HEIGHT_MM = 2500L
        const val GRID_COLUMNS = 4
        const val GRID_PITCH_MM = 30_000.0
        const val FULL_TURN = 2.0 * Math.PI
        const val STEP_MM = 10
        const val MARGIN_MM = 100L
        const val MIN_WALL_FOR_OPENING_MM = 2000L
        const val MIN_OPENING_WIDTH_MM = 600
        const val MAX_OPENING_WIDTH_MM = 1200
        const val DOOR_HEIGHT_MM = 2000L
        const val WINDOW_HEIGHT_MM = 1200L
        const val SILL_HEIGHT_MM = 900L
    }
}
