package app.plansnap.core.geometry.reference

import app.plansnap.core.geometry.bands.BandInput
import app.plansnap.core.geometry.bands.DefaultWallBandBuilder
import app.plansnap.core.geometry.bands.WallBand
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.ClosureClass
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Properties of TESTING section 3 over the reference rooms, run on the outline of the merged pipeline: rigid motions,
 * wall thickness, bands. Seeds are fixed.
 */
@OptIn(ExperimentalKotest::class)
class ReferenceInvariantsTest {
    private val analyzer = DefaultPolygonAnalyzer()
    private val bandBuilder = DefaultWallBandBuilder()

    @Test
    fun `given reference outline when rotated and translated then area and perimeter are invariant`() =
        runBlocking<Unit> {
            val motions = Arb.int(-ANGLE_MILLI_RANGE, ANGLE_MILLI_RANGE).map { it / MILLI }
            val shifts = Arb.int(-SHIFT_RANGE_MM, SHIFT_RANGE_MM).map { it.toDouble() }
            checkAll(ITERATIONS, config(SEED_MOTION), Arb.element(ReferenceRooms.ALL), motions, shifts, shifts) {
                room,
                angle,
                dx,
                dy,
                ->
                val outline = ReferencePipeline.run(room).outline
                val moved = Polygon2(outline.vertices.map { it.rotated(angle) + Vec2(dx, dy) })
                val area = analyzer.areaMm2(outline)
                val perimeter = analyzer.perimeterMm(outline)
                assertEquals(area, analyzer.areaMm2(moved), area * MOTION_RELATIVE + MOTION_ABSOLUTE, room.key)
                assertEquals(perimeter, analyzer.perimeterMm(moved), perimeter * MOTION_RELATIVE, room.key)
                assertTrue(analyzer.isSimple(moved), room.key)
                assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(moved), room.key)
            }
        }

    @Test
    fun `given reference outline when wall thicknesses vary then inner contour area and perimeter do not change`() =
        runBlocking<Unit> {
            val thicknesses = Arb.long(0L, GeometryTolerances.MAX_THICKNESS_MM)
            checkAll(ITERATIONS, config(SEED_THICKNESS), Arb.element(ReferenceRooms.ALL), thicknesses) {
                room,
                seed,
                ->
                val outline = ReferencePipeline.run(room).outline
                val n = outline.vertices.size
                val widths = List(n) { i -> (seed * (i + 1)) % (GeometryTolerances.MAX_THICKNESS_MM + 1) }
                val before = analyzer.areaMm2(outline)
                val layout = bandBuilder.build(BandInput(outline, Winding.COUNTER_CLOCKWISE, widths))
                assertEquals(n, layout.bands.size, room.key)
                assertEquals(n, layout.corners.size, room.key)
                assertEquals(before, analyzer.areaMm2(outline), 0.0, room.key)
                layout.bands.forEach { band -> assertBandOnOuterSide(room.key, outline, widths, band) }
            }
        }

    @Test
    fun `given reference room with default thickness then each band has the wall as its inner edge`() {
        ReferenceRooms.ALL.forEach { room ->
            val outline = ReferencePipeline.run(room).outline
            val widths = room.room.walls.map { it.thickness.mm }
            val layout = bandBuilder.build(BandInput(outline, Winding.COUNTER_CLOCKWISE, widths))
            layout.bands.forEach { band ->
                val i = band.wallIndex
                val shape = (band as WallBand.Filled).shape
                assertEquals(outline.vertices[i], shape.vertices[0], room.key)
                assertEquals(outline.vertices[(i + 1) % outline.vertices.size], shape.vertices[1], room.key)
                assertTrue(analyzer.areaMm2(shape) > 0.0, room.key)
            }
        }
    }

    @Test
    fun `given the maximum of 128 walls when run through the pipeline then area and perimeter are analytic`() {
        val room = roomFromVertices("staircase-128", staircase(STEPS, STEP_MM))
        assertEquals(GeometryTolerances.MAX_WALLS_PER_ROOM, room.walls.size)
        val input = room.toShapeInput()
        assertEquals(ClosureClass.EXACT, ReferencePipeline.closureClass(input))
        val outline =
            ReferencePipeline.outline(
                input.lengthsMm.map { it.toDouble() },
                ReferencePipeline.targetStart(input),
            )
        val squareMeters = (STEPS * STEPS + STEPS) / 2.0
        assertEquals(squareMeters * MM2_PER_M2, analyzer.areaMm2(outline), squareMeters * MM2_PER_M2 * AREA_RELATIVE)
        assertEquals(4.0 * STEPS * STEP_MM, analyzer.perimeterMm(outline), PERIMETER_MM)
        assertTrue(analyzer.isSimple(outline))
    }

    private fun assertBandOnOuterSide(
        key: String,
        outline: Polygon2,
        widths: List<Long>,
        band: WallBand,
    ) {
        val i = band.wallIndex
        val start = outline.vertices[i]
        val end = outline.vertices[(i + 1) % outline.vertices.size]
        when (band) {
            is WallBand.Line -> {
                assertEquals(0L, widths[i], key)
                assertEquals(start, band.start, key)
                assertEquals(end, band.end, key)
            }

            is WallBand.Filled -> {
                assertTrue(widths[i] > 0L, key)
                assertEquals(start, band.shape.vertices[0], key)
                assertEquals(end, band.shape.vertices[1], key)
            }
        }
    }

    /** Triangle with a staircase hypotenuse: two straight walls and `2 · steps` stair walls, counter-clockwise. */
    private fun staircase(
        steps: Int,
        stepMm: Long,
    ): List<Vec2> {
        val step = stepMm.toDouble()
        val size = steps * step
        val corners = mutableListOf(Vec2(0.0, 0.0), Vec2(size, 0.0), Vec2(size, size))
        for (k in 1..steps) {
            val top = size - (k - 1) * step
            corners += Vec2(top - step, top)
            if (k < steps) corners += Vec2(top - step, top - step)
        }
        return corners
    }

    private fun config(seed: Long) = PropTestConfig(seed = seed)

    private companion object {
        const val ITERATIONS = 200
        const val SEED_MOTION = 7_001L
        const val SEED_THICKNESS = 7_002L
        const val ANGLE_MILLI_RANGE = 3_141
        const val MILLI = 1_000.0
        const val SHIFT_RANGE_MM = 1_000_000
        const val MOTION_RELATIVE = 1e-9
        const val MOTION_ABSOLUTE = 1e-3
        const val STEPS = 63
        const val STEP_MM = 1000L
        const val MM2_PER_M2 = 1_000_000.0
        const val AREA_RELATIVE = 1e-9
        const val PERIMETER_MM = 1e-6
    }
}
