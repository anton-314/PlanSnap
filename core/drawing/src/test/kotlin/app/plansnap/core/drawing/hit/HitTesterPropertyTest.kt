package app.plansnap.core.drawing.hit

import app.plansnap.core.drawing.hit.HitScenes.line
import app.plansnap.core.drawing.hit.HitScenes.outlinedRoom
import app.plansnap.core.drawing.hit.HitScenes.room
import app.plansnap.core.drawing.hit.HitScenes.scene
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HitTesterPropertyTest {
    private val tester = DefaultHitTester()

    @Test
    fun `the result does not depend on the order of rooms and elements`() =
        runBlocking<Unit> {
            var hits = 0
            checkAll(ITERATIONS, config(SEED_ORDER), cases) { case ->
                val expected = tester.hitTest(case.scene, case.point, case.radiusMm)
                val shuffled =
                    case.scene.rooms
                        .map { it.copy(elements = it.elements.shuffled(case.shuffle).toImmutableList()) }
                        .shuffled(case.shuffle)
                val actual = tester.hitTest(scene(*shuffled.toTypedArray()), case.point, case.radiusMm)
                assertEquals(expected, actual, "$case")
                if (expected != null) hits++
            }
            assertTrue(hits > ITERATIONS / 2, "only $hits of $ITERATIONS cases hit anything")
        }

    @Test
    fun `translating scene and point by whole millimeters keeps the result`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_TRANSLATION), cases) { case ->
                val shift = Vec2(case.shift.x, case.shift.y)
                val moved =
                    scene(
                        *case.scene.rooms
                            .map { translated(it, shift) }
                            .toTypedArray(),
                    )
                assertEquals(
                    tester.hitTest(case.scene, case.point, case.radiusMm),
                    tester.hitTest(moved, case.point + shift, case.radiusMm),
                    "$case",
                )
            }
        }

    @Test
    fun `a larger radius never yields a lower priority class`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_RADIUS), cases) { case ->
                val small = tester.hitTest(case.scene, case.point, case.radiusMm) ?: return@checkAll
                val large = tester.hitTest(case.scene, case.point, case.radiusMm * 2.0 + 1.0)
                assertNotNull(large, "$case")
                assertTrue(rank(large!!) <= rank(small), "$small then $large, $case")
            }
        }

    @Test
    fun `removing candidates of lower priority keeps the result`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_REMOVAL), cases) { case ->
                val hit = tester.hitTest(case.scene, case.point, case.radiusMm) ?: return@checkAll
                val reduced =
                    case.scene.rooms.map { room ->
                        room(
                            room.roomId.value,
                            room.elements.filter { e ->
                                e.target.let {
                                    it != null &&
                                        rank(it) <= rank(hit)
                                }
                            },
                        )
                    }
                assertEquals(hit, tester.hitTest(scene(*reduced.toTypedArray()), case.point, case.radiusMm), "$case")
            }
        }

    @Test
    fun `removing the winner leaves a result of the same or lower priority`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_WINNER), cases) { case ->
                val hit = tester.hitTest(case.scene, case.point, case.radiusMm) ?: return@checkAll
                val reduced =
                    case.scene.rooms.map { room -> room(room.roomId.value, room.elements.filter { it.target != hit }) }
                val next = tester.hitTest(scene(*reduced.toTypedArray()), case.point, case.radiusMm)
                if (next != null) assertTrue(rank(next) >= rank(hit), "$hit then $next, $case")
            }
        }

    @Test
    fun `reference rooms hit their corners at the vertices and their walls at the edge midpoints`() {
        for (reference in ReferenceRooms.ALL) {
            val vertices = reference.vertices
            val scene = scene(outlinedRoom("r", vertices))
            val roomId = RoomId("r")
            val shortest = vertices.indices.minOf { (vertices[(it + 1) % vertices.size] - vertices[it]).length() }
            val radius = shortest / 4.0
            vertices.forEachIndexed { i, vertex ->
                assertEquals(SceneTarget.Corner(roomId, i), tester.hitTest(scene, vertex, radius), reference.key)
                val next = vertices[(i + 1) % vertices.size]
                val middle = (vertex + next) * 0.5
                assertEquals(
                    SceneTarget.Wall(roomId, WallId("r-w$i")),
                    tester.hitTest(scene, middle, radius),
                    reference.key,
                )
            }
        }
    }

    @Test
    fun `reference rooms hit their interior exactly at the points inside`() =
        runBlocking<Unit> {
            var inside = 0
            checkAll(ITERATIONS, config(SEED_INTERIOR), referencePoints) { (key, scene, point) ->
                val vertices =
                    (
                        scene.rooms
                            .single()
                            .elements
                            .single() as SceneElement.Fill
                    ).polygon.vertices
                val expected = if (windingNumber(vertices, point) != 0) SceneTarget.RoomBody(RoomId("r")) else null
                assertEquals(expected, tester.hitTest(scene, point, 0.0), "$key at $point")
                if (expected != null) inside++
            }
            assertTrue(inside in ITERATIONS / 4 until ITERATIONS, "$inside of $ITERATIONS points were inside")
        }

    /** Independent oracle: the winding number from the summed angles, not a crossing count. */
    private fun windingNumber(
        vertices: List<Vec2>,
        point: Vec2,
    ): Int {
        var total = 0.0
        vertices.forEachIndexed { i, vertex ->
            val a = vertex - point
            val b = vertices[(i + 1) % vertices.size] - point
            total += StrictMath.atan2(a.cross(b), a.dot(b))
        }
        return Math.round(total / (2.0 * Math.PI)).toInt()
    }

    /** Random scene on an integer grid, so translations by whole millimeters are exact. */
    private data class Case(
        val scene: DrawingScene,
        val point: Vec2,
        val radiusMm: Double,
        val shift: Vec2,
        val shuffle: kotlin.random.Random,
    ) {
        override fun toString(): String = "point $point, radius $radiusMm, shift $shift, scene $scene"
    }

    private val cases: Arb<Case> =
        arbitrary { rs ->
            val roomCount = rs.random.nextInt(1, MAX_ROOMS + 1)
            val rooms =
                (0 until roomCount).map { r ->
                    val roomId = RoomId("room$r")
                    room(roomId.value, List(rs.random.nextInt(1, MAX_ELEMENTS + 1)) { rs.element(roomId) })
                }
            Case(
                scene = scene(*rooms.toTypedArray()),
                point = rs.gridPoint(),
                radiusMm = rs.random.nextInt(0, MAX_RADIUS + 1).toDouble(),
                shift = Vec2(rs.coordinate(MAX_SHIFT), rs.coordinate(MAX_SHIFT)),
                shuffle = kotlin.random.Random(rs.random.nextLong()),
            )
        }

    private fun RandomSource.coordinate(limit: Int): Double = random.nextInt(-limit, limit + 1).toDouble()

    private fun RandomSource.gridPoint(): Vec2 = Vec2(coordinate(GRID), coordinate(GRID))

    private fun RandomSource.target(roomId: RoomId): SceneTarget? {
        val id = "t${random.nextInt(0, ID_POOL)}"
        return when (random.nextInt(0, TARGET_KINDS)) {
            0 -> SceneTarget.Opening(roomId, OpeningId(id))
            1 -> SceneTarget.Corner(roomId, random.nextInt(0, ID_POOL))
            2 -> SceneTarget.Wall(roomId, WallId(id))
            3 -> SceneTarget.Diagonal(roomId, DiagonalId(id))
            4 -> SceneTarget.RoomBody(roomId)
            else -> null
        }
    }

    private fun RandomSource.element(roomId: RoomId): SceneElement {
        val target = target(roomId)
        return when (random.nextInt(0, ELEMENT_KINDS)) {
            0 -> {
                val vertices = List(random.nextInt(3, MAX_FILL_VERTICES + 1)) { gridPoint() }
                SceneElement.Fill(Polygon2(vertices), SceneStyle.WALL_BAND, target)
            }

            1 -> {
                line(target, gridPoint(), gridPoint(), gridPoint())
            }

            2 -> {
                SceneElement.Arc(
                    center = gridPoint(),
                    radiusMm = random.nextInt(1, GRID).toDouble(),
                    startRad = random.nextInt(0, ANGLE_STEPS) * ANGLE_STEP,
                    sweepRad = random.nextInt(-ANGLE_STEPS, ANGLE_STEPS + 1) * ANGLE_STEP,
                    style = SceneStyle.OPENING,
                    target = target,
                )
            }

            else -> {
                SceneElement.Label(
                    gridPoint(),
                    LabelText.Literal("x"),
                    0.0,
                    random.nextInt(1, GRID).toDouble(),
                    SceneStyle.LABEL,
                    target,
                )
            }
        }
    }

    private fun translated(
        room: RoomScene,
        shift: Vec2,
    ): RoomScene =
        room(
            room.roomId.value,
            room.elements.map { element ->
                when (element) {
                    is SceneElement.Fill -> {
                        element.copy(
                            polygon = Polygon2(element.polygon.vertices.map { it + shift }),
                        )
                    }

                    is SceneElement.Polyline -> {
                        element.copy(
                            points = element.points.map { it + shift }.toImmutableList(),
                        )
                    }

                    is SceneElement.Arc -> {
                        element.copy(center = element.center + shift)
                    }

                    is SceneElement.Label -> {
                        element.copy(anchor = element.anchor + shift)
                    }
                }
            },
        )

    private data class ReferencePoint(
        val key: String,
        val scene: DrawingScene,
        val point: Vec2,
    )

    private val referenceScenes = ReferenceRooms.ALL.map { it.key to scene(room("r", listOf(body(it.vertices)))) }

    private fun body(vertices: List<Vec2>): SceneElement =
        SceneElement.Fill(Polygon2(vertices), SceneStyle.WALL_BAND, SceneTarget.RoomBody(RoomId("r")))

    /** Random points in the bounds of a reference room. */
    private val referencePoints: Arb<ReferencePoint> =
        arbitrary { rs ->
            val (key, scene) = referenceScenes[rs.random.nextInt(referenceScenes.size)]
            val bounds = scene.bounds!!
            val point =
                Vec2(
                    bounds.minX + rs.random.nextDouble() * bounds.width,
                    bounds.minY + rs.random.nextDouble() * bounds.height,
                )
            ReferencePoint(key, scene, point)
        }

    private companion object {
        const val ITERATIONS = 1_000
        const val MAX_ROOMS = 3
        const val MAX_ELEMENTS = 6
        const val GRID = 100
        const val MAX_RADIUS = 60
        const val MAX_SHIFT = 1_000_000
        const val ID_POOL = 3
        const val MAX_FILL_VERTICES = 5
        const val TARGET_KINDS = 6
        const val ELEMENT_KINDS = 4
        const val ANGLE_STEPS = 8
        const val ANGLE_STEP = Math.PI / 4.0
        const val SEED_ORDER = 9_001L
        const val SEED_TRANSLATION = 9_002L
        const val SEED_RADIUS = 9_003L
        const val SEED_REMOVAL = 9_004L
        const val SEED_WINNER = 9_005L
        const val SEED_INTERIOR = 9_006L

        fun rank(target: SceneTarget): Int =
            when (target) {
                is SceneTarget.Opening -> 0
                is SceneTarget.Corner -> 1
                is SceneTarget.Wall -> 2
                is SceneTarget.Diagonal -> 3
                is SceneTarget.RoomBody -> 4
            }

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)
    }
}
