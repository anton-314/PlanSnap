package app.plansnap.core.domain.report

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.rooms.ReferenceRoom
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.math.hypot

/** Table tests of the area report (DOMAIN section 7, REQ-MOD-005, REQ-MOD-009, REQ-MOD-010). */
class DefaultAreaReportCalculatorTest {
    private val calculator = DefaultAreaReportCalculator()
    private val analyzer = DefaultPolygonAnalyzer()
    private val defaultHeight = Length(2500)

    private fun shapeOf(reference: ReferenceRoom): EvaluatedShape {
        val vertices = reference.vertices
        val n = vertices.size
        val lengths =
            (0 until n).map {
                hypot(
                    vertices[(it + 1) % n].x - vertices[it].x,
                    vertices[(it + 1) % n].y - vertices[it].y,
                )
            }
        val polygon = Polygon2(vertices)
        return EvaluatedShape(
            outline = polygon,
            winding = Winding.COUNTER_CLOCKWISE,
            lengthsMm = lengths.toImmutableList(),
            turnsRad = List(n) { 0.0 }.toImmutableList(),
            areaMm2 = analyzer.areaMm2(polygon),
            perimeterMm = analyzer.perimeterMm(polygon),
        )
    }

    private fun confirmed(
        room: Room,
        reference: ReferenceRoom,
    ) = RoomEvaluation.Confirmed(room.id, shapeOf(reference), persistentMapOf())

    private fun projectOf(vararg rooms: Room) =
        Project(ProjectId("p"), "Flat", defaultHeight, persistentListOf(*rooms), 1, 2)

    private fun report(
        project: Project,
        vararg evaluations: RoomEvaluation,
    ) = calculator.report(project, evaluations.associateBy { it.roomId }.toImmutableMap())

    @Test
    fun `every reference room reports its analytic area, perimeter and volume`() {
        ReferenceRooms.ALL.forEach { reference ->
            val room = reference.room
            val entry = report(projectOf(room), confirmed(room, reference)).entries.single()
            val metrics = entry.metrics!!
            assertEquals(
                reference.expectedAreaMm2,
                metrics.areaMm2,
                TOLERANCE * reference.expectedAreaMm2,
                reference.key,
            )
            assertEquals(
                reference.expectedPerimeterMm,
                metrics.perimeterMm,
                TOLERANCE * reference.expectedPerimeterMm,
                reference.key,
            )
            assertEquals(metrics.areaMm2 * 2500.0, metrics.volumeMm3, 0.0, reference.key)
            assertEquals(metrics.areaMm2, metrics.creditedAreaMm2, 0.0, reference.key)
            assertEquals(ShapeStatus.CONFIRMED, entry.status)
        }
    }

    @Test
    fun `trapezoid has area 8 sqrt 2 square meters`() {
        val reference = ReferenceRooms.TRAPEZOID_3_3_3_5
        val metrics = report(projectOf(reference.room), confirmed(reference.room, reference)).totals
        assertEquals(8.0 * Math.sqrt(2.0) * 1_000_000.0, metrics.areaMm2, 1e-3)
    }

    @Test
    fun `credit factor scales the area but not area, perimeter or volume`() {
        listOf(0 to 0.0, 1 to 0.01, 33 to 0.33, 50 to 0.5, 100 to 1.0).forEach { (percent, ratio) ->
            val reference = ReferenceRooms.RECTANGLE
            val room = reference.room.copy(areaFactor = AreaFactor(percent))
            val metrics = report(projectOf(room), confirmed(room, reference)).entries.single().metrics!!
            assertEquals(12_000_000.0, metrics.areaMm2, 1e-6)
            assertEquals(12_000_000.0 * ratio, metrics.creditedAreaMm2, 1e-6, "factor $percent")
            assertEquals(14_000.0, metrics.perimeterMm, 1e-9)
            assertEquals(12_000_000.0 * 2500.0, metrics.volumeMm3, 1e-3)
        }
    }

