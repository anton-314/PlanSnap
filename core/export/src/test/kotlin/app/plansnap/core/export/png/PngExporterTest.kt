package app.plansnap.core.export.png

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.plot.titleBlockLayout
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.svg.ExportFixtures
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.render.SceneStyleSheet
import app.plansnap.core.render.argb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PngExporterTest {
    private val layout = DefaultPlotLayout()
    private val exporter = PngExporter(DefaultSceneBuilder(), layout, ExportFixtures::formatter, ExportFixtures.unconfined)
    private val ink = SceneStyleSheet.EXPORT_PALETTE.argb(SceneStyleSheet.TITLE_BLOCK_INK)

    private fun request(
        vararg rooms: Room,
        selection: PlotSelection = PlotSelection(),
    ) = ExportFixtures.request(ExportFixtures.project(*rooms), ExportFormat.PNG, selection = selection)

    private fun run(
        request: ExportRequest,
        output: OutputStream = ByteArrayOutputStream(),
    ) = runBlocking { exporter.export(request, output) }

    private fun png(request: ExportRequest): Triple<ByteArray, Bitmap, ExportSummary> {
        val bytes = ByteArrayOutputStream()
        val summary = (run(request, bytes) as DomainResult.Success).value
        val array = bytes.toByteArray()
        return Triple(array, BitmapFactory.decodeByteArray(array, 0, array.size), summary)
    }

    private fun pixel(
        bitmap: Bitmap,
        xMm: Double,
        yMm: Double,
    ): Int = bitmap.getPixel((xMm / MM_PER_INCH * DPI).roundToInt(), (yMm / MM_PER_INCH * DPI).roundToInt())

    @Test
    fun `given a project when exporting then the bitmap has the page size at 200 dpi`() {
        val (_, bitmap, summary) = png(request(ExportFixtures.confirmed))
        val plan = summary.plan!!
        assertEquals((plan.pageWidthMm / MM_PER_INCH * DPI).roundToInt(), bitmap.width)
        assertEquals((plan.pageHeightMm / MM_PER_INCH * DPI).roundToInt(), bitmap.height)
        assertEquals(ExportFormat.PNG, summary.format)
        assertTrue(bitmap.width.toLong() * bitmap.height <= PngExporter.MAX_PIXELS)
    }

    @Test
    fun `given an A3 page when exporting then it still fits the pixel limit`() {
        val (_, bitmap, summary) = png(request(ExportFixtures.wide, selection = PlotSelection(paper = PaperSize.A3)))
        assertEquals(PaperSize.A3, summary.plan!!.paper)
        assertTrue(bitmap.width.toLong() * bitmap.height <= PngExporter.MAX_PIXELS)
    }

    @Test
    fun `given a page when exporting then margin and background are opaque white`() {
        val (_, bitmap, _) = png(request(ExportFixtures.confirmed))
        assertEquals(SceneStyleSheet.PAGE_BACKGROUND_ARGB, pixel(bitmap, 2.0, 2.0))
        assertEquals(SceneStyleSheet.PAGE_BACKGROUND_ARGB, bitmap.getPixel(bitmap.width - 1, 0))
        assertEquals(SceneStyleSheet.PAGE_BACKGROUND_ARGB, bitmap.getPixel(0, bitmap.height - 1))
    }

    @Test
    fun `given a wall when exporting then the pixel at its band is wall ink`() {
        val request = request(ExportFixtures.confirmed)
        val (_, bitmap, summary) = png(request)
        val scene =
            DefaultSceneBuilder().buildRoom(
                ExportFixtures.confirmed,
                request.evaluations.getValue(ExportFixtures.confirmed.id),
                SceneOptions(true, true),
            )
        val bands = scene.elements.filterIsInstance<SceneElement.Fill>().filter { it.style == SceneStyle.WALL_BAND }
        assertTrue(bands.isNotEmpty())
        val inked =
            bands.count { band ->
                val vertices = band.polygon.vertices
                val center = Vec2(vertices.sumOf { it.x } / vertices.size, vertices.sumOf { it.y } / vertices.size)
                val point = layout.worldToPagePoints(summary.plan!!, center)
                bitmap.getPixel(
                    (point.x / POINTS_PER_INCH * DPI).roundToInt(),
                    (point.y / POINTS_PER_INCH * DPI).roundToInt(),
                ) == ink
            }
        assertTrue("wall bands with ink at their center: $inked of ${bands.size}", inked >= 1)
    }

    @Test
    fun `given the scale bar when exporting then its filled segment is ink and its light one is white`() {
        for (scale in PlotScale.entries) {
            val (_, bitmap, summary) = png(request(ExportFixtures.confirmed, selection = PlotSelection(scale = scale)))
            val bar = summary.plan!!.titleBlockLayout.scaleBar
            val middle = bar.topMm + bar.heightMm / 2
            val filled = bar.segments[0]
            val light = bar.segments[1]
            assertEquals(ink, pixel(bitmap, filled.leftMm + filled.widthMm / 2, middle))
            assertEquals(
                SceneStyleSheet.PAGE_BACKGROUND_ARGB,
                pixel(bitmap, light.leftMm + light.widthMm / 2, middle),
            )
        }
    }

    /** Whether a pixel within two pixels of the position is dark: a 0,25 mm line is antialiased. */
    private fun darkNear(
        bitmap: Bitmap,
        xMm: Double,
        yMm: Double,
    ): Boolean {
        val cx = (xMm / MM_PER_INCH * DPI).roundToInt()
        val cy = (yMm / MM_PER_INCH * DPI).roundToInt()
        return (-2..2).any { dx -> (-2..2).any { dy -> (bitmap.getPixel(cx + dx, cy + dy) shr 16 and 0xFF) < DARK } }
    }

    @Test
    fun `given the title block when exporting then its frame is drawn`() {
        val (_, bitmap, summary) = png(request(ExportFixtures.confirmed))
        val frame = summary.plan!!.titleBlockLayout.frame
        assertTrue(darkNear(bitmap, frame.leftMm.toDouble(), frame.topMm + frame.heightMm / 2.0))
        assertTrue(darkNear(bitmap, frame.leftMm + frame.widthMm / 2.0, frame.bottomMm.toDouble()))
        assertFalse(darkNear(bitmap, frame.leftMm + frame.widthMm / 2.0, frame.bottomMm + 2.0))
    }

    @Test
    fun `given an unconfirmed room when exporting then the image differs from the confirmed rendering`() {
        val confirmed = png(request(ExportFixtures.confirmed)).first
        val unconfirmed = png(request(ExportFixtures.unconfirmed)).first
        assertFalse(confirmed.contentEquals(unconfirmed))
    }

    @Test
    fun `given open and conflict rooms when exporting then they are omitted and reported`() {
        val (_, _, summary) = png(request(ExportFixtures.confirmed, ExportFixtures.open, ExportFixtures.conflict))
        assertEquals(setOf(RoomId("open"), RoomId("conflict")), summary.omittedRooms)
    }

    @Test
    fun `given only open and conflict rooms when exporting then nothing is written`() {
        val bytes = ByteArrayOutputStream()
        val result = run(request(ExportFixtures.open, ExportFixtures.conflict), bytes)
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)), result)
        assertEquals(0, bytes.size())
    }

    @Test
    fun `given a plan that fits no page when exporting then the layout error is passed on`() {
        assertEquals(
            DomainResult.Failure(DomainError.Export(ExportProblem.PLAN_TOO_LARGE)),
            run(request(ExportFixtures.huge)),
        )
    }

    @Test
    fun `given a selection that does not fit when exporting then the selection error is passed on`() {
        val selection = PlotSelection(PlotScale.ONE_TO_50, PaperSize.A4)
        assertEquals(
            DomainResult.Failure(DomainError.Export(ExportProblem.PLAN_DOES_NOT_FIT_SELECTION)),
            run(request(ExportFixtures.wide, selection = selection)),
        )
    }

    @Test
    fun `given a resolution that exceeds the pixel limit when exporting then the result is an encoding error`() {
        val huge = PngExporter(DefaultSceneBuilder(), layout, ExportFixtures::formatter, ExportFixtures.unconfined, 600)
        val bytes = ByteArrayOutputStream()
        val result = runBlocking { huge.export(request(ExportFixtures.confirmed), bytes) }
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.ENCODING_FAILED)), result)
        assertEquals(0, bytes.size())
    }

    @Test
    fun `given a failing stream when exporting then the result is a write error`() {
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw IOException("disk full")
            }
        assertEquals(
            DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED)),
            run(request(ExportFixtures.confirmed), failing),
        )
    }

    @Test
    fun `given a cancellation while writing when exporting then it propagates`() {
        val cancelling =
            object : OutputStream() {
                override fun write(b: Int) = throw CancellationException("cancelled")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw CancellationException("cancelled")
            }
        assertThrows(CancellationException::class.java) { run(request(ExportFixtures.confirmed), cancelling) }
    }

    @Test
    fun `given a stream when exporting then it stays open`() {
        var closed = false
        val stream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        run(request(ExportFixtures.confirmed), stream)
        assertFalse(closed)
        assertTrue(stream.size() > 0)
    }

    @Test
    fun `given another format when exporting then it is a programming error`() {
        val svg = ExportFixtures.request(ExportFixtures.project(ExportFixtures.confirmed), ExportFormat.SVG)
        assertThrows(IllegalArgumentException::class.java) { run(svg) }
    }

    @Test
    fun `given a non positive resolution when creating then it is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) {
            PngExporter(DefaultSceneBuilder(), layout, ExportFixtures::formatter, ExportFixtures.unconfined, 0)
        }
    }

    @Test
    fun `given the same input when exporting twice then the bytes are equal`() {
        val first = png(request(ExportFixtures.confirmed, ExportFixtures.unconfirmed)).first
        val second = png(request(ExportFixtures.confirmed, ExportFixtures.unconfirmed)).first
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `given a long project name when exporting then it is shortened and does not reach the scale text`() {
        val project = ExportFixtures.project(ExportFixtures.confirmed).copy(name = "W".repeat(200))
        val (_, bitmap, summary) = png(ExportFixtures.request(project, ExportFormat.PNG))
        val title = summary.plan!!.titleBlockLayout
        val gap = pixel(bitmap, title.projectName.xMm + title.projectNameMaxWidthMm + 1.0, title.projectName.baselineMm - 1.0)
        assertEquals(SceneStyleSheet.PAGE_BACKGROUND_ARGB, gap)
    }

    private companion object {
        const val DPI = 200.0
        const val DARK = 0x60
        const val MM_PER_INCH = 25.4
        const val POINTS_PER_INCH = 72.0
    }
}
