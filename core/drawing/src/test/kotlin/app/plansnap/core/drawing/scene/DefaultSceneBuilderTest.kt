package app.plansnap.core.drawing.scene

import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.SceneFixtures.assertClose
import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class DefaultSceneBuilderTest {
    private val builder = DefaultSceneBuilder()
    private val rectangle = ReferenceRooms.RECTANGLE.room
    private val wall0 = WallId("ref-rectangle-w0")

    /** Opening [id] covering [span] along its wall, from `offset` to `offset + width`. */
    private fun opening(
        id: String,
        type: OpeningType,
        span: LongRange,
        swing: DoorSwing? = null,
        wallId: WallId = wall0,
    ) = Opening(OpeningId(id), wallId, type, Length(span.first), Length(span.last - span.first), null, null, swing)

    private fun scene(
        room: Room,
        options: SceneOptions = SceneOptions(),
    ): RoomScene = builder.buildRoom(room, evaluate(room), options)

    private fun RoomScene.of(target: SceneTarget): List<SceneElement> = elements.filter { it.target == target }

    private fun assertPolygon(
        expected: List<Vec2>,
        actual: Polygon2,
    ) {
        assertEquals(expected.size, actual.vertices.size)
        expected.zip(actual.vertices).forEach { (e, a) -> assertClose(e, a) }
    }

    @Test
    fun `given a confirmed rectangle when built then each wall is a mitered band selecting the wall`() {
        val bands = scene(rectangle).elements.filter { it.style == SceneStyle.WALL_BAND }
        assertEquals(4, bands.size)
        val first = bands.first() as SceneElement.Fill
        assertEquals(SceneTarget.Wall(rectangle.id, wall0), first.target)
        assertPolygon(
            listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4120.0, -120.0), Vec2(-120.0, -120.0)),
            first.polygon,
        )
    }

    @Test
    fun `given a wall of thickness zero when built then it is a line in the style of the contour`() {
        val thin = rectangle.copy(walls = rectangle.walls.map { it.copy(thickness = Length.ZERO) }.toImmutableList())
        val lines = scene(thin).of(SceneTarget.Wall(thin.id, wall0)).filter { it.style == SceneStyle.WALL_LINE }
        assertEquals(1, lines.size)
        val line = lines.single() as SceneElement.Polyline
        assertClose(Vec2(0.0, 0.0), line.points[0])
        assertClose(Vec2(4000.0, 0.0), line.points[1])
    }

    @Test
    fun `given an unconfirmed room with thin walls when built then walls and body are in unconfirmed styles`() {
        val proposed = SceneFixtures.proposed
        val thin = proposed.copy(walls = proposed.walls.map { it.copy(thickness = Length.ZERO) }.toImmutableList())
        val built = scene(thin)
        val wallStyles = built.elements.filter { it.target is SceneTarget.Wall && it.style != SceneStyle.DIMENSION }
        assertEquals(setOf(SceneStyle.WALL_UNCONFIRMED), wallStyles.map { it.style }.toSet())
        val body = built.of(SceneTarget.RoomBody(thin.id)).filterIsInstance<SceneElement.Fill>()
        assertEquals(listOf(SceneStyle.ROOM_BODY_UNCONFIRMED), body.map { it.style })
    }

    @Test
    fun `given a room when built then the body is the inner contour selecting the room`() {
        val body = scene(rectangle).of(SceneTarget.RoomBody(rectangle.id)).filterIsInstance<SceneElement.Fill>()
        assertEquals(1, body.size)
        assertPolygon(ReferenceRooms.RECTANGLE.vertices, body.single().polygon)
    }

    @Test
    fun `given an inward door with DIN right hinge when built then seen from inside the hinge is at the start`() {
        // Wall 0 runs from (0, 0) to (4000, 0) with the room above it. Seen from inside, the viewer faces -y and has
        // the start of the wall on the right.
        val room =
            rectangle.copy(
                openings = persistentListOf(opening("d", OpeningType.DOOR, 1000L..1800L, DoorSwing.HINGE_RIGHT_INWARD)),
            )
        val elements = scene(room).of(SceneTarget.Opening(room.id, OpeningId("d")))
        assertEquals(listOf(SceneStyle.OPENING), elements.map { it.style }.distinct())
        val fill = elements[0] as SceneElement.Fill
        assertPolygon(
            listOf(Vec2(1000.0, 0.0), Vec2(1800.0, 0.0), Vec2(1800.0, -120.0), Vec2(1000.0, -120.0)),
            fill.polygon,
        )
        val leaf = elements[1] as SceneElement.Polyline
        assertClose(Vec2(1000.0, 0.0), leaf.points[0])
        assertClose(Vec2(1000.0, 800.0), leaf.points[1])
        val arc = elements[2] as SceneElement.Arc
        assertClose(Vec2(1000.0, 0.0), arc.center)
        assertEquals(800.0, arc.radiusMm, 1e-9)
        assertEquals(0.0, arc.startRad, 1e-12)
        assertEquals(PI / 2.0, arc.sweepRad, 1e-12)
    }

    @Test
    fun `given an outward door with DIN right hinge when built then seen from outside it swings out at the end`() {
        val room =
            rectangle.copy(
                openings =
                    persistentListOf(
                        opening("d", OpeningType.DOOR, 1000L..1800L, DoorSwing.HINGE_RIGHT_OUTWARD),
                    ),
            )
        val elements = scene(room).of(SceneTarget.Opening(room.id, OpeningId("d")))
        val leaf = elements[1] as SceneElement.Polyline
        assertClose(Vec2(1800.0, -120.0), leaf.points[0])
        assertClose(Vec2(1800.0, -920.0), leaf.points[1])
        val arc = elements[2] as SceneElement.Arc
        assertClose(Vec2(1800.0, -120.0), arc.center)
        assertEquals(PI, arc.startRad, 1e-12)
        assertEquals(PI / 2.0, arc.sweepRad, 1e-12)
    }

    @Test
    fun `given the other swings when built then the arc turns from the jamb to the leaf`() {
        val cases =
            mapOf(
                DoorSwing.HINGE_LEFT_OUTWARD to Triple(Vec2(1000.0, -120.0), 0.0, -PI / 2.0),
                DoorSwing.HINGE_LEFT_INWARD to Triple(Vec2(1800.0, 0.0), PI, -PI / 2.0),
            )
        cases.forEach { (swing, expected) ->
            val room = rectangle.copy(openings = persistentListOf(opening("d", OpeningType.DOOR, 1000L..1800L, swing)))
            val arc = scene(room).elements.filterIsInstance<SceneElement.Arc>().single()
            assertClose(expected.first, arc.center)
            assertEquals(expected.second, arc.startRad, 1e-12, "$swing")
            assertEquals(expected.third, arc.sweepRad, 1e-12, "$swing")
        }
    }

    @Test
    fun `given the same door in a room measured clockwise when built then hinge leaf and arc stay in place`() {
        // The rectangle measured clockwise from (4000, 0): placed with half a turn it covers the same area, and its
        // wall 0 is the bottom wall of the reference, run from (4000, 0) to (0, 0).
        val clockwise =
            Room(
                id = RoomId("cw"),
                walls = SceneFixtures.walls("cw", 4000, 3000, 4000, 3000),
                corners =
                    persistentListOf(
                        CornerSpec.Unspecified,
                        CornerSpec.DeclaredRight(TurnDirection.RIGHT),
                        CornerSpec.DeclaredRight(TurnDirection.RIGHT),
                        CornerSpec.DeclaredRight(TurnDirection.RIGHT),
                    ),
                isClosed = true,
                placement = RoomPlacement(4000.0, 0.0, PI, null),
            )
        DoorSwing.entries.forEach { swing ->
            val reference =
                scene(rectangle.copy(openings = persistentListOf(opening("d", OpeningType.DOOR, 1000L..1800L, swing))))
            val mirrored =
                scene(
                    clockwise.copy(
                        openings =
                            persistentListOf(
                                opening("d", OpeningType.DOOR, 2200L..3000L, swing, WallId("cw-w0")),
                            ),
                    ),
                )
            val expected = reference.elements.filterIsInstance<SceneElement.Arc>().single()
            val actual = mirrored.elements.filterIsInstance<SceneElement.Arc>().single()
            assertClose(expected.center, actual.center, 1e-6)
            assertEquals(expected.radiusMm, actual.radiusMm, 1e-9)
            assertClose(arcPoint(expected, 0.0), arcPoint(actual, 0.0), 1e-6)
            assertClose(arcPoint(expected, 1.0), arcPoint(actual, 1.0), 1e-6)
        }
    }

    private fun arcPoint(
        arc: SceneElement.Arc,
        fraction: Double,
    ): Vec2 {
        val angle = arc.startRad + arc.sweepRad * fraction
        return arc.center + Vec2(kotlin.math.cos(angle), kotlin.math.sin(angle)) * arc.radiusMm
    }

    @Test
    fun `given window passage and misplaced opening when built then each has its symbol and style`() {
        val room =
            rectangle.copy(
                openings =
                    persistentListOf(
                        opening("w", OpeningType.WINDOW, 500L..1500L),
                        opening("p", OpeningType.PASSAGE, 2000L..2900L),
                        opening("x", OpeningType.PASSAGE, 3500L..4300L, wallId = WallId("ref-rectangle-w2")),
                    ),
            )
        val built = scene(room)
        val window = built.of(SceneTarget.Opening(room.id, OpeningId("w")))
        assertEquals(2, window.size)
        val middle = window[1] as SceneElement.Polyline
        assertClose(Vec2(500.0, -60.0), middle.points[0])
        assertClose(Vec2(1500.0, -60.0), middle.points[1])
        assertEquals(1, built.of(SceneTarget.Opening(room.id, OpeningId("p"))).size)
        val misplaced = built.of(SceneTarget.Opening(room.id, OpeningId("x")))
        assertEquals(listOf(SceneStyle.OPENING_OUT_OF_BOUNDS), misplaced.map { it.style })
        // Wall 2 runs from (4000, 3000) to (0, 3000), so the opening reaches beyond V_3 to x = -300.
        assertPolygon(
            listOf(Vec2(500.0, 3000.0), Vec2(-300.0, 3000.0), Vec2(-300.0, 3120.0), Vec2(500.0, 3120.0)),
            (misplaced.single() as SceneElement.Fill).polygon,
        )
    }

    @Test
    fun `given an opening on a wall of thickness zero when built then it is a segment on the line`() {
        val thin =
            rectangle.copy(
                walls = rectangle.walls.map { it.copy(thickness = Length.ZERO) }.toImmutableList(),
                openings = persistentListOf(opening("w", OpeningType.WINDOW, 500L..1500L)),
            )
        val elements = scene(thin).of(SceneTarget.Opening(thin.id, OpeningId("w")))
        val line = elements.single() as SceneElement.Polyline
        assertClose(Vec2(500.0, 0.0), line.points[0])
        assertClose(Vec2(1500.0, 0.0), line.points[1])
    }

    @Test
    fun `given a rectangle when built then every wall has a dimension with its length outside the band`() {
        val built = scene(rectangle)
        val dimensions = built.elements.filter { it.style == SceneStyle.DIMENSION && it.target is SceneTarget.Wall }
        val labels = dimensions.filterIsInstance<SceneElement.Label>()
        assertEquals(
            listOf(4000.0, 3000.0, 4000.0, 3000.0).map { LabelText.Dimension(it) },
            labels.map { it.text },
        )
        val first = built.of(SceneTarget.Wall(rectangle.id, wall0))
        val line = first.filterIsInstance<SceneElement.Polyline>().single()
        assertClose(Vec2(0.0, -420.0), line.points[0])
        assertClose(Vec2(4000.0, -420.0), line.points[1])
        assertClose(Vec2(2000.0, -570.0), labels[0].anchor)
        // Text stays upright: wall 2 runs to -x and wall 3 to -y, so both read at 0 and +90 degrees.
        listOf(0.0, PI / 2.0, 0.0, PI / 2.0).zip(labels).forEach { (expected, label) ->
            assertEquals(expected, label.rotationRad, 1e-12)
        }
        val extensions = built.elements.filter { it.style == SceneStyle.DIMENSION && it.target == null }
        assertEquals(8, extensions.size)
        val firstExtension = extensions.first() as SceneElement.Polyline
        assertClose(Vec2(0.0, -170.0), firstExtension.points[0])
        assertClose(Vec2(0.0, -495.0), firstExtension.points[1])
    }

    @Test
    fun `given a diagonal when built then line and length label select it`() {
        val room = rectangle.copy(diagonals = persistentListOf(Diagonal(DiagonalId("d0"), 0, 2, Length(5000))))
        val elements = scene(room).of(SceneTarget.Diagonal(room.id, DiagonalId("d0")))
        val line = elements[0] as SceneElement.Polyline
        assertEquals(SceneStyle.DIAGONAL, line.style)
        assertClose(Vec2(0.0, 0.0), line.points[0])
        assertClose(Vec2(4000.0, 3000.0), line.points[1])
        val label = elements[1] as SceneElement.Label
        assertEquals(LabelText.Dimension(5000.0), label.text)
        assertClose(Vec2(2000.0, 1500.0), label.anchor)
        assertEquals(StrictMath.atan2(3.0, 4.0), label.rotationRad, 1e-12)
    }

    @Test
    fun `given measured corners when built then their interior angles are labelled inside the room`() {
        val reference = ReferenceRooms.SLOPE
        val built = scene(reference.room)
        val angles = built.elements.filter { (it as? SceneElement.Label)?.text is LabelText.InteriorAngle }
        assertEquals(listOf(2, 3), angles.map { (it.target as SceneTarget.Corner).index })
        angles.forEach {
            val label = it as SceneElement.Label
            val index = (label.target as SceneTarget.Corner).index
            val degrees = (label.text as LabelText.InteriorAngle).degrees
            assertEquals(reference.expectedInteriorAnglesDeg[index], degrees, 0.01)
            assertTrue(insideOf(reference.vertices, label.anchor), "$label")
        }
    }

    @Test
    fun `given a reflex measured corner when built then its angle exceeds 180 degrees and sits inside`() {
        val reference = ReferenceRooms.BAY
        val angles =
            scene(
                reference.room,
            ).elements.mapNotNull { it as? SceneElement.Label }.filter { it.text is LabelText.InteriorAngle }
        val reflex = angles.single { (it.target as SceneTarget.Corner).index == 3 }
        assertEquals(reference.expectedInteriorAnglesDeg[3], (reflex.text as LabelText.InteriorAngle).degrees, 0.01)
        assertTrue(insideOf(reference.vertices, reflex.anchor))
    }

    @Test
    fun `given name type and notes when built then the labels stack around the interior point`() {
        val room = rectangle.copy(name = "Kitchen", typeKey = RoomTypeKey("kitchen"), notes = "Tiles")
        val without =
            scene(room).elements.filter {
                it.target == SceneTarget.RoomBody(room.id) &&
                    it is SceneElement.Label
            }
        val texts = without.map { (it as SceneElement.Label).text }
        assertEquals(listOf(LabelText.Literal("Kitchen"), LabelText.RoomType(RoomTypeKey("kitchen"))), texts.take(2))
        assertEquals(12_000_000.0, (texts[2] as LabelText.Area).mm2, 1e-3)
        listOf(1800.0, 1500.0, 1200.0).zip(without).forEach { (y, label) ->
            assertClose(Vec2(2000.0, y), (label as SceneElement.Label).anchor, 1e-9)
        }
        val with = scene(room, SceneOptions(includeNotes = true)).elements.filterIsInstance<SceneElement.Label>()
        assertEquals(LabelText.Literal("Tiles"), with.last { it.target is SceneTarget.RoomBody }.text)
    }

    @Test
    fun `given a room label when built then its lines form one block and single-line labels have none`() {
        val room = rectangle.copy(name = "Kitchen", typeKey = RoomTypeKey("kitchen"), notes = "Tiles")
        val labels = scene(room, SceneOptions(includeNotes = true)).elements.filterIsInstance<SceneElement.Label>()
        val (block, single) = labels.partition { it.target == SceneTarget.RoomBody(room.id) }
        assertEquals(4, block.size)
        val anchor = requireNotNull(block.map { it.blockAnchor }.distinct().single())
        assertClose(Vec2(2000.0, 1500.0), anchor, 1e-9)
        assertTrue(single.isNotEmpty())
        assertTrue(single.all { it.blockAnchor == null }, "dimensions and angles are single lines")
    }

    @Test
    fun `given a U shape whose centroid lies outside when built then the room label lies inside`() {
        val reference = ReferenceRooms.U_SHAPE
        val area =
            scene(
                reference.room,
            ).elements.filterIsInstance<SceneElement.Label>().single { it.text is LabelText.Area }
        assertTrue(insideOf(reference.vertices, area.anchor), "${area.anchor}")
    }

    @Test
    fun `given a placement when built then every point is rotated and moved into the world`() {
        val placed = rectangle.copy(placement = RoomPlacement(10_000.0, 5000.0, PI / 2.0, null))
        val markers =
            scene(placed)
                .elements
                .filter {
                    it.target is SceneTarget.Corner
                }.map { (it as SceneElement.Polyline).points[0] }
        assertClose(Vec2(10_000.0, 5000.0), markers[0], 1e-9)
        assertClose(Vec2(10_000.0, 9000.0), markers[1], 1e-9)
        assertClose(Vec2(7000.0, 9000.0), markers[2], 1e-9)
        assertClose(Vec2(7000.0, 5000.0), markers[3], 1e-9)
    }

    @Test
    fun `given an acute corner beyond the miter limit when built then its bevel selects the corner`() {
        val sliver = roomFromVertices("sliver", listOf(vertex(0, 0), vertex(4000, 0), vertex(0, 500)))
        val bevels = scene(sliver).elements.filter { it is SceneElement.Fill && it.target is SceneTarget.Corner }
        assertTrue(bevels.isNotEmpty())
        assertTrue(bevels.all { it.style == SceneStyle.WALL_BAND })
    }

    @Test
    fun `given an open chain when built then walls dimensions and openings follow the walked chain`() {
        val room =
            SceneFixtures.open.copy(
                name = "Hall",
                openings = persistentListOf(opening("o", OpeningType.PASSAGE, 500L..1400L, wallId = WallId("open-w0"))),
            )
        val built = scene(room)
        val wall1 = built.of(SceneTarget.Wall(room.id, WallId("open-w1"))).first() as SceneElement.Polyline
        assertClose(Vec2(3000.0, 0.0), wall1.points[0])
        assertClose(Vec2(3000.0, 2000.0), wall1.points[1])
        val passage = built.of(SceneTarget.Opening(room.id, OpeningId("o"))).single() as SceneElement.Polyline
        assertClose(Vec2(500.0, 0.0), passage.points[0])
        // The chain turns left, so the outer side of w0 is -y and its dimension lies below it.
        val dimension =
            built
                .of(
                    SceneTarget.Wall(room.id, WallId("open-w0")),
                ).filterIsInstance<SceneElement.Label>()
                .single()
        assertEquals(LabelText.Dimension(3000.0), dimension.text)
        assertClose(Vec2(1500.0, -450.0), dimension.anchor)
        val name = built.elements.filterIsInstance<SceneElement.Label>().single { it.target is SceneTarget.RoomBody }
        assertClose(Vec2(2000.0, 2000.0 / 3.0), name.anchor, 1e-9)
    }

    @Test
    fun `given an open chain turning right when built then its outer side is to the left`() {
        val right =
            SceneFixtures.open.copy(
                corners =
                    persistentListOf(
                        SceneFixtures.open.corners[0],
                        CornerSpec.DeclaredRight(TurnDirection.RIGHT),
                    ),
            )
        val dimension =
            scene(
                right,
            ).of(SceneTarget.Wall(right.id, WallId("open-w0"))).filterIsInstance<SceneElement.Label>().single()
        assertClose(Vec2(1500.0, 450.0), dimension.anchor)
    }

    @Test
    fun `given a room without walls when built then only its name is drawn and without name nothing`() {
        val empty = Room(id = RoomId("empty"))
        assertNull(scene(empty).bounds)
        val named = scene(empty.copy(name = "New"))
        assertEquals(1, named.elements.size)
        assertClose(Vec2.ZERO, (named.elements.single() as SceneElement.Label).anchor)
    }

    @Test
    fun `given an impossible diagonal when built then the conflict marks it`() {
        val room = rectangle.copy(diagonals = persistentListOf(Diagonal(DiagonalId("d0"), 0, 2, Length(9000))))
        val evaluation = evaluate(room)
        assertEquals(ConflictReason.DIAGONAL_IMPOSSIBLE, (evaluation as RoomEvaluation.Conflict).reason)
        val diagonal =
            builder
                .buildRoom(
                    room,
                    evaluation,
                    SceneOptions(),
                ).of(SceneTarget.Diagonal(room.id, DiagonalId("d0")))
        assertEquals(SceneStyle.CONFLICT, diagonal.first().style)
        val conflictWalls =
            builder.buildRoom(room, evaluation, SceneOptions()).elements.filter {
                it.style ==
                    SceneStyle.CONFLICT &&
                    it.target is SceneTarget.Wall
            }
        assertEquals(4, conflictWalls.size)
    }

    @Test
    fun `given equal input when built twice then the scenes are equal`() {
        val room =
            rectangle.copy(
                name = "A",
                openings = persistentListOf(opening("d", OpeningType.DOOR, 1000L..1800L, DoorSwing.HINGE_LEFT_INWARD)),
            )
        val evaluation = evaluate(room)
        assertEquals(
            builder.buildRoom(room, evaluation, SceneOptions()),
            DefaultSceneBuilder().buildRoom(room, evaluation, SceneOptions()),
        )
    }

    @Test
    fun `given a scene when built then the bounds enclose every element and the arc circle`() {
        val room =
            rectangle.copy(
                openings = persistentListOf(opening("d", OpeningType.DOOR, 1000L..1800L, DoorSwing.HINGE_LEFT_INWARD)),
            )
        val bounds = scene(room).bounds!!
        // The dimension labels reach 420 + 150 + 75 mm beyond the 120 mm bands on every side.
        assertEquals(-645.0, bounds.minX, 1e-9)
        assertEquals(-645.0, bounds.minY, 1e-9)
        assertEquals(4645.0, bounds.maxX, 1e-9)
        assertEquals(3645.0, bounds.maxY, 1e-9)
    }

    @Test
    fun `given a project when built then rooms keep project order and export leaves out open and conflict rooms`() {
        val rooms = listOf(SceneFixtures.open, rectangle, SceneFixtures.conflict)
        val project = Project(ProjectId("p"), "P", Length(2500), rooms.toImmutableList(), 0L, 0L)
        val evaluations = rooms.associate { it.id to evaluate(it) }.toImmutableMap()
        val all = builder.build(project, evaluations, SceneOptions())
        assertEquals(rooms.map { it.id }, all.rooms.map { it.roomId })
        assertEquals(all.rooms.mapNotNull { it.bounds }.reduce(WorldBounds::union), all.bounds)
        val export = builder.build(project, evaluations, SceneOptions(exportableOnly = true))
        assertEquals(listOf(rectangle.id), export.rooms.map { it.roomId })
        val none = builder.build(project.copy(rooms = persistentListOf()), persistentMapOf(), SceneOptions())
        assertNull(none.bounds)
    }

    @Test
    fun `given missing or foreign evaluations when built then the call is rejected`() {
        val project = Project(ProjectId("p"), "P", Length(2500), persistentListOf(rectangle), 0L, 0L)
        assertThrows(IllegalArgumentException::class.java) { builder.build(project, persistentMapOf(), SceneOptions()) }
        val foreign = evaluate(SceneFixtures.open)
        assertThrows(IllegalArgumentException::class.java) { builder.buildRoom(rectangle, foreign, SceneOptions()) }
    }

    /** Even-odd containment with a margin: points closer than 1 mm to an edge count as outside. */
    private fun insideOf(
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
        val clear =
            polygon.indices.all { i ->
                val a = polygon[i]
                val b = polygon[(i + 1) % polygon.size]
                val d = b - a
                val t = ((point - a).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
                (point - (a + d * t)).length() > 1.0
            }
        return inside && clear
    }
}
