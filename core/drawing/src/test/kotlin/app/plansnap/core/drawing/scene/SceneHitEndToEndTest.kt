package app.plansnap.core.drawing.scene

import app.plansnap.core.common.units.Length
import app.plansnap.core.drawing.hit.DefaultHitTester
import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.rooms.ReferenceRoom
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Builds scenes from the reference rooms with the real evaluator and hits every corner, wall, opening, diagonal and
 * room body through [DefaultHitTester] at its analytic position (DOMAIN section 11).
 */
class SceneHitEndToEndTest {
    private val builder = DefaultSceneBuilder()
    private val tester = DefaultHitTester()

    /** Every reference room, the rectangle also with a diagonal, each with openings on its long walls. */
    private val cases: List<ReferenceRoom> =
        ReferenceRooms.ALL +
            ReferenceRooms.RECTANGLE.copy(
                key = "rectangle-diagonal",
                room =
                    ReferenceRooms.RECTANGLE.room.copy(
                        diagonals = persistentListOf(Diagonal(DiagonalId("rect-d0"), 0, 2, Length(5000))),
                    ),
            )

    /** One opening per wall of at least 1 m, covering `L/8 … 3L/8`, cycling through types and swings. */
    private fun withOpenings(room: Room): Room {
        val openings =
            room.walls.withIndex().filter { it.value.length.mm >= MIN_OPENING_WALL_MM }.map { (i, wall) ->
                val type = OpeningType.entries[i % OpeningType.entries.size]
                val swing = if (type == OpeningType.DOOR) DoorSwing.entries[(i / 3) % DoorSwing.entries.size] else null
                Opening(
                    id = OpeningId("${room.id.value}-o$i"),
                    wallId = wall.id,
                    type = type,
                    offset = Length(wall.length.mm / 8),
                    width = Length(wall.length.mm / 4),
                    height = null,
                    sillHeight = null,
                    swing = swing,
                )
            }
        return room.copy(openings = openings.toImmutableList(), placement = PLACEMENT)
    }

    private fun world(local: Vec2): Vec2 =
        local.rotated(PLACEMENT.rotationRad) + Vec2(PLACEMENT.originX, PLACEMENT.originY)

    private fun build(room: Room): DrawingScene {
        val project = Project(ProjectId("p"), "P", Length(2500), persistentListOf(room), 0L, 0L)
        return builder.build(project, mapOf(room.id to evaluate(room)).toImmutableMap(), SceneOptions())
    }

    private fun assertHit(
        scene: DrawingScene,
        expected: SceneTarget,
        at: Vec2,
    ) {
        assertEquals(expected, tester.hitTest(scene, at, RADIUS_MM), "at $at")
    }

    @TestFactory
    fun `given a reference room when built then every element is hittable at its position`(): List<DynamicTest> =
        cases.map { reference ->
            DynamicTest.dynamicTest(reference.key) {
                val room = withOpenings(reference.room)
                val scene = build(room)
                // The local frame puts V_0 at the origin (DOMAIN section 2), the reference outline may not.
                val local = reference.vertices.map { it - reference.vertices.first() }
                val n = local.size
                local.forEachIndexed { i, vertex -> assertHit(scene, SceneTarget.Corner(room.id, i), world(vertex)) }
                for (i in 0 until n) {
                    val wallPoint = local[i] + (local[(i + 1) % n] - local[i]) * WALL_HIT_FRACTION
                    assertHit(scene, SceneTarget.Wall(room.id, room.walls[i].id), world(wallPoint))
                }
                room.openings.forEach { opening ->
                    val i = room.walls.indexOfFirst { it.id == opening.wallId }
                    val edge = local[(i + 1) % n] - local[i]
                    val along = (opening.offset.mm + opening.width.mm / 2.0) / edge.length()
                    assertHit(scene, SceneTarget.Opening(room.id, opening.id), world(local[i] + edge * along))
                }
                room.diagonals.forEach { diagonal ->
                    val middle = (local[diagonal.fromCorner] + local[diagonal.toCorner]) * 0.5
                    assertHit(scene, SceneTarget.Diagonal(room.id, diagonal.id), world(middle))
                }
                assertHit(scene, SceneTarget.RoomBody(room.id), world(deepInteriorPoint(local, room)))
            }
        }

