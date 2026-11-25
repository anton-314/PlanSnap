package app.plansnap.core.export.pdf

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.svg.ExportFixtures
import app.plansnap.core.model.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Error paths and contract checks of [PdfExporter]. Robolectric has no working `PdfDocument` (its native document
 * handle stays 0), so the written PDF is verified on a device by `PdfExportInstrumentedTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfExporterTest {
    private val exporter =
        PdfExporter(DefaultSceneBuilder(), DefaultPlotLayout(), ExportFixtures::formatter, ExportFixtures.unconfined)

    private fun request(
        vararg rooms: Room,
        selection: PlotSelection = PlotSelection(),
    ) = ExportFixtures.request(ExportFixtures.project(*rooms), ExportFormat.PDF, selection = selection)

    private fun run(
        request: ExportRequest,
        output: OutputStream = ByteArrayOutputStream(),
    ) = runBlocking { exporter.export(request, output) }

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
    fun `given another format when exporting then it is a programming error`() {
        val png = ExportFixtures.request(ExportFixtures.project(ExportFixtures.confirmed), ExportFormat.PNG)
        assertThrows(IllegalArgumentException::class.java) { run(png) }
    }

    @Test
    fun `given an already cancelled caller when exporting then the cancellation propagates and nothing is written`() {
        val bytes = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                coroutineContext.cancel()
                exporter.export(request(ExportFixtures.confirmed), bytes)
            }
        }
        assertEquals(0, bytes.size())
    }
}
