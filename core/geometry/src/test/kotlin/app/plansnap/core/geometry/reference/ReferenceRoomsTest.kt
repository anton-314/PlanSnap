package app.plansnap.core.geometry.reference

import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.solver.interiorAnglesDeg
import app.plansnap.core.geometry.solver.shapeDistanceMm
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.rooms.ReferenceRoom
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * End-to-end check of every analytic reference room of TESTING section 3 over the merged pipeline: closure
 * classification, walk or adjustment or solver, polygon analysis. The expected values are the hand-derived literals of
 * [ReferenceRooms] (REQ-MOD-004, REQ-MOD-009).
 */
class ReferenceRoomsTest {
    private val analyzer = DefaultPolygonAnalyzer()

    @Test
    fun `given the fixture then it holds the thirteen reference rooms with unique keys`() {
        assertEquals(ROOM_COUNT, ReferenceRooms.ALL.size)
        assertEquals(
            ROOM_COUNT,
            ReferenceRooms.ALL
                .map { it.key }
                .toSet()
                .size,
        )
    }

    @TestFactory
    fun `given reference room when run through the pipeline then area perimeter and angles match`() =
        eachRoom { room ->
            val result = ReferencePipeline.run(room)
            assertEquals(room.expectedAreaMm2, analyzer.areaMm2(result.outline), room.expectedAreaMm2 * AREA_RELATIVE)
            assertEquals(room.expectedPerimeterMm, analyzer.perimeterMm(result.outline), PERIMETER_MM)
            val angles = interiorAnglesDeg(result.outline)
            assertEquals(room.expectedInteriorAnglesDeg.size, angles.size)
            room.expectedInteriorAnglesDeg.forEachIndexed { i, expected ->
                assertEquals(expected, angles[i], ANGLE_DEG, "${room.key}: interior angle at corner $i")
            }
        }

    @TestFactory
    fun `given reference room when run through the pipeline then the shape equals the analytic vertices`() =
        eachRoom { room ->
            val outline = ReferencePipeline.run(room).outline
            assertEquals(room.vertices.size, outline.vertices.size)
            assertEquals(0.0, shapeDistanceMm(outline.vertices, room.vertices), SHAPE_DISTANCE_MM, room.key)
        }

    @TestFactory
    fun `given reference room when run through the pipeline then the outline is simple and counter-clockwise`() =
        eachRoom { room ->
            val outline = ReferencePipeline.run(room).outline
            assertTrue(analyzer.isSimple(outline), room.key)
            assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(outline), room.key)
            assertTrue(analyzer.signedAreaMm2(outline) > 0.0, room.key)
        }

    @TestFactory
    fun `given reference room when run through the pipeline then closure class and status match`() =
        eachRoom { room ->
            val result = ReferencePipeline.run(room)
            when (room) {
                ReferenceRooms.TRAPEZOID_3_3_3_5, ReferenceRooms.TRAPEZOID_WITH_DIAGONAL -> {
                    assertEquals(ClosureClass.SHAPE_DEVIATION, result.closureClass, room.key)
                    assertEquals(ShapeSource.SOLVED, result.source, room.key)
                }

                else -> {
                    assertEquals(ClosureClass.EXACT, result.closureClass, room.key)
                    assertEquals(ShapeSource.WALKED, result.source, room.key)
                }
            }
            room.expectedStatus?.let { assertEquals(it, result.status, room.key) }
            if (room.inputDeterminesShape) assertEquals(ShapeStatus.CONFIRMED, result.status, room.key)
        }

    @TestFactory
    fun `given reference room when run twice then the outline is bit-identical`() =
        eachRoom { room ->
            val first = ReferencePipeline.run(room)
            val second = ReferencePipeline.run(room)
            assertEquals(bits(first.outline), bits(second.outline), room.key)
            assertEquals(first.status, second.status, room.key)
        }

    @Test
    fun `given the diagonal trapezoid then the added diagonal determines the shape`() {
        val result = ReferencePipeline.run(ReferenceRooms.TRAPEZOID_WITH_DIAGONAL)
        assertEquals(ShapeStatus.CONFIRMED, result.status)
        assertEquals(ShapeStatus.PROPOSED, ReferencePipeline.run(ReferenceRooms.TRAPEZOID_3_3_3_5).status)
    }

    @Test
    fun `given the trapezoid then area is eight root two square meters`() {
        val result = ReferencePipeline.run(ReferenceRooms.TRAPEZOID_3_3_3_5)
        val expectedSquareMeters = 8.0 * StrictMath.sqrt(2.0)
        assertEquals(expectedSquareMeters, analyzer.areaMm2(result.outline) / MM2_PER_M2, TRAPEZOID_AREA_M2)
    }

    @Test
    fun `given a wall that is wrong by 50 mm then the pipeline no longer reproduces the reference`() {
        val room = ReferenceRooms.RECTANGLE
        val exact = room.room.toShapeInput()
        val wrong =
            ShapeInput(
                lengthsMm = exact.lengthsMm.mapIndexed { i, mm -> if (i == 0) mm + WRONG_WALL_MM else mm },
                corners = exact.corners,
            )
        assertEquals(ClosureClass.EXACT, ReferencePipeline.closureClass(exact))
        assertTrue(ReferencePipeline.closureClass(wrong) != ClosureClass.EXACT, "a 50 mm error must not be exact")
    }

    private fun eachRoom(check: (ReferenceRoom) -> Unit): List<DynamicTest> =
        ReferenceRooms.ALL.map { room -> DynamicTest.dynamicTest(room.key) { check(room) } }

    private fun bits(outline: Polygon2): List<Long> =
        outline.vertices.flatMap { listOf(it.x.toRawBits(), it.y.toRawBits()) }

    private companion object {
        const val ROOM_COUNT = 13
        const val AREA_RELATIVE = 2e-6
        const val PERIMETER_MM = 0.02
        const val ANGLE_DEG = 0.001
        const val SHAPE_DISTANCE_MM = 0.05
        const val MM2_PER_M2 = 1_000_000.0
        const val TRAPEZOID_AREA_M2 = 1e-6
        const val WRONG_WALL_MM = 50L
    }
}
