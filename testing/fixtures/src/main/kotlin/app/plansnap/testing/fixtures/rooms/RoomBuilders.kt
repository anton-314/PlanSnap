package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

private const val MIN_VERTICES = 3
private const val MILLI_PER_DEGREE = 1_000.0
private const val STRAIGHT_MILLI_DEGREES = 180_000

/** Creates a vertex from whole millimeters. */
fun vertex(
    x: Int,
    y: Int,
): Vec2 = Vec2(x.toDouble(), y.toDouble())

/** Returns the id of wall `w_index` of the room built with [idPrefix] by [roomFromVertices]. */
fun wallIdOf(
    idPrefix: String,
    index: Int,
): WallId = WallId("$idPrefix-w$index")

/**
 * Builds a closed [Room] from the corner points of a simple, counter-clockwise outline given in the local frame
 * (DOMAIN section 2). This only turns coordinates into entered raw values, it is not a solver:
 *
 * - wall `w_i` gets the distance `V_i -> V_{i+1}` rounded to whole millimeters,
 * - a corner with exactly perpendicular neighbours becomes [CornerSpec.DeclaredRight],
 * - every other corner becomes [CornerSpec.Measured] with the angle `α` rounded to whole thousandths of a degree,
 * - `V_0` stays [CornerSpec.Unspecified], as for a freshly closed room.
 *
 * The raw values of a room with non-perpendicular corners therefore carry the rounding of the raw types.
 * Wall ids are `<idPrefix>-w<i>`, the room id is [idPrefix].
 *
 * @param idPrefix non-blank room id.
 * @param vertices at least three points in counter-clockwise order, without repeated neighbours.
 * @param thicknessMm outward wall thickness of every wall.
 * @param name display name.
 * @throws IllegalArgumentException if the outline is not counter-clockwise or an edge is shorter than 0.5 mm.
 */
fun roomFromVertices(
    idPrefix: String,
    vertices: List<Vec2>,
    thicknessMm: Long = GeometryTolerances.DEFAULT_THICKNESS_MM,
    name: String = "",
): Room {
    val n = vertices.size
    require(n >= MIN_VERTICES) { "An outline needs at least three vertices" }
    require(signedTwiceArea(vertices) > 0.0) { "Outline must be counter-clockwise" }
    val walls =
        List(n) { i ->
            val edge = vertices[(i + 1) % n] - vertices[i]
            Wall(wallIdOf(idPrefix, i), Length(Math.round(StrictMath.sqrt(edge.dot(edge)))), Length(thicknessMm))
        }
    val corners =
        List(n) { i ->
            if (i == 0) {
                CornerSpec.Unspecified
            } else {
                cornerAt(vertices[i] - vertices[i - 1], vertices[(i + 1) % n] - vertices[i])
            }
        }
    return Room(
        id = RoomId(idPrefix),
        name = name,
        walls = walls.toImmutableList(),
        corners = corners.toImmutableList(),
        isClosed = true,
    )
}

private fun signedTwiceArea(vertices: List<Vec2>): Double =
    vertices.indices.sumOf { i -> vertices[i].cross(vertices[(i + 1) % vertices.size]) }

/** Entered corner for the arriving edge [incoming] and the leaving edge [outgoing] of a counter-clockwise outline. */
private fun cornerAt(
    incoming: Vec2,
    outgoing: Vec2,
): CornerSpec {
    val cross = incoming.cross(outgoing)
    val direction = if (cross >= 0.0) TurnDirection.LEFT else TurnDirection.RIGHT
    val dot = incoming.dot(outgoing)
    if (dot == 0.0) return CornerSpec.DeclaredRight(direction)
    val turnMilliDegrees = Math.round(Math.toDegrees(StrictMath.atan2(Math.abs(cross), dot)) * MILLI_PER_DEGREE)
    return CornerSpec.Measured(direction, Angle(STRAIGHT_MILLI_DEGREES - turnMilliDegrees.toInt()))
}
