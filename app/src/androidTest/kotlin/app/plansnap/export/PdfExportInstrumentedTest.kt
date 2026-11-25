package app.plansnap.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.plot.TitleBlockSpec
import app.plansnap.core.drawing.plot.titleBlockLayout
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.pdf.PdfExporter
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.core.render.LabelTextFormatter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Checks the PDF export on a device with [PdfRenderer] (ADR 0009, REQ-EXP-001): page size, and the scale of the
 * page measured on the rendered pixels. The scale bar and the title block frame are measured and compared with the
 * nominal millimeters; both must agree within 0,5 mm at 1:50 and 1:100 on A4 and A3. Robolectric cannot write a real
 * `PdfDocument`, so this is the only test that runs the PDF writer.
 */
class PdfExportInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        }
    private val exporter =
        PdfExporter(
            DefaultSceneBuilder(),
            DefaultPlotLayout(),
            { locale, unit -> LabelTextFormatter(context.resources, locale, unit) },
            dispatchers,
        )

    private fun room(
        id: String,
        vararg lengthsMm: Long,
        closed: Boolean = true,
    ): Room =
        Room(
            id = RoomId(id),
            walls =
                lengthsMm
                    .mapIndexed {
                        i,
                        mm,
                        ->
                        Wall(WallId("$id-w$i"), Length(mm), Length(WALL_MM))
                    }.toImmutableList(),
            corners =
                List(lengthsMm.size) {
                    if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT)
                }.toImmutableList(),
            isClosed = closed,
        )

    private fun request(
        selection: PlotSelection,
        vararg rooms: Room,
    ): ExportRequest {
        val project = Project(ProjectId("p"), "Calibration", Length(2500), rooms.toList().toImmutableList(), 1L, 2L)
        val evaluations =
            project.rooms
                .associate { room ->
                    val result = runBlocking { DefaultRoomEvaluator(dispatchers).evaluate(room, ClosureTolerance()) }
                    room.id to (result as DomainResult.Success).value
                }.toImmutableMap()
        return ExportRequest(project, evaluations, ExportFormat.PDF, LengthUnit.CENTIMETER, Locale.GERMAN, selection)
    }

    private fun export(request: ExportRequest): Pair<ByteArray, ExportSummary> {
        val bytes = ByteArrayOutputStream()
        val result = runBlocking { exporter.export(request, bytes) }
        return bytes.toByteArray() to (result as DomainResult.Success).value
    }

    /** Renders [pdf] at [PIXELS_PER_POINT] pixels per point, which equals 0,088 mm per pixel. */
    private fun render(pdf: ByteArray): Triple<Bitmap, Int, Int> {
        val file = File.createTempFile("calibration", ".pdf", context.cacheDir)
        try {
            file.writeBytes(pdf)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap =
                            Bitmap.createBitmap(
                                page.width * PIXELS_PER_POINT,
                                page.height * PIXELS_PER_POINT,
                                Bitmap.Config.ARGB_8888,
                            )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Triple(bitmap, page.width, page.height)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun isDark(pixel: Int): Boolean =
        (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < DARK_LEVEL

    /** Distance in mm between the outer edges of the first and last dark pixel of row [yMm] within [fromMm]..[toMm]. */
    private fun darkSpanMm(
        bitmap: Bitmap,
        pxPerMm: Double,
        yMm: Double,
        fromMm: Double,
        toMm: Double,
    ): Double {
        val y = (yMm * pxPerMm).roundToInt()
        val dark =
            ((fromMm * pxPerMm).roundToInt()..(toMm * pxPerMm).roundToInt()).filter {
                isDark(
                    bitmap.getPixel(it, y),
                )
            }
        assertTrue("no ink on row $yMm mm", dark.isNotEmpty())
        return (dark.last() - dark.first() + 1) / pxPerMm
    }

    private fun assertCalibrated(
        scale: PlotScale,
        paper: PaperSize,
    ) {
        val (pdf, summary) = export(request(PlotSelection(scale, paper), room("calibration", 4000, 3000, 4000, 3000)))
        val plan = summary.plan!!
        assertEquals(scale, plan.scale)
        assertEquals(paper, plan.paper)
        val (bitmap, widthPt, heightPt) = render(pdf)
        try {
            assertEquals((plan.pageWidthMm * POINTS_PER_MM).roundToInt(), widthPt)
            assertEquals((plan.pageHeightMm * POINTS_PER_MM).roundToInt(), heightPt)
            val pxPerMm = PIXELS_PER_POINT * POINTS_PER_MM
            val title = plan.titleBlockLayout
            val bar = title.scaleBar
            val stroke = title.strokeMm
            val barMiddle = bar.topMm + bar.heightMm / 2
            val barMm =
                darkSpanMm(bitmap, pxPerMm, barMiddle, bar.leftMm - 2.0, bar.leftMm + bar.lengthMm + 2.0) - stroke
            assertEquals(
                "scale bar at 1:${scale.denominator} on $paper",
                TitleBlockSpec.nominalLengthMm(scale).toDouble() / scale.denominator,
                barMm,
                TOLERANCE_MM,
            )
            val frame = title.frame
            val frameMm =
                darkSpanMm(bitmap, pxPerMm, frame.topMm + FRAME_ROW_MM, frame.leftMm - 2.0, frame.rightMm + 2.0) -
                    stroke
            assertEquals("frame width on $paper", frame.widthMm.toDouble(), frameMm, TOLERANCE_MM)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun scaleBarMeasuresNominalLengthAtOneToFiftyOnA4() = assertCalibrated(PlotScale.ONE_TO_50, PaperSize.A4)

    @Test
    fun scaleBarMeasuresNominalLengthAtOneToFiftyOnA3() = assertCalibrated(PlotScale.ONE_TO_50, PaperSize.A3)

    @Test
    fun scaleBarMeasuresNominalLengthAtOneToHundredOnA4() = assertCalibrated(PlotScale.ONE_TO_100, PaperSize.A4)

    @Test
    fun scaleBarMeasuresNominalLengthAtOneToHundredOnA3() = assertCalibrated(PlotScale.ONE_TO_100, PaperSize.A3)

    @Test
    fun omittedRoomsAreReportedAndNotDrawn() {
        val open = room("open", 3000, 2000, closed = false)
        val (pdf, summary) = export(request(PlotSelection(), room("ok", 4000, 3000, 4000, 3000), open))
        assertEquals(setOf(RoomId("open")), summary.omittedRooms)
        val (bitmap, _, _) = render(pdf)
        bitmap.recycle()
    }

    @Test
    fun successfulExportKeepsTheStreamOpen() {
        var closed = false
        val stream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        runBlocking { exporter.export(request(PlotSelection(), room("ok", 4000, 3000, 4000, 3000)), stream) }
        assertFalse(closed)
        assertTrue(stream.size() > PDF_HEADER_BYTES)
    }

    @Test
    fun nothingToExportWritesNothing() {
        val bytes = ByteArrayOutputStream()
        val result =
            runBlocking { exporter.export(request(PlotSelection(), room("open", 3000, 2000, closed = false)), bytes) }
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)), result)
        assertEquals(0, bytes.size())
    }

    @Test
    fun failingStreamGivesWriteFailedAndKeepsItOpen() {
        var closed = false
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw IOException("disk full")

                override fun close() {
                    closed = true
                }
            }
        val result =
            runBlocking { exporter.export(request(PlotSelection(), room("ok", 4000, 3000, 4000, 3000)), failing) }
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED)), result)
        assertFalse(closed)
    }

    private companion object {
        const val WALL_MM = 120L
        const val PIXELS_PER_POINT = 4
        const val POINTS_PER_MM = 72.0 / 25.4
        const val TOLERANCE_MM = 0.5
        const val DARK_LEVEL = 128
        const val FRAME_ROW_MM = 22.0
        const val PDF_HEADER_BYTES = 8
    }
}
