package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.RoomId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoomBuildersTest {
    private val rectangle = listOf(vertex(0, 0), vertex(4000, 0), vertex(4000, 3000), vertex(0, 3000))

    @Test
    fun `given a rectangle when built then walls are the edge lengths and ids derive from the prefix`() {
        val room = roomFromVertices("r1", rectangle, thicknessMm = 200L, name = "Living")

        assertEquals(RoomId("r1"), room.id)
        assertEquals("Living", room.name)
        assertEquals(listOf(4000L, 3000L, 4000L, 3000L), room.walls.map { it.length.mm })
        assertEquals(listOf("r1-w0", "r1-w1", "r1-w2", "r1-w3"), room.walls.map { it.id.value })
        assertTrue(room.walls.all { it.thickness.mm == 200L })
        assertTrue(room.isClosed)
    }

    @Test
    fun `given a rectangle when built then V0 is unspecified and perpendicular corners are declared right left`() {
        val room = roomFromVertices("r1", rectangle)

        assertEquals(CornerSpec.Unspecified, room.corners[0])
        room.corners.drop(1).forEach { assertEquals(CornerSpec.DeclaredRight(TurnDirection.LEFT), it) }
    }

    @Test
    fun `given a reflex corner when built then it is declared right with direction right`() {
        val l =
            listOf(
                vertex(0, 0),
                vertex(6000, 0),
                vertex(6000, 2000),
                vertex(3000, 2000),
                vertex(3000, 4000),
                vertex(0, 4000),
            )

        val room = roomFromVertices("l", l)

        assertEquals(CornerSpec.DeclaredRight(TurnDirection.RIGHT), room.corners[3])
    }

    @Test
    fun `given a 3-4-5 slope when built then the oblique corner is measured to thousandths of a degree`() {
        val slope = listOf(vertex(0, 0), vertex(6000, 0), vertex(6000, 3000), vertex(2000, 6000), vertex(0, 6000))

        val room = roomFromVertices("s", slope)

        assertEquals(CornerSpec.Measured(TurnDirection.LEFT, Angle(126_870)), room.corners[2])
        assertEquals(CornerSpec.Measured(TurnDirection.LEFT, Angle(143_130)), room.corners[3])
        assertEquals(5000L, room.walls[2].length.mm)
    }

    @Test
    fun `given a collinear vertex when built then the corner is a straight angle`() {
        val withMidpoint = listOf(vertex(0, 0), vertex(2000, 0), vertex(4000, 0), vertex(4000, 3000), vertex(0, 3000))

        val room = roomFromVertices("c", withMidpoint)

        assertEquals(CornerSpec.Measured(TurnDirection.LEFT, Angle.STRAIGHT), room.corners[1])
    }

    @Test
    fun `given a clockwise outline when built then it is rejected`() {
        assertThrows<IllegalArgumentException> { roomFromVertices("cw", rectangle.reversed()) }
    }

    @Test
    fun `given fewer than three vertices when built then it is rejected`() {
        assertThrows<IllegalArgumentException> { roomFromVertices("two", rectangle.take(2)) }
    }

    @Test
    fun `given blank prefix when built then the room id is rejected`() {
        assertThrows<IllegalArgumentException> { roomFromVertices(" ", rectangle) }
    }

    @Test
    fun `given wall id helper then it matches the ids of the built room`() {
        val room = roomFromVertices("x", rectangle)

        assertEquals(room.walls[2].id, wallIdOf("x", 2))
    }
}
