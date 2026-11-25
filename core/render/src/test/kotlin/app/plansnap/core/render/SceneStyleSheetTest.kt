package app.plansnap.core.render

import app.plansnap.core.drawing.scene.SceneStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneStyleSheetTest {
    /** One line per style: fill, outlined, stroke, units, cap, dash, text. Pins the look of all outputs. */
    private val expected =
        mapOf(
            SceneStyle.WALL_BAND to "WALL false WALL 2.0 BUTT - WALL",
            SceneStyle.WALL_LINE to "NONE true WALL 2.0 ROUND - WALL",
            SceneStyle.WALL_UNCONFIRMED to "NONE true WALL 2.0 BUTT 6.0/4.0 WALL",
            SceneStyle.OPENING to "OPENING_FILL true WALL 1.0 BUTT - WALL",
            SceneStyle.OPENING_OUT_OF_BOUNDS to "OPENING_FILL true CONFLICT 1.0 BUTT 3.0/3.0 CONFLICT",
            SceneStyle.DIAGONAL to "NONE true DIMENSION 1.0 BUTT 2.0/3.0 DIMENSION",
            SceneStyle.DIMENSION to "NONE true DIMENSION 1.0 BUTT - DIMENSION",
            SceneStyle.LABEL to "NONE true WALL 1.0 BUTT - WALL",
            SceneStyle.WARNING to "NONE true WARNING 1.0 BUTT - WARNING",
            SceneStyle.CONFLICT to "NONE true CONFLICT 3.0 ROUND - CONFLICT",
            SceneStyle.ROOM_BODY to "ROOM_BODY true WALL 1.0 BUTT - WALL",
            SceneStyle.ROOM_BODY_UNCONFIRMED to "ROOM_BODY true WALL 1.5 BUTT 5.0/3.0 WALL",
        )

    private fun describe(spec: SceneStyleSpec): String =
        with(spec) {
            val dashText = dash?.let { "${it.onUnits}/${it.offUnits}" } ?: "-"
            "$fill $outlined $stroke $strokeUnits $cap $dashText $text"
        }

    @Test
    fun `given every scene style when looked up then the sheet defines exactly the pinned look`() {
        assertEquals(SceneStyle.entries.toSet(), expected.keys)
        SceneStyle.entries.forEach { style ->
            assertEquals(style.name, expected.getValue(style), describe(SceneStyleSheet.spec(style)))
        }
    }

    @Test
    fun `given a style when looked up twice then the same instance is returned`() {
        SceneStyle.entries.forEach { assertSame(SceneStyleSheet.spec(it), SceneStyleSheet.spec(it)) }
    }

    @Test
    fun `given status styles when compared then status is never shown by color alone`() {
        val sheet = SceneStyleSheet
        assertNull(sheet.spec(SceneStyle.WALL_LINE).dash)
        assertNotEquals(null, sheet.spec(SceneStyle.WALL_UNCONFIRMED).dash)
        assertNotEquals(null, sheet.spec(SceneStyle.ROOM_BODY_UNCONFIRMED).dash)
        assertNotEquals(null, sheet.spec(SceneStyle.OPENING_OUT_OF_BOUNDS).dash)
        assertTrue(sheet.spec(SceneStyle.CONFLICT).strokeUnits > sheet.spec(SceneStyle.WALL_LINE).strokeUnits)
    }

    @Test
    fun `given every ink when resolved then it maps to the palette role`() {
        val p = RenderPalette.Dark
        assertEquals(0, p.argb(PlanInk.NONE))
        assertEquals(p.wall, p.argb(PlanInk.WALL))
        assertEquals(p.roomBody, p.argb(PlanInk.ROOM_BODY))
        assertEquals(p.openingFill, p.argb(PlanInk.OPENING_FILL))
        assertEquals(p.dimension, p.argb(PlanInk.DIMENSION))
        assertEquals(p.warning, p.argb(PlanInk.WARNING))
        assertEquals(p.conflict, p.argb(PlanInk.CONFLICT))
    }

    @Test
    fun `given the export constants when read then they are the light palette on white paper`() {
        assertSame(RenderPalette.Light, SceneStyleSheet.EXPORT_PALETTE)
        assertEquals(0xFFFFFFFF.toInt(), SceneStyleSheet.PAGE_BACKGROUND_ARGB)
        assertEquals(PlanInk.WALL, SceneStyleSheet.TITLE_BLOCK_INK)
        assertEquals(0.18, SceneStyleSheet.PAPER_LINE_UNIT_MM, 0.0)
        assertEquals(1.0, SceneStyleSheet.EDITOR_LINE_UNIT_DP, 0.0)
        assertEquals(3.0, SceneStyleSheet.MARKER_UNITS, 0.0)
    }

    @Test
    fun `given styles when named for css then names are distinct kebab case`() {
        assertEquals("wall-band", SceneStyleSheet.className(SceneStyle.WALL_BAND))
        assertEquals("room-body-unconfirmed", SceneStyleSheet.className(SceneStyle.ROOM_BODY_UNCONFIRMED))
        assertEquals(
            SceneStyle.entries.size,
            SceneStyle.entries
                .map(SceneStyleSheet::className)
                .toSet()
                .size,
        )
    }

    @Test
    fun `given the canvas view when compared with the sheet then it shows the same look`() {
        val palette = RenderPalette.Light
        SceneStyle.entries.forEach { style ->
            val spec = SceneStyleSheet.spec(style)
            assertEquals(palette.argb(spec.fill), StyleLook.fillColor(style, palette))
            assertEquals(palette.argb(spec.stroke), StyleLook.strokeColor(style, palette))
            assertEquals(palette.argb(spec.text), StyleLook.textColor(style, palette))
            assertEquals(spec.outlined, StyleLook.outlined(style))
            assertEquals(spec.strokeUnits, StyleLook.strokeUnits(style).toDouble(), 0.0)
            assertEquals(spec.cap == LineCap.ROUND, StyleLook.roundCap(style))
            val dash = StyleLook.DASH_PATTERNS[StyleLook.dashIndex(style)]
            assertEquals(spec.dash?.onUnits, dash?.on?.toDouble())
            assertEquals(spec.dash?.offUnits, dash?.off?.toDouble())
        }
        assertNull(StyleLook.DASH_PATTERNS[0])
        assertEquals(3f, StyleLook.MARKER_UNITS)
    }

    @Test
    fun `given invalid values when building a spec then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DashPattern(0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { DashPattern(1.0, -1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStyleSpec(PlanInk.NONE, true, PlanInk.WALL, 0.0, LineCap.BUTT, null, PlanInk.WALL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStyleSpec(PlanInk.NONE, true, PlanInk.NONE, 1.0, LineCap.BUTT, null, PlanInk.WALL)
        }
        assertFalse(SceneStyleSheet.spec(SceneStyle.WALL_BAND).outlined)
    }
}
