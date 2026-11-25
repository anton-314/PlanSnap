package app.plansnap.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.LabelText
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w200dp-h200dp-mdpi")
class SceneRendererExportTest {
    private val harness = RenderHarness()
    private val layout = DefaultPlotLayout()
    private val bounds = WorldBounds(-2000.0, -1500.0, 2000.0, 1500.0)

    private fun plan(scale: PlotScale) = PlotPlan(PaperSize.A4, PageOrientation.PORTRAIT, scale, bounds)

    private fun page(
        scale: PlotScale,
        vararg elements: app.plansnap.core.drawing.scene.SceneElement,
        roomBounds: WorldBounds = bounds,
    ): Bitmap {
        val plan = plan(scale)
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, Bitmap.Config.ARGB_8888)
        val room = TestScenes.scene(ShapeStatus.CONFIRMED, elements.toList(), roomBounds)
        harness.renderer.drawExport(Canvas(bitmap), listOf(room), plan, layout)
        return bitmap
    }

    private fun textHeight(bitmap: Bitmap): Int {
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxY < 0) 0 else maxY - minY + 1
    }

    @Test
    fun `given a wall band when exported then it lands at the page point of the plot layout in the light palette`() {
        val bitmap = page(PlotScale.ONE_TO_50, TestScenes.rect(-500.0, -300.0, 500.0, 300.0, SceneStyle.WALL_BAND))
        val center = layout.worldToPagePoints(plan(PlotScale.ONE_TO_50), Vec2(0.0, 0.0))
        assertEquals(RenderPalette.Light.wall, bitmap.getPixel(center.x.toInt(), center.y.toInt()))
        val edge = layout.worldToPagePoints(plan(PlotScale.ONE_TO_50), Vec2(600.0, 0.0))
        assertEquals(0, bitmap.getPixel(edge.x.toInt(), edge.y.toInt()))
    }

    @Test
    fun `given a tiny room when exported then its label is still drawn`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Literal("Küche"), SceneStyle.LABEL)
        val bitmap = page(PlotScale.ONE_TO_50, label, roomBounds = WorldBounds(-10.0, -10.0, 10.0, 10.0))
        assertTrue(textHeight(bitmap) > 0)
    }

    @Test
    fun `given a dimension label when exported at 1 to 50 and 1 to 100 then the text is taller at 1 to 50`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Literal("8888"), SceneStyle.DIMENSION)
        val near = textHeight(page(PlotScale.ONE_TO_50, label))
        val far = textHeight(page(PlotScale.ONE_TO_100, label))
        assertTrue("1:50 $near pt, 1:100 $far pt", near > far)
    }

    @Test
    fun `given a dimension label when exported at 1 to 100 and 1 to 200 then both have the minimum height`() {
        val label = TestScenes.label(0.0, 0.0, LabelText.Literal("8888"), SceneStyle.DIMENSION)
        val hundred = textHeight(page(PlotScale.ONE_TO_100, label))
        val twoHundred = textHeight(page(PlotScale.ONE_TO_200, label))
        assertEquals(hundred, twoHundred)
    }

    private companion object {
        const val PAGE_WIDTH_PT = 595
        const val PAGE_HEIGHT_PT = 842
    }
}
