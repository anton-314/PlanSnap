package app.plansnap.feature.editor.canvas

import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGridTest {
    @Test
    fun `given any zoom when the step is asked then it is a 1-2-5 value at least 24 dp wide`() {
        for (exponent in -8..8) {
            val pixelsPerMm = Math.pow(10.0, exponent / 2.0).coerceIn(1e-4, 100.0)
            val step = PlanGrid.stepMm(pixelsPerMm, 2.0)
            assertTrue(
                "step $step at $pixelsPerMm",
                step * pixelsPerMm >= 24.0 * 2.0 - 1e-9 || step == PlanGrid.MIN_STEP_MM,
            )
            val mantissa = step / Math.pow(10.0, Math.floor(Math.log10(step)))
            assertTrue("mantissa $mantissa", listOf(1.0, 2.0, 5.0).any { Math.abs(it - mantissa) < 1e-9 })
        }
    }

    @Test
    fun `given 0,1 px per mm on mdpi when the step is asked then it is 500 mm`() {
        assertEquals(500.0, PlanGrid.stepMm(0.1, 1.0), 1e-9)
    }

    @Test
    fun `given a very deep zoom when the step is asked then it never falls below one millimeter`() {
        assertEquals(PlanGrid.MIN_STEP_MM, PlanGrid.stepMm(100.0, 1.0), 0.0)
    }

    @Test
    fun `given a viewport when the lines are prepared then the first lines lie on the screen and on the grid`() {
        val viewport = Viewport(Vec2(1250.0, 730.0), 0.1, 360, 640)
        val lines = PlanGrid.lines(viewport, 500.0)

        assertEquals(50.0f, lines.spacing, 1e-3f)
        assertTrue(lines.firstX in 0f..lines.spacing)
        assertTrue(lines.firstY in 0f..lines.spacing)
        val worldX = (lines.firstX - 180.0) / 0.1 + 1250.0
        assertEquals(0.0, worldX % 500.0, 1e-3)
    }
}
