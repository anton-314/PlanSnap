package app.plansnap.core.render

import android.graphics.Bitmap
import android.graphics.Color
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.ShapeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel checks at known positions. With the default harness 1 mm is 0,1 px, the world origin is the pixel corner
 * (100, 100), and world y points up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w200dp-h200dp-mdpi")
class SceneRendererBitmapTest {
    private val harness = RenderHarness()
    private val light = RenderPalette.Light

    private fun scene(vararg elements: SceneElement) = TestScenes.scene(ShapeStatus.CONFIRMED, elements.toList())

    private fun painted(
        bitmap: Bitmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Int {
        var count = 0
        for (y in y0..y1) for (x in x0..x1) if (Color.alpha(bitmap.getPixel(x, y)) != 0) count++
        return count
    }

    private fun assertColor(
        expected: Int,
        actual: Int,
    ) {
        assertEquals("alpha", Color.alpha(expected), Color.alpha(actual))
        assertEquals("red", Color.red(expected), Color.red(actual))
        assertEquals("green", Color.green(expected), Color.green(actual))
        assertEquals("blue", Color.blue(expected), Color.blue(actual))
    }

    @Test
    fun `given a wall band when drawn then its inside has the wall color and the outside is untouched`() {
        val bitmap = harness.editor(listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.WALL_BAND))))
        assertColor(light.wall, bitmap.getPixel(100, 100))
        assertColor(light.wall, bitmap.getPixel(51, 71))
        assertEquals(0, bitmap.getPixel(40, 100))
        assertEquals(0, bitmap.getPixel(100, 60))
    }

    @Test
    fun `given the dark palette when drawn then the wall band has the dark wall color`() {
        val bitmap =
            harness.editor(
                listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.WALL_BAND))),
                appearance = EditorAppearance(RenderPalette.Dark, density = 1f, fontScale = 1f),
            )
        assertColor(RenderPalette.Dark.wall, bitmap.getPixel(100, 100))
    }

    @Test
    fun `given a room body when drawn then it is a light tint with a stronger outline`() {
        val bitmap = harness.editor(listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.ROOM_BODY))))
        val inside = bitmap.getPixel(100, 100)
        assertEquals(Color.alpha(light.roomBody), Color.alpha(inside))
        assertTrue("tint is not the wall color", Color.red(inside) < Color.red(light.wall) + 1 && inside != light.wall)
        assertTrue(Color.alpha(bitmap.getPixel(50, 100)) > Color.alpha(inside))
    }

    @Test
    fun `given a solid wall line when drawn then it is closed along its length`() {
        val line = TestScenes.polyline(SceneStyle.WALL_LINE, Vec2(-500.0, 0.0), Vec2(500.0, 0.0))
        val bitmap = harness.editor(listOf(scene(line)))
        for (x in 52..148) assertColor(light.wall, bitmap.getPixel(x, 99))
        assertEquals(0, bitmap.getPixel(80, 95))
    }

    @Test
    fun `given an unconfirmed wall line when drawn then it alternates 6 px dashes and 4 px gaps`() {
        val line = TestScenes.polyline(SceneStyle.WALL_UNCONFIRMED, Vec2(-500.0, 0.0), Vec2(500.0, 0.0))
        val bitmap = harness.editor(listOf(scene(line)))
        assertColor(light.wall, bitmap.getPixel(52, 99))
        assertColor(light.wall, bitmap.getPixel(54, 100))
        assertEquals(0, bitmap.getPixel(57, 99))
        assertEquals(0, bitmap.getPixel(58, 100))
        assertColor(light.wall, bitmap.getPixel(62, 99))
        assertEquals(0, bitmap.getPixel(67, 99))
    }

    @Test
    fun `given an unconfirmed room body when drawn then its outline is dashed while the confirmed one is solid`() {
        val solid = harness.editor(listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.ROOM_BODY))))
        val dashed =
            harness.editor(
                listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.ROOM_BODY_UNCONFIRMED))),
            )
        val solidEdge = (51..149).count { Color.alpha(solid.getPixel(it, 70)) > Color.alpha(light.roomBody) }
        val dashedEdge = (51..149).count { Color.alpha(dashed.getPixel(it, 70)) > Color.alpha(light.roomBody) }
        assertTrue("solid outline covers the edge ($solidEdge)", solidEdge > 90)
        assertTrue("dashed outline leaves gaps ($dashedEdge)", dashedEdge in 40..70)
    }

    @Test
    fun `given a corner marker of length zero when drawn then it is a round dot`() {
        val marker = TestScenes.polyline(SceneStyle.WALL_LINE, Vec2(0.0, 0.0), Vec2(0.0, 0.0))
        val bitmap =
            harness.editor(
                listOf(scene(marker)),
                appearance = EditorAppearance(light, density = 2f, fontScale = 1f),
            )
        assertColor(light.wall, bitmap.getPixel(100, 100))
        assertColor(light.wall, bitmap.getPixel(98, 98))
        assertTrue("a square cap would cover the corner fully", Color.alpha(bitmap.getPixel(102, 102)) < 0x40)
        assertEquals(0, bitmap.getPixel(106, 100))
    }

    @Test
    fun `given a door swing arc when drawn then it follows the quarter circle in the y-up world`() {
        val arc = SceneElement.Arc(Vec2(0.0, 0.0), 500.0, 0.0, Math.PI / 2.0, SceneStyle.OPENING, null)
        val bitmap = harness.editor(listOf(scene(arc)))
        assertTrue(painted(bitmap, 134, 64, 137, 66) > 0)
        assertTrue(painted(bitmap, 148, 98, 151, 101) > 0)
        assertTrue(painted(bitmap, 98, 48, 101, 51) > 0)
        assertEquals(0, painted(bitmap, 63, 134, 66, 137))
    }

    @Test
    fun `given every style when a polyline is drawn then each one leaves a mark`() {
        for (style in SceneStyle.entries) {
            val line = TestScenes.polyline(style, Vec2(-500.0, 0.0), Vec2(500.0, 0.0))
            val bitmap = harness.editor(listOf(scene(line)))
            assertTrue("$style polyline", painted(bitmap, 0, 90, 199, 110) > 0)
        }
    }

    @Test
    fun `given every style when a fill is drawn then each one leaves a mark`() {
        for (style in SceneStyle.entries) {
            val bitmap = harness.editor(listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, style))))
            assertTrue("$style fill", painted(bitmap, 0, 0, 199, 199) > 0)
        }
    }

    @Test
    fun `given an opening out of bounds when drawn then it differs from a regular opening in color and dash`() {
        val regular = harness.editor(listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.OPENING))))
        val outOfBounds =
            harness.editor(
                listOf(scene(TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.OPENING_OUT_OF_BOUNDS))),
            )
        assertColor(light.openingFill, regular.getPixel(100, 100))
        assertTrue(regular.getPixel(70, 70) != outOfBounds.getPixel(70, 70))
        val regularEdge = (51..149).count { Color.green(regular.getPixel(it, 70)) < 0xD0 }
        val dashedEdge = (51..149).count { Color.green(outOfBounds.getPixel(it, 70)) < 0xD0 }
        assertTrue("dashes leave gaps ($dashedEdge of $regularEdge)", dashedEdge < regularEdge - 20)
    }

    private fun labelBitmap(
        elements: List<SceneElement>,
        pixelsPerMm: Double = 0.1,
        bounds: WorldBounds = WorldBounds(-2000.0, -2000.0, 2000.0, 2000.0),
    ): Bitmap =
        harness.editor(listOf(TestScenes.scene(ShapeStatus.CONFIRMED, elements, bounds)), pixelsPerMm = pixelsPerMm)

    private fun labelBounds(
        elements: List<SceneElement>,
        pixelsPerMm: Double = 0.1,
        bounds: WorldBounds = WorldBounds(-2000.0, -2000.0, 2000.0, 2000.0),
    ): Pair<Int, Int> {
        val bitmap = labelBitmap(elements, pixelsPerMm, bounds)
        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxX < 0) 0 to 0 else (maxX - minX + 1) to (maxY - minY + 1)
    }

    @Test
    fun `given a label when the zoom changes then the text keeps its screen size`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Literal("Küche"), SceneStyle.LABEL)
        val near = labelBounds(listOf(label), pixelsPerMm = 0.1)
        val far = labelBounds(listOf(label), pixelsPerMm = 0.05)
        assertTrue("text is drawn", near.first > 0)
        assertEquals(near, far)
    }

    @Test
    fun `given a taller label when drawn then the text is larger in proportion to its nominal height`() {
        val nominal = labelBounds(listOf(TestScenes.label(0.0, 0.0, LabelText.Literal("Küche"), SceneStyle.LABEL)))
        val larger =
            labelBounds(
                listOf(TestScenes.label(0.0, 0.0, LabelText.Literal("Küche"), SceneStyle.LABEL, heightMm = 300.0)),
            )
        assertTrue(
            "${larger.first} vs ${nominal.first}",
            larger.first in (nominal.first * 19 / 10)..(nominal.first * 21 / 10),
        )
    }

    @Test
    fun `given a dimension label when its dimension line is shorter than the text then it is hidden`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Dimension(1000.0), SceneStyle.DIMENSION)
        val long =
            TestScenes.polyline(
                SceneStyle.DIMENSION,
                Vec2(-1000.0, 300.0),
                Vec2(1000.0, 300.0),
                target = label.target,
            )
        val short =
            TestScenes.polyline(
                SceneStyle.DIMENSION,
                Vec2(-100.0, 300.0),
                Vec2(100.0, 300.0),
                target = label.target,
            )
        assertTrue(painted(labelBitmap(listOf(long, label)), 60, 85, 140, 115) > 0)
        assertEquals(0, painted(labelBitmap(listOf(short, label)), 60, 85, 140, 115))
    }

    @Test
    fun `given a room label when the room is smaller than the text then it is hidden`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Literal("Küche"), SceneStyle.LABEL)
        val tiny = labelBounds(listOf(label), bounds = WorldBounds(-100.0, -100.0, 100.0, 100.0))
        val roomy = labelBounds(listOf(label), bounds = WorldBounds(-1000.0, -1000.0, 1000.0, 1000.0))
        assertEquals(0 to 0, tiny)
        assertTrue(roomy.first > 0)
    }
}
