package app.plansnap.core.render

import android.graphics.Bitmap
import android.graphics.Color
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout of multi-line label blocks (CR-W2-05). Each line is drawn alone, keeping its place in the block, and the
 * painted rows of the lines must be disjoint and in block order.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w200dp-h200dp-mdpi")
class LabelBlockLayoutTest {
    private val harness = RenderHarness()
    private val lines =
        TestScenes.block(
            Vec2(0.0, 0.0),
            LabelText.Literal("Küche") to SceneStyle.LABEL,
            LabelText.RoomType(RoomTypeKey("kitchen")) to SceneStyle.LABEL,
            LabelText.Area(12_000_000.0) to SceneStyle.WARNING,
            LabelText.ShapeNotConfirmed to SceneStyle.WARNING,
        )

    /** Rows with a painted pixel, `null` if nothing is painted. */
    private fun paintedRows(bitmap: Bitmap): IntRange? {
        val rows = (0 until bitmap.height).filter { y -> isRowPainted(bitmap, y) }
        return if (rows.isEmpty()) null else rows.first()..rows.last()
    }

    /** Whether row [y] has a pixel that is neither transparent nor the white export page. */
    private fun isRowPainted(
        bitmap: Bitmap,
        y: Int,
    ): Boolean =
        (0 until bitmap.width).any { x ->
            val pixel = bitmap.getPixel(x, y)
            Color.alpha(pixel) != 0 && pixel != Color.WHITE
        }

    private fun assertDisjointInOrder(
        rows: List<IntRange?>,
        context: String,
    ) {
        val painted = rows.map { requireNotNull(it) { "$context: every line is drawn, got $rows" } }
        painted.zipWithNext().forEach { (upper, lower) ->
            assertTrue("$context: lines overlap, rows $painted", upper.last < lower.first)
        }
    }

    private fun exportRows(
        scale: PlotScale,
        line: SceneElement.Label,
    ): IntRange? {
        val bounds = WorldBounds(-3000.0, -3000.0, 3000.0, 3000.0)
        val plan = PlotPlan(PaperSize.A4, PageOrientation.PORTRAIT, scale, bounds)
        return paintedRows(harness.export(listOf(TestScenes.scene(ShapeStatus.CONFIRMED, listOf(line), bounds)), plan))
    }

    private fun editorRows(
        pixelsPerMm: Double,
        roomBounds: WorldBounds,
        line: SceneElement.Label,
    ): IntRange? =
        paintedRows(
            harness.editor(
                listOf(TestScenes.scene(ShapeStatus.CONFIRMED, listOf(line), roomBounds)),
                size = 300,
                pixelsPerMm = pixelsPerMm,
            ),
        )

    @Test
    fun `given a label block when exported at 1 to 50, 1 to 100 and 1 to 200 then its lines do not overlap`() {
        for (scale in listOf(PlotScale.ONE_TO_50, PlotScale.ONE_TO_100, PlotScale.ONE_TO_200)) {
            assertDisjointInOrder(lines.map { exportRows(scale, it) }, "1:${scale.denominator}")
        }
    }

    @Test
    fun `given a label block when drawn in the editor at any zoom where it is shown then its lines do not overlap`() {
        for (pixelsPerMm in listOf(0.005, 0.02, 0.1, 0.5, 2.0)) {
            // A room whose smaller side is 250 px on the screen, so the block is shown at every zoom.
            val half = 125.0 / pixelsPerMm
            val roomBounds = WorldBounds(-half, -half, half, half)
            assertDisjointInOrder(lines.map { editorRows(pixelsPerMm, roomBounds, it) }, "$pixelsPerMm px/mm")
        }
    }

    @Test
    fun `given a block taller than its room on the screen when drawn in the editor then only the middle line stays`() {
        val block =
            TestScenes.block(
                Vec2(0.0, 0.0),
                LabelText.Literal("A") to SceneStyle.LABEL,
                LabelText.Literal("B") to SceneStyle.LABEL,
                LabelText.Literal("C") to SceneStyle.LABEL,
            )
        // 0,1 px/mm and a 300 mm room: the smaller side is 30 px, the text 16 px, the outer lines 24 px off center.
        val roomBounds = WorldBounds(-150.0, -150.0, 150.0, 150.0)
        val shown = block.map { editorRows(0.1, roomBounds, it) != null }
        assertEquals(listOf(false, true, false), shown)
    }
}
