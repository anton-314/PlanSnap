package app.plansnap.core.drawing.hit

import app.plansnap.core.drawing.hit.HitScenes.cornerMark
import app.plansnap.core.drawing.hit.HitScenes.fill
import app.plansnap.core.drawing.hit.HitScenes.line
import app.plansnap.core.drawing.hit.HitScenes.outlinedRoom
import app.plansnap.core.drawing.hit.HitScenes.rectangle
import app.plansnap.core.drawing.hit.HitScenes.room
import app.plansnap.core.drawing.hit.HitScenes.scene
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DefaultHitTesterTest {
    private val tester = DefaultHitTester()

    /** One row of a hit table. */
    data class HitCase(
        val name: String,
        val scene: DrawingScene,
        val point: Vec2,
        val radiusMm: Double,
        val expected: SceneTarget?,
    ) {
        override fun toString(): String = name
    }

    /** One row of a distance table: the element is hit exactly up to [distanceMm]. */
    data class DistanceCase(
        val name: String,
        val element: SceneElement,
        val point: Vec2,
        val distanceMm: Double,
    ) {
        override fun toString(): String = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("priorityCases")
    fun `given overlapping candidates when hit then the priority rule decides`(case: HitCase) {
        assertEquals(case.expected, tester.hitTest(case.scene, case.point, case.radiusMm))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaTieCases", "idTieCases")
    fun `given tied candidates when hit then area and id decide`(case: HitCase) {
        assertEquals(case.expected, tester.hitTest(case.scene, case.point, case.radiusMm))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("distanceCases")
    fun `given single element when hit then it counts up to its distance`(case: DistanceCase) {
        val scene = scene(room("r", listOf(case.element)))
        val target = case.element.target
        assertEquals(target, tester.hitTest(scene, case.point, case.distanceMm + DISTANCE_MARGIN), "just inside")
        if (case.distanceMm >= DISTANCE_MARGIN) {
            assertNull(tester.hitTest(scene, case.point, case.distanceMm - DISTANCE_MARGIN), "just outside")
        }
    }

    @Test
    fun `given invalid radius when hit then it is rejected`() {
        val scene = scene(outlinedRoom("r", rectangle(0.0, 0.0, 10.0, 10.0)))
        for (radius in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { tester.hitTest(scene, Vec2.ZERO, radius) }
        }
    }

    @Test
    fun `given empty scene when hit then nothing is found`() {
        assertNull(tester.hitTest(scene(), Vec2.ZERO, 100.0))
        assertNull(tester.hitTest(scene(room("empty", emptyList())), Vec2.ZERO, 100.0))
    }

    companion object {
        private const val DISTANCE_MARGIN = 1e-6
        private val R = RoomId("r")

        private fun wall(
            id: String,
            room: RoomId = R,
        ) = SceneTarget.Wall(room, WallId(id))

        private fun body(id: String) = SceneTarget.RoomBody(RoomId(id))

        private val opening = SceneTarget.Opening(R, OpeningId("o1"))
        private val diagonal = SceneTarget.Diagonal(R, DiagonalId("d1"))

        /** Square 4 m room with an opening on w0 from x = 1000 to 1900 and the diagonal from V0 to V2. */
        private val base =
            scene(
                outlinedRoom(
                    "r",
                    rectangle(0.0, 0.0, 4_000.0, 4_000.0),
                    extra =
                        listOf(
                            line(opening, Vec2(1_000.0, 0.0), Vec2(1_900.0, 0.0)),
                            line(diagonal, Vec2(0.0, 0.0), Vec2(4_000.0, 4_000.0)),
                        ),
                ),
            )

        @JvmStatic
        fun priorityCases(): List<HitCase> =
            listOf(
                HitCase("interior away from everything", base, Vec2(3_000.0, 1_000.0), 10.0, body("r")),
                HitCase("wall before interior", base, Vec2(2_500.0, 5.0), 10.0, wall("r-w0")),
                HitCase("wall outside tolerance leaves interior", base, Vec2(2_500.0, 5.0), 4.0, body("r")),
                HitCase("wall from outside the room", base, Vec2(2_500.0, -5.0), 10.0, wall("r-w0")),
                HitCase("radius is inclusive", base, Vec2(2_500.0, -5.0), 5.0, wall("r-w0")),
                HitCase("nothing outside tolerance", base, Vec2(2_500.0, -5.0), 4.0, null),
                HitCase("corner before walls and diagonal", base, Vec2(3.0, 3.0), 10.0, SceneTarget.Corner(R, 0)),
                HitCase("far corner", base, Vec2(4_001.0, 4_001.0), 10.0, SceneTarget.Corner(R, 2)),
                HitCase("diagonal when corner is out of reach", base, Vec2(20.0, 20.0), 10.0, diagonal),
                HitCase("diagonal before interior", base, Vec2(2_000.0, 2_005.0), 10.0, diagonal),
                HitCase("opening before wall", base, Vec2(1_500.0, 5.0), 10.0, opening),
                HitCase("opening before a nearer wall", base, Vec2(950.0, 2.0), 60.0, opening),
                HitCase("opening out of reach leaves wall", base, Vec2(950.0, 2.0), 40.0, wall("r-w0")),
                HitCase("far away", base, Vec2(10_000.0, 10_000.0), 100.0, null),
                HitCase(
                    "opening before corner at the same point",
                    scene(
                        room(
                            "r",
                            listOf(
                                cornerMark(SceneTarget.Corner(R, 0), Vec2.ZERO),
                                line(opening, Vec2.ZERO, Vec2(900.0, 0.0)),
                            ),
                        ),
                    ),
                    Vec2.ZERO,
                    1.0,
                    opening,
                ),
                HitCase(
                    "decoration is never hit",
                    scene(room("r", listOf(fill(null, rectangle(0.0, 0.0, 100.0, 100.0))))),
                    Vec2(50.0, 50.0),
                    10.0,
                    null,
                ),
                HitCase(
                    "nested room is hit in its interior",
                    scene(
                        outlinedRoom("big", rectangle(0.0, 0.0, 4_000.0, 4_000.0)),
                        room("empty", emptyList()),
                        outlinedRoom("small", rectangle(1_000.0, 1_000.0, 2_000.0, 2_000.0)),
                    ),
                    Vec2(1_500.0, 1_500.0),
                    0.0,
                    body("small"),
                ),
            )

        @JvmStatic
        fun areaTieCases(): List<HitCase> =
            listOf(
                HitCase(
                    "equal interiors fall back to the smaller room id",
                    scene(
                        room("b", listOf(fill(body("b"), rectangle(0.0, 0.0, 100.0, 100.0)))),
                        room("a", listOf(fill(body("a"), rectangle(0.0, 0.0, 100.0, 100.0)))),
                    ),
                    Vec2(5.0, 5.0),
                    0.0,
                    body("a"),
                ),
                HitCase(
                    "smaller area wins over smaller id",
                    scene(
                        room("a", listOf(fill(body("a"), rectangle(0.0, 0.0, 200.0, 200.0)))),
                        room("z", listOf(fill(body("z"), rectangle(0.0, 0.0, 100.0, 100.0)))),
                    ),
                    Vec2(5.0, 5.0),
                    0.0,
                    body("z"),
                ),
                HitCase(
                    "room without fill counts as infinitely large",
                    scene(
                        room("a", listOf(label(body("a"), Vec2(50.0, 50.0)))),
                        room("z", listOf(fill(body("z"), rectangle(0.0, 0.0, 100.0, 100.0)))),
                    ),
                    Vec2(50.0, 50.0),
                    0.0,
                    body("z"),
                ),
                HitCase(
                    "two rooms without fill fall back to the id",
                    scene(
                        room("z", listOf(label(body("z"), Vec2(50.0, 50.0)))),
                        room("a", listOf(label(body("a"), Vec2(50.0, 50.0)))),
                    ),
                    Vec2(50.0, 50.0),
                    0.0,
                    body("a"),
                ),
                HitCase(
                    "nearer interior wins before area",
                    scene(
                        room("z", listOf(fill(body("z"), rectangle(0.0, 0.0, 1_000.0, 1_000.0)))),
                        room("a", listOf(fill(body("a"), rectangle(1_010.0, 0.0, 1_020.0, 10.0)))),
                    ),
                    Vec2(999.0, 5.0),
                    20.0,
                    body("z"),
                ),
            )

        @JvmStatic
        fun idTieCases(): List<HitCase> =
            listOf(
                HitCase("equidistant walls fall back to the id", walls, Vec2(50.0, 5.0), 10.0, wall("wa")),
                HitCase("nearer wall wins over the id", walls, Vec2(50.0, 4.0), 10.0, wall("wb")),
                HitCase(
                    "equal wall ids fall back to the room id",
                    scene(
                        room("b", listOf(line(wall("w", RoomId("b")), Vec2(0.0, 0.0), Vec2(100.0, 0.0)))),
                        room("a", listOf(line(wall("w", RoomId("a")), Vec2(0.0, 0.0), Vec2(100.0, 0.0)))),
                    ),
                    Vec2(50.0, 1.0),
                    10.0,
                    wall("w", RoomId("a")),
                ),
                HitCase(
                    "corners at the same point fall back to the smaller index",
                    scene(
                        room(
                            "r",
                            listOf(
                                cornerMark(SceneTarget.Corner(R, 3), Vec2.ZERO),
                                cornerMark(SceneTarget.Corner(R, 1), Vec2.ZERO),
                            ),
                        ),
                    ),
                    Vec2(1.0, 0.0),
                    10.0,
                    SceneTarget.Corner(R, 1),
                ),
                HitCase(
                    "equidistant openings fall back to the id",
                    scene(
                        room(
                            "r",
                            listOf(
                                line(SceneTarget.Opening(R, OpeningId("o9")), Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                                line(SceneTarget.Opening(R, OpeningId("o2")), Vec2(0.0, 10.0), Vec2(100.0, 10.0)),
                            ),
                        ),
                    ),
                    Vec2(50.0, 5.0),
                    10.0,
                    SceneTarget.Opening(R, OpeningId("o2")),
                ),
                HitCase(
                    "equidistant diagonals fall back to the id",
                    scene(
                        room(
                            "r",
                            listOf(
                                line(SceneTarget.Diagonal(R, DiagonalId("d9")), Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                                line(SceneTarget.Diagonal(R, DiagonalId("d2")), Vec2(0.0, 10.0), Vec2(100.0, 10.0)),
                            ),
                        ),
                    ),
                    Vec2(50.0, 5.0),
                    10.0,
                    SceneTarget.Diagonal(R, DiagonalId("d2")),
                ),
            )

        private val walls =
            scene(
                room(
                    "r",
                    listOf(
                        line(wall("wb"), Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                        line(wall("wa"), Vec2(0.0, 10.0), Vec2(100.0, 10.0)),
                    ),
                ),
            )

        private fun label(
            target: SceneTarget,
            anchor: Vec2,
            heightMm: Double = 200.0,
        ): SceneElement = SceneElement.Label(anchor, LabelText.Literal("x"), 0.0, heightMm, SceneStyle.LABEL, target)

        private fun arc(
            startRad: Double,
            sweepRad: Double,
        ): SceneElement = SceneElement.Arc(Vec2.ZERO, 1_000.0, startRad, sweepRad, SceneStyle.OPENING, opening)

        private fun polar(
            radius: Double,
            angle: Double,
        ) = Vec2(radius * StrictMath.cos(angle), radius * StrictMath.sin(angle))

        /** Chord from the point at -45° to the arc start at 0°, both on the circle of 1 m. */
        private val endChord = 2.0 * 1_000.0 * sin(PI / 8.0)

        private val triangle = persistentListOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0))

        @JvmStatic
        fun distanceCases(): List<DistanceCase> =
            listOf(
                DistanceCase("arc radial", arc(0.0, PI / 2.0), polar(1_010.0, PI / 4.0), 10.0),
                DistanceCase("arc radial from inside", arc(0.0, PI / 2.0), polar(990.0, PI / 4.0), 10.0),
                DistanceCase("arc beyond its start", arc(0.0, PI / 2.0), polar(1_000.0, -PI / 4.0), endChord),
                DistanceCase("arc beyond its end", arc(0.0, PI / 2.0), polar(1_000.0, 3.0 * PI / 4.0), endChord),
                DistanceCase("arc center", arc(0.0, PI / 2.0), Vec2.ZERO, 1_000.0),
                DistanceCase("clockwise arc on its sweep", arc(0.0, -PI / 2.0), polar(1_005.0, -PI / 4.0), 5.0),
                DistanceCase("clockwise arc beyond its end", arc(0.0, -PI / 2.0), polar(1_000.0, PI / 4.0), endChord),
                DistanceCase("full circle", arc(1.0, 2.0 * PI), polar(1_005.0, -3.0 * PI / 4.0), 5.0),
                DistanceCase(
                    "arc with start beyond a turn",
                    arc(5.0 * PI, PI / 2.0),
                    polar(1_003.0, -3.0 * PI / 4.0),
                    3.0,
                ),
                DistanceCase("label disc", label(opening, Vec2.ZERO, 100.0), Vec2(80.0, 0.0), 30.0),
                DistanceCase("label inside its disc", label(opening, Vec2.ZERO, 100.0), Vec2(30.0, 0.0), 0.0),
                DistanceCase("fill inside", fill(opening, rectangle(0.0, 0.0, 100.0, 100.0)), Vec2(50.0, 50.0), 0.0),
                DistanceCase(
                    "fill beside an edge",
                    fill(opening, rectangle(0.0, 0.0, 100.0, 100.0)),
                    Vec2(150.0, 50.0),
                    50.0,
                ),
                DistanceCase(
                    "fill beside a corner",
                    fill(opening, rectangle(0.0, 0.0, 100.0, 100.0)),
                    Vec2(130.0, 140.0),
                    50.0,
                ),
                DistanceCase(
                    "open polyline ignores the closing edge",
                    SceneElement.Polyline(triangle, closed = false, style = SceneStyle.WALL_LINE, target = opening),
                    Vec2(30.0, 60.0),
                    60.0,
                ),
                DistanceCase(
                    "closed polyline includes the closing edge",
                    SceneElement.Polyline(triangle, closed = true, style = SceneStyle.WALL_LINE, target = opening),
                    Vec2(30.0, 60.0),
                    30.0 / sqrt(2.0),
                ),
                DistanceCase("segment end", line(opening, Vec2(0.0, 0.0), Vec2(100.0, 0.0)), Vec2(103.0, 4.0), 5.0),
                DistanceCase(
                    "point marker",
                    cornerMark(SceneTarget.Corner(R, 0), Vec2(7.0, 7.0)),
                    Vec2(10.0, 11.0),
                    5.0,
                ),
            )
    }
}
