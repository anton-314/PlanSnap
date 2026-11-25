package app.plansnap.core.export.png

import android.graphics.Bitmap
import android.graphics.Canvas
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.Exporter
import app.plansnap.core.export.pdf.PageExportFlow
import app.plansnap.core.export.pdf.PageJob
import app.plansnap.core.render.LabelTextFormatter
import app.plansnap.core.render.SceneStyleSheet
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes the plan as a PNG (REQ-EXP-003) of the chosen page at a fixed resolution of [dpi] (DOMAIN section 12: 200).
 *
 * 200 dpi gives 0,127 mm per pixel, which resolves the thinnest plan line (0,18 mm) and 3 mm labels at print quality.
 * A3 landscape then has 3307 by 2339 pixels, about 31 MB as `ARGB_8888` bitmap, which fits the heap of every device
 * from API 26. A page above [MAX_PIXELS] or one the heap cannot hold ends with `ENCODING_FAILED` instead of an
 * out-of-memory crash. The background is opaque `PAGE_BACKGROUND_ARGB`; the content is the shared canvas renderer on
 * a canvas scaled from points to pixels, so geometry equals PDF and SVG.
 *
 * `OPEN` and `CONFLICT` rooms are left out like in the SVG. The PNG is encoded completely before the first byte is
 * written, and the bitmap is recycled on every path.
 *
 * @param sceneBuilder source of the room scenes.
 * @param plotLayout scale and page choice and the conversion from world to page.
 * @param labelFormatter creates the label formatter for the locale and unit of a request.
 * @param dispatchers dispatchers for the scene work and for writing.
 * @param dpi resolution in pixels per inch, positive.
 */
class PngExporter(
    sceneBuilder: SceneBuilder,
    plotLayout: PlotLayout,
    labelFormatter: (Locale, LengthUnit) -> LabelTextFormatter,
    dispatchers: DispatcherProvider,
    private val dpi: Int = DEFAULT_DPI,
) : Exporter {
    private val flow = PageExportFlow(sceneBuilder, plotLayout, labelFormatter, dispatchers)

    init {
        require(dpi > 0) { "dpi must be positive" }
    }

    override val format: ExportFormat = ExportFormat.PNG

    override suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary> {
        require(request.format == ExportFormat.PNG) { "PngExporter writes PNG, not ${request.format}" }
        return flow.run(request, ExportFormat.PNG, output, ::encode)
    }

    private fun encode(
        job: PageJob,
        checkActive: () -> Unit,
    ): DomainResult<ByteArray> {
        val width = pixels(job.plan.pageWidthMm)
        val height = pixels(job.plan.pageHeightMm)
        if (width.toLong() * height > MAX_PIXELS) return encodingFailed()
        val bitmap =
            try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                return encodingFailed()
            }
        try {
            bitmap.eraseColor(SceneStyleSheet.PAGE_BACKGROUND_ARGB)
            val canvas = Canvas(bitmap)
            val factor = dpi / POINTS_PER_INCH
            canvas.scale(factor, factor)
            job.painter.paint(canvas, job.plan, job.rooms, job.projectName, checkActive)
            val bytes = ByteArrayOutputStream()
            return if (bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)) {
                DomainResult.Success(bytes.toByteArray())
            } else {
                encodingFailed()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun pixels(mm: Int): Int = (mm / MM_PER_INCH * dpi).roundToInt()

    private fun encodingFailed(): DomainResult<ByteArray> =
        DomainResult.Failure(DomainError.Export(ExportProblem.ENCODING_FAILED))

    /** Defaults of [PngExporter]. */
    companion object {
        /** Resolution of the PNG in pixels per inch. */
        const val DEFAULT_DPI: Int = 200

        /** Largest accepted bitmap in pixels, A3 at 200 dpi has 7,7 million. */
        const val MAX_PIXELS: Long = 8_000_000L

        private const val POINTS_PER_INCH = 72f
        private const val MM_PER_INCH = 25.4
        private const val PNG_QUALITY = 100
    }
}
