package app.plansnap.core.export.pdf

import android.graphics.pdf.PdfDocument
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.Exporter
import app.plansnap.core.render.LabelTextFormatter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes the plan as a scaled one-page PDF (REQ-EXP-001, ADR 0009) with Android `PdfDocument` and the shared canvas
 * renderer. The page has the paper size of the [PlotPlan] in points (1 pt = 1/72 in, rounded to whole points because
 * `PdfDocument.PageInfo` takes integers, at most 0,5 pt = 0,18 mm); content is positioned in unrounded points from
 * [PlotLayout], so the scale is exact (`pt = mm / M · 72 / 25.4`).
 *
 * `OPEN` and `CONFLICT` rooms are left out like in the SVG, the title block comes from `PlotPlan.titleBlockLayout`.
 * The document is built completely before the first byte is written, so a failure leaves [OutputStream] untouched.
 *
 * @param sceneBuilder source of the room scenes.
 * @param plotLayout scale and page choice and the conversion from world to page.
 * @param labelFormatter creates the label formatter for the locale and unit of a request.
 * @param dispatchers dispatchers for the scene work and for writing.
 */
class PdfExporter(
    sceneBuilder: SceneBuilder,
    plotLayout: PlotLayout,
    labelFormatter: (Locale, LengthUnit) -> LabelTextFormatter,
    dispatchers: DispatcherProvider,
) : Exporter {
    private val flow = PageExportFlow(sceneBuilder, plotLayout, labelFormatter, dispatchers)

    override val format: ExportFormat = ExportFormat.PDF

    override suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary> {
        require(request.format == ExportFormat.PDF) { "PdfExporter writes PDF, not ${request.format}" }
        return flow.run(
            request,
            ExportFormat.PDF,
            output,
        ) { job, checkActive -> DomainResult.Success(encode(job, checkActive)) }
    }

    private fun encode(
        job: PageJob,
        checkActive: () -> Unit,
    ): ByteArray {
        val document = PdfDocument()
        try {
            val info = pageInfo(job.plan)
            val page = document.startPage(info)
            try {
                job.painter.paint(page.canvas, job.plan, job.rooms, job.projectName, checkActive)
            } finally {
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun pageInfo(plan: PlotPlan): PdfDocument.PageInfo =
        PdfDocument.PageInfo
            .Builder(
                (plan.pageWidthMm * POINTS_PER_MM).roundToInt(),
                (plan.pageHeightMm * POINTS_PER_MM).roundToInt(),
                1,
            ).create()
}