    @Test
    fun `given an open chain when built then its walls and corners are hittable`() {
        val room = SceneFixtures.open.copy(placement = PLACEMENT)
        val scene = build(room)
        val chain = listOf(Vec2(0.0, 0.0), Vec2(3000.0, 0.0), Vec2(3000.0, 2000.0))
        assertHit(scene, SceneTarget.Corner(room.id, 0), world(chain[0]))
        assertHit(scene, SceneTarget.Corner(room.id, 1), world(chain[1]))
        assertHit(scene, SceneTarget.Wall(room.id, WallId("open-w0")), world(Vec2(1500.0, 0.0)))
        assertHit(scene, SceneTarget.Wall(room.id, WallId("open-w1")), world(Vec2(3000.0, 1000.0)))
    }

    @Test
    fun `given several placed rooms when built then each hit selects the element of its own room`() {
        val first = ReferenceRooms.RECTANGLE.room.copy(placement = RoomPlacement(0.0, 0.0, 0.0, null))
        val second = ReferenceRooms.L_SHAPE.room.copy(placement = RoomPlacement(20_000.0, 0.0, Math.PI / 2.0, null))
        val project = Project(ProjectId("p"), "P", Length(2500), persistentListOf(first, second), 0L, 0L)
        val scene =
            builder.build(
                project,
                listOf(first, second).associate { it.id to evaluate(it) }.toImmutableMap(),
                SceneOptions(),
            )
        assertHit(scene, SceneTarget.Corner(first.id, 2), Vec2(4000.0, 3000.0))
        // The L shape is turned by 90 degrees, so V_1 = (6000, 0) lies at (20000, 6000).
        assertHit(scene, SceneTarget.Corner(second.id, 1), Vec2(20_000.0, 6000.0))
        assertHit(scene, SceneTarget.RoomBody(second.id), Vec2(19_000.0, 1000.0))
        assertHit(scene, SceneTarget.RoomBody(first.id), Vec2(1000.0, 1000.0))
    }

    /**
     * Grid point inside the analytic outline that is farthest from every wall, among those away from the diagonals,
     * so that only the room body can be hit there.
     */
    private fun deepInteriorPoint(
        vertices: List<Vec2>,
        room: Room,
    ): Vec2 {
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        val grid =
            (1 until GRID).flatMap { i ->
                (1 until GRID).map { j -> Vec2(minX + (maxX - minX) * i / GRID, minY + (maxY - minY) * j / GRID) }
            }
        val segments = vertices.indices.map { vertices[it] to vertices[(it + 1) % vertices.size] }
        val diagonals = room.diagonals.map { vertices[it.fromCorner] to vertices[it.toCorner] }
        return grid
            .filter { inside(vertices, it) }
            .filter { point -> diagonals.all { distance(point, it) > (maxX - minX) / GRID } }
            .maxBy { point -> segments.minOf { distance(point, it) } }
    }

    private fun inside(
        polygon: List<Vec2>,
        point: Vec2,
    ): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if ((current.y > point.y) != (previous.y > point.y)) {
                val x = current.x + (point.y - current.y) * (previous.x - current.x) / (previous.y - current.y)
                if (point.x < x) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun distance(
        point: Vec2,
        segment: Pair<Vec2, Vec2>,
    ): Double {
        val (a, b) = segment
        val d = b - a
        val t = ((point - a).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
        return (point - (a + d * t)).length()
    }

    private companion object {
        const val RADIUS_MM = 1.0
        const val WALL_HIT_FRACTION = 0.75
        const val MIN_OPENING_WALL_MM = 1000L
        const val GRID = 16
        val PLACEMENT = RoomPlacement(12_345.0, -6789.0, 0.7, null)
    }
}