    @Test
    fun `height uses the room value, otherwise the project default`() {
        val reference = ReferenceRooms.RECTANGLE
        val own = reference.room.copy(heightOverride = Length(3000))
        val other = ReferenceRooms.SQUARE.room
        val result =
            report(
                projectOf(own, other),
                confirmed(own, reference),
                confirmed(other, ReferenceRooms.SQUARE),
            )
        assertEquals(12_000_000.0 * 3000.0, result.entries[0].metrics!!.volumeMm3, 1e-3)
        assertEquals(ReferenceRooms.SQUARE.expectedAreaMm2 * 2500.0, result.entries[1].metrics!!.volumeMm3, 1e-3)
    }

    @Test
    fun `totals sum the unrounded values in project order`() {
        val rectangle = ReferenceRooms.RECTANGLE
        val trapezoid = ReferenceRooms.TRAPEZOID_3_3_3_5
        val a = rectangle.room.copy(name = "A", typeKey = RoomTypeKey("living"), areaFactor = AreaFactor(50))
        val b = trapezoid.room.copy(name = "B", heightOverride = Length(2000))
        val result = report(projectOf(a, b), confirmed(a, rectangle), confirmed(b, trapezoid))
        val (first, second) = result.entries.map { it.metrics!! }
        assertEquals(listOf("A", "B"), result.entries.map { it.name })
        assertEquals(RoomTypeKey("living"), result.entries[0].typeKey)
        assertEquals(first.areaMm2 + second.areaMm2, result.totals.areaMm2, 0.0)
        assertEquals(first.creditedAreaMm2 + second.creditedAreaMm2, result.totals.creditedAreaMm2, 0.0)
        assertEquals(first.perimeterMm + second.perimeterMm, result.totals.perimeterMm, 0.0)
        assertEquals(first.volumeMm3 + second.volumeMm3, result.totals.volumeMm3, 0.0)
        assertEquals(6_000_000.0 + second.areaMm2, result.totals.creditedAreaMm2, 1e-6)
    }

    @Test
    fun `open and conflict rooms have no metrics and stay out of the totals`() {
        val reference = ReferenceRooms.RECTANGLE
        val ok = reference.room
        val open = Room(RoomId("open"), name = "Open")
        val conflict = Room(RoomId("conflict"), name = "Conflict")
        val result =
            report(
                projectOf(open, ok, conflict),
                RoomEvaluation.Open(open.id, persistentListOf(Vec2(0.0, 0.0)), persistentMapOf()),
                confirmed(ok, reference),
                RoomEvaluation.Conflict(
                    conflict.id,
                    ConflictReason.MEASUREMENTS_INCONSISTENT,
                    null,
                    null,
                    persistentMapOf(),
                ),
            )
        assertEquals(
            listOf(ShapeStatus.OPEN, ShapeStatus.CONFIRMED, ShapeStatus.CONFLICT),
            result.entries.map { it.status },
        )
        assertNull(result.entries[0].metrics)
        assertNull(result.entries[2].metrics)
        assertEquals(12_000_000.0, result.totals.areaMm2, 1e-6)
        assertEquals(14_000.0, result.totals.perimeterMm, 1e-9)
    }

    @Test
    fun `adjusted rooms report the displayed shape`() {
        val reference = ReferenceRooms.L_SHAPE
        val room = reference.room
        val evaluation = RoomEvaluation.Adjusted(room.id, shapeOf(reference), persistentMapOf())
        val entry = report(projectOf(room), evaluation).entries.single()
        assertEquals(ShapeStatus.ADJUSTED, entry.status)
        assertEquals(reference.expectedAreaMm2, entry.metrics!!.areaMm2, TOLERANCE * reference.expectedAreaMm2)
    }

    @Test
    fun `empty project reports no entries and zero totals`() {
        val result = report(projectOf())
        assertEquals(0, result.entries.size)
        assertEquals(AreaMetrics.ZERO, result.totals)
    }

    @Test
    fun `project without a shaped room reports zero totals`() {
        val open = Room(RoomId("open"))
        val result =
            report(projectOf(open), RoomEvaluation.Open(open.id, persistentListOf(Vec2(0.0, 0.0)), persistentMapOf()))
        assertEquals(AreaMetrics.ZERO, result.totals)
    }

    @Test
    fun `a missing evaluation is rejected`() {
        val room = Room(RoomId("r"))
        assertThrows(IllegalArgumentException::class.java) { report(projectOf(room)) }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
