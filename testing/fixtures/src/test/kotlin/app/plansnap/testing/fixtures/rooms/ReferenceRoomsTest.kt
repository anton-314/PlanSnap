package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.ShapeStatus
import app.plansnap.testing.fixtures.IndependentGeometry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ReferenceRoomsTest {
    private val determined = ReferenceRooms.ALL.filter { it.inputDeterminesShape }

    @Test
    fun `given catalog then it contains every case of TESTING section 3 with a unique key`() {
        val keys = ReferenceRooms.ALL.map { it.key }

        assertEquals(keys.toSet().size, keys.size)
        assertEquals(
            setOf(
                "rectangle",
                "square",
                "l-shape",
                "u-shape",
                "t-shape",
                "niche",
                "bay",
                "slope",
                "cross",
                "tiny",
                "huge",
                "trapezoid-3-3-3-5",
                "trapezoid-3-3-3-5-diagonal",
            ),
            keys.toSet(),
        )
    }

    @Test
    fun `given every reference room then expected area equals the shoelace area of its coordinates`() {
        ReferenceRooms.ALL.forEach {
            val area = IndependentGeometry.shoelaceArea(it.vertices)
            assertEquals(it.expectedAreaMm2, area, it.expectedAreaMm2 * 1e-12, it.key)
        }
    }

    @Test
    fun `given every reference room then expected perimeter equals the sum of its edge lengths`() {
        ReferenceRooms.ALL.forEach {
            assertEquals(it.expectedPerimeterMm, IndependentGeometry.edgeLengths(it.vertices).sum(), 1e-6, it.key)
        }
    }

    @Test
    fun `given every reference room then expected interior angles equal the angles of its coordinates`() {
        ReferenceRooms.ALL.forEach { reference ->
            val measured = IndependentGeometry.interiorAnglesDeg(reference.vertices)

            assertEquals(measured.size, reference.expectedInteriorAnglesDeg.size, reference.key)
            measured.zip(reference.expectedInteriorAnglesDeg).forEachIndexed { i, (actual, expected) ->
                assertEquals(expected, actual, 1e-9, "${reference.key} corner $i")
            }
        }
    }

    @Test
    fun `given every reference room then interior angles sum to n minus two times 180 degrees`() {
        ReferenceRooms.ALL.forEach {
            assertEquals((it.vertices.size - 2) * 180.0, it.expectedInteriorAnglesDeg.sum(), 1e-9, it.key)
        }
    }

    @Test
    fun `given every reference room then the outline is counter-clockwise and simple`() {
        ReferenceRooms.ALL.forEach {
            assertTrue(IndependentGeometry.signedArea(it.vertices) > 0.0, it.key)
            assertTrue(IndependentGeometry.isSimple(it.vertices), it.key)
        }
    }

    @Test
    fun `given every reference room then it is a closed room with one corner per wall and only V0 unspecified`() {
        ReferenceRooms.ALL.forEach {
            assertTrue(it.room.isClosed, it.key)
            assertEquals(it.room.walls.size, it.room.corners.size, it.key)
            assertEquals(it.vertices.size, it.room.walls.size, it.key)
            assertTrue(it.room.corners.first() is CornerSpec.Unspecified, it.key)
        }
    }

    @Test
    fun `given every reference room then wall lengths are within the permitted limits`() {
        ReferenceRooms.ALL.forEach { reference ->
            reference.room.walls.forEach {
                val limits = GeometryTolerances.MIN_WALL_MM..GeometryTolerances.MAX_WALL_MM
                assertTrue(it.length.mm in limits, reference.key)
            }
        }
    }

    @Test
    fun `given reference rooms whose input fixes the shape then wall lengths are the rounded edge lengths`() {
        determined.forEach { reference ->
            val edges = IndependentGeometry.edgeLengths(reference.vertices)

            assertEquals(edges.map { Math.round(it) }, reference.room.walls.map { it.length.mm }, reference.key)
            val perimeter =
                reference.room.walls
                    .sumOf { it.length.mm }
                    .toDouble()
            assertEquals(reference.expectedPerimeterMm, perimeter, 1e-9, reference.key)
        }
    }

    @Test
    fun `given reference rooms whose input fixes the shape then entered corners match the expected angles`() {
        determined.forEach { reference ->
            reference.room.corners.forEachIndexed { i, corner ->
                if (corner !is CornerSpec.Unspecified) {
                    val enteredInterior = enteredInteriorDeg(corner)
                    val expected = reference.expectedInteriorAnglesDeg[i]
                    assertEquals(expected, enteredInterior, 6e-4, "${reference.key} corner $i")
                }
            }
        }
    }

    @Test
    fun `given reference rooms whose input fixes the shape then the raw traverse closes within 1 mm`() {
        determined.forEach {
            val gap = IndependentGeometry.traverseClosureGapMm(it.room)

            assertTrue(gap <= GeometryTolerances.EXACT_CLOSURE_MM, "${it.key} gap $gap")
        }
    }

    @Test
    fun `given reference rooms whose input fixes the shape then the expected status is confirmed`() {
        determined.forEach { assertEquals(ShapeStatus.CONFIRMED, it.expectedStatus, it.key) }
    }

    @Test
    fun `given the mandatory trapezoid then area is 8 times root 2 square meters`() {
        val trapezoid = ReferenceRooms.TRAPEZOID_3_3_3_5

        assertEquals(8.0 * sqrt(2.0), trapezoid.expectedAreaSquareMeters, 1e-9)
        assertEquals(8.0 * sqrt(2.0), IndependentGeometry.shoelaceArea(trapezoid.vertices) / 1e6, 1e-9)
        assertEquals(14.0, trapezoid.expectedPerimeterMm / 1000.0, 1e-12)
    }

    @Test
    fun `given the mandatory trapezoid then its coordinates have the sides 5, 3, 3 and 3 meters`() {
        val trapezoid = ReferenceRooms.TRAPEZOID_3_3_3_5

        val edges = IndependentGeometry.edgeLengths(trapezoid.vertices)

        assertEquals(listOf(5000.0, 3000.0, 3000.0, 3000.0), edges.map { Math.round(it).toDouble() })
        edges.zip(listOf(5000.0, 3000.0, 3000.0, 3000.0)).forEach { (actual, expected) ->
            assertEquals(expected, actual, 1e-9)
        }
    }

    @Test
    fun `given the mandatory trapezoid then base angles are acos of one third`() {
        val angles = ReferenceRooms.TRAPEZOID_3_3_3_5.expectedInteriorAnglesDeg

        val base = Math.toDegrees(kotlin.math.acos(1.0 / 3.0))

        listOf(base, base, 180.0 - base, 180.0 - base).forEachIndexed { i, expected ->
            assertEquals(expected, angles[i], 1e-9, "corner $i")
        }
    }

    @Test
    fun `given the mandatory trapezoid input then it is the open 5000 3000 3000 3000 input`() {
        val reference = ReferenceRooms.TRAPEZOID_3_3_3_5

        assertEquals(listOf(5000L, 3000L, 3000L, 3000L), reference.room.walls.map { it.length.mm })
        assertTrue(
            reference.room.corners
                .drop(1)
                .all { it is CornerSpec.DeclaredRight },
        )
        assertEquals(ShapeStatus.PROPOSED, reference.expectedStatus)
        assertTrue(!reference.inputDeterminesShape)
        assertTrue(IndependentGeometry.traverseClosureGapMm(reference.room) > 1000.0)
    }

    @Test
    fun `given the diagonal variant then the diagonal equals the distance of its corners and no status is asserted`() {
        val reference = ReferenceRooms.TRAPEZOID_WITH_DIAGONAL
        val diagonal = reference.room.diagonals.single()
        val from = reference.vertices[diagonal.fromCorner]
        val to = reference.vertices[diagonal.toCorner]

        assertEquals(kotlin.math.hypot(to.x - from.x, to.y - from.y), diagonal.length.mm.toDouble(), 0.5)
        assertNull(reference.expectedStatus)
        assertEquals(ReferenceRooms.TRAPEZOID_3_3_3_5.expectedAreaMm2, reference.expectedAreaMm2, 0.0)
    }

    @Test
    fun `given the extreme cases then they sit exactly on the wall limits`() {
        assertEquals(
            GeometryTolerances.MIN_WALL_MM,
            ReferenceRooms.TINY.room.walls
                .minOf { it.length.mm },
        )
        assertEquals(
            GeometryTolerances.MAX_WALL_MM,
            ReferenceRooms.HUGE.room.walls
                .maxOf { it.length.mm },
        )
        assertEquals(100.0, ReferenceRooms.TINY.expectedAreaMm2, 0.0)
        assertEquals(10_000.0, ReferenceRooms.HUGE.expectedAreaSquareMeters, 0.0)
    }

    @Test
    fun `given hand derived areas then they match the decomposition into rectangles`() {
        val rectangleParts =
            mapOf(
                ReferenceRooms.RECTANGLE to 4000.0 * 3000.0,
                ReferenceRooms.SQUARE to 3000.0 * 3000.0,
                ReferenceRooms.L_SHAPE to 6000.0 * 2000.0 + 3000.0 * 2000.0,
                ReferenceRooms.U_SHAPE to 6000.0 * 4000.0 - 2000.0 * 2500.0,
                ReferenceRooms.T_SHAPE to 2000.0 * 3000.0 + 6000.0 * 2000.0,
                ReferenceRooms.NICHE to 4000.0 * 3000.0 + 1000.0 * 500.0,
                ReferenceRooms.CROSS to 5 * 2000.0 * 2000.0,
                ReferenceRooms.BAY to 4000.0 * 3000.0 + (2000.0 + 800.0) / 2.0 * 800.0,
                ReferenceRooms.SLOPE to 2000.0 * 6000.0 + (3000.0 + 6000.0) / 2.0 * 4000.0,
            )

        rectangleParts.forEach { (reference, area) ->
            assertEquals(area, reference.expectedAreaMm2, 1e-6, reference.key)
        }
    }

    @Test
    fun `given reference rooms then ids are unique across the catalog`() {
        val roomIds = ReferenceRooms.ALL.map { it.room.id }
        val wallIds = ReferenceRooms.ALL.flatMap { r -> r.room.walls.map { it.id } }

        assertEquals(roomIds.toSet().size, roomIds.size)
        assertEquals(wallIds.toSet().size, wallIds.size)
    }

    private fun enteredInteriorDeg(corner: CornerSpec): Double {
        val alpha =
            when (corner) {
                is CornerSpec.DeclaredRight -> 90.0
                is CornerSpec.Measured -> corner.angle.milliDegrees / 1000.0
                CornerSpec.Unspecified -> error("unspecified")
            }
        return if (corner.direction!!.sign > 0) alpha else 360.0 - alpha
    }
}
