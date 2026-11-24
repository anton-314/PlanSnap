package app.plansnap.core.geometry.bands

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WallBandModelTest {
    @Test
    fun `given thickness count different from wall count when creating input then it is rejected`() {
        assertThrows<IllegalArgumentException> { input(120L, 120L) }
        assertThrows<IllegalArgumentException> { input(120L, 120L, 120L, 120L) }
    }

    @Test
    fun `given thickness outside the range when creating input then it is rejected`() {
        assertThrows<IllegalArgumentException> { input(120L, -1L, 120L) }
        assertThrows<IllegalArgumentException> { input(120L, GeometryTolerances.MAX_THICKNESS_MM + 1, 120L) }
    }

    @Test
    fun `given thicknesses at the range limits when creating input then it is accepted`() {
        val input = input(0L, GeometryTolerances.MAX_THICKNESS_MM, 120L)

        assertEquals(listOf(0L, GeometryTolerances.MAX_THICKNESS_MM, 120L), input.thicknessesMm)
    }

    @Test
    fun `given mutable thickness list when creating input then later changes do not leak in`() {
        val source = mutableListOf(120L, 120L, 120L)
        val input = BandInput(TRIANGLE, Winding.COUNTER_CLOCKWISE, source)
        source[0] = 999L

        assertEquals(listOf(120L, 120L, 120L), input.thicknessesMm)
        assertThrows<UnsupportedOperationException> { (input.thicknessesMm as MutableList<Long>)[0] = 1L }
    }

    @Test
    fun `given equal and different inputs when compared then equality follows all properties`() {
        val input = input(120L, 0L, 240L)

        assertEquals(input, input(120L, 0L, 240L))
        assertEquals(input.hashCode(), input(120L, 0L, 240L).hashCode())
        assertNotEquals(input, input(120L, 0L, 241L))
        assertNotEquals(input, BandInput(TRIANGLE, Winding.CLOCKWISE, listOf(120L, 0L, 240L)))
        val shifted = Polygon2(TRIANGLE.vertices.map { it + Vec2(1.0, 0.0) })
        assertNotEquals(input, BandInput(shifted, Winding.COUNTER_CLOCKWISE, listOf(120L, 0L, 240L)))
        assertFalse(input.equals("not an input"))
        assertNotEquals(input.hashCode(), input(120L, 0L, 241L).hashCode())
        assertNotEquals(
            input.hashCode(),
            BandInput(TRIANGLE, Winding.CLOCKWISE, listOf(120L, 0L, 240L)).hashCode(),
        )
        assertNotEquals(
            input.hashCode(),
            BandInput(shifted, Winding.COUNTER_CLOCKWISE, listOf(120L, 0L, 240L)).hashCode(),
        )
        assertTrue(input.toString().contains("thicknessesMm=[120, 0, 240]"))
    }

    @Test
    fun `given zero thickness walls when built then lines keep their wall index`() {
        val layout = DefaultWallBandBuilder().build(input(0L, 0L, 0L))

        assertEquals(listOf(0, 1, 2), layout.bands.map { it.wallIndex })
    }

    private companion object {
        val TRIANGLE = Polygon2(listOf(Vec2(0.0, 0.0), Vec2(3000.0, 0.0), Vec2(0.0, 3000.0)))

        fun input(vararg thicknessesMm: Long) = BandInput(TRIANGLE, Winding.COUNTER_CLOCKWISE, thicknessesMm.toList())
    }
}
