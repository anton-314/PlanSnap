package app.plansnap.feature.editor.canvas

import androidx.compose.ui.graphics.Color
import app.plansnap.core.designsystem.theme.PlanSnapColors
import app.plansnap.core.render.RenderPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasPaletteTest {
    @Test
    fun `given the light tokens when mapped then the palette equals the light render palette`() {
        val palette = PlanSnapColors.Light.toRenderPalette(PlanSnapColors.Light.material.surface)

        assertPalette(RenderPalette.Light, palette)
    }

    @Test
    fun `given the dark tokens when mapped then the palette equals the dark render palette`() {
        val palette = PlanSnapColors.Dark.toRenderPalette(PlanSnapColors.Dark.material.surface)

        assertPalette(RenderPalette.Dark, palette)
    }

    @Test
    fun `given a surface when mapped then it fills the openings`() {
        val palette = PlanSnapColors.Light.toRenderPalette(Color(0xFF123456))

        assertEquals(0xFF123456.toInt(), palette.openingFill)
    }

    private fun assertPalette(
        expected: RenderPalette,
        actual: RenderPalette,
    ) {
        assertEquals("wall", expected.wall, actual.wall)
        assertEquals("roomBody", expected.roomBody, actual.roomBody)
        assertEquals("openingFill", expected.openingFill, actual.openingFill)
        assertEquals("dimension", expected.dimension, actual.dimension)
        assertEquals("warning", expected.warning, actual.warning)
        assertEquals("conflict", expected.conflict, actual.conflict)
    }
}
