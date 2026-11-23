package app.plansnap.testing.fixtures.generators

import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.testing.fixtures.random.SeededRandom
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Generated room together with the exact corner points it was derived from.
 *
 * @property room closed room built by `roomFromVertices`, so its raw values carry the rounding of the raw types.
 * @property vertices corner points in whole millimeters, counter-clockwise, a simple polygon by construction.
 */
data class GeneratedRoom(
    val room: Room,
    val vertices: ImmutableList<Vec2>,
)

/**
 * Seeded generator of simple, closed rooms. The same seed and the same call sequence yield identical rooms.
 * Randomness comes only from [SeededRandom], never from the clock.
 *
 * Every generated outline is a simple polygon, counter-clockwise, with all wall lengths between
 * [GeometryTolerances.MIN_WALL_MM] and [GeometryTolerances.MAX_WALL_MM] and at most
 * [GeometryTolerances.MAX_WALLS_PER_ROOM] walls.
 *
 * @param seed seed of the random sequence.
 * @param ids source of room ids. Walls derive their ids from the room id.
 */
class RoomGenerator(
    seed: Long,
    private val ids: IdGenerator = SequentialIdGenerator("room"),
) {
    private val random = SeededRandom(seed)

    /** Returns a rectangle with sides between 1 m and 8 m. */
    fun rectangle(): GeneratedRoom = histogram(columns = 1)

    /**
     * Returns an orthogonal room made of [columns] adjacent columns of random width and height on a common base line.
     * Neighbouring columns differ in height, so no corner is collinear. The room has `2 · columns + 2` walls.
     *
     * @param columns number of columns, `1` is a rectangle, at most [MAX_COLUMNS].
     */
    fun histogram(columns: Int): GeneratedRoom {
        require(columns in 1..MAX_COLUMNS) { "Columns out of range: $columns" }
        // The base wall spans all columns, so the widest column shrinks until even 63 columns fit under the maximum.
        val maxWidth = minOf(MAX_COLUMN_WIDTH_MM, GeometryTolerances.MAX_WALL_MM.toInt() / columns / STEP_MM * STEP_MM)
        val widths = List(columns) { random.nextMultiple(MIN_COLUMN_WIDTH_MM..maxWidth, STEP_MM) }
        val heights = mutableListOf<Int>()
        repeat(columns) {
            var height: Int
            do {
                height = random.nextMultiple(MIN_COLUMN_HEIGHT_MM..MAX_COLUMN_HEIGHT_MM, STEP_MM)
            } while (height == heights.lastOrNull())
            heights += height
        }
        val edges = widths.runningFold(0) { x, width -> x + width }
        val points = mutableListOf(vertex(x = 0, y = 0), vertex(x = edges.last(), y = 0))
        for (column in columns - 1 downTo 0) {
            points += vertex(x = edges[column + 1], y = heights[column])
            points += vertex(x = edges[column], y = heights[column])
        }
        return generated(points)
    }

    /**
     * Returns a star-shaped polygon around the origin with random radii, which is simple by construction and
     * usually has oblique and reflex corners. Corner angles are measured values rounded to thousandths of a degree.
     *
     * @param vertexCount number of corners and walls, from [MIN_STAR_VERTICES] to [MAX_STAR_VERTICES].
     */
    fun star(vertexCount: Int): GeneratedRoom {
        require(vertexCount in MIN_STAR_VERTICES..MAX_STAR_VERTICES) { "Vertex count out of range: $vertexCount" }
        val sector = FULL_TURN / vertexCount
        val points =
            List(vertexCount) { i ->
                val angle = sector * (i + (random.nextDouble() - HALF) * JITTER)
                val radius = random.nextInt(MIN_STAR_RADIUS_MM..MAX_STAR_RADIUS_MM)
                vertex(
                    x = Math.round(radius * StrictMath.cos(angle)).toInt(),
                    y = Math.round(radius * StrictMath.sin(angle)).toInt(),
                )
            }
        return generated(points)
    }

    /** Returns a room of a randomly chosen kind: rectangle, histogram or star. */
    fun any(): GeneratedRoom =
        when (random.nextInt(KIND_COUNT)) {
            0 -> rectangle()
            1 -> histogram(random.nextInt(MIN_RANDOM_COLUMNS..MAX_RANDOM_COLUMNS))
            else -> star(random.nextInt(MIN_STAR_VERTICES..MAX_RANDOM_STAR_VERTICES))
        }

    private fun generated(points: List<Vec2>): GeneratedRoom {
        val room = roomFromVertices(idPrefix = ids.newId(), vertices = points)
        return GeneratedRoom(room, points.toImmutableList())
    }

    /** Constants. */
    companion object {
        /** Largest column count of [histogram], which gives exactly 128 walls. */
        const val MAX_COLUMNS = (GeometryTolerances.MAX_WALLS_PER_ROOM - 2) / 2

        /** Fewest corners of [star]. */
        const val MIN_STAR_VERTICES = 5

        /** Most corners of [star]. Larger counts could push neighbouring corners below the wall minimum. */
        const val MAX_STAR_VERTICES = 16

        private const val STEP_MM = 10
        private const val MIN_COLUMN_WIDTH_MM = 500
        private const val MAX_COLUMN_WIDTH_MM = 3000
        private const val MIN_COLUMN_HEIGHT_MM = 1000
        private const val MAX_COLUMN_HEIGHT_MM = 8000
        private const val MIN_STAR_RADIUS_MM = 1000
        private const val MAX_STAR_RADIUS_MM = 5000
        private const val MIN_RANDOM_COLUMNS = 2
        private const val MAX_RANDOM_COLUMNS = 10
        private const val MAX_RANDOM_STAR_VERTICES = 12
        private const val KIND_COUNT = 3
        private const val FULL_TURN = 2.0 * Math.PI
        private const val HALF = 0.5
        private const val JITTER = 0.5
    }
}
