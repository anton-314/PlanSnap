package app.plansnap.core.export.pdf

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.model.RoomId
import app.plansnap.core.render.LabelTextFormatter
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/** The page to draw: the chosen [plan], the scenes of the exportable rooms and the omitted rooms. */
internal class PageJob(
    val plan: PlotPlan,
    val rooms: List<RoomScene>,
    val omitted: ImmutableSet<RoomId>,
    val projectName: String,
    val painter: PagePainter,
)

/**
 * Flow shared by the PDF and the PNG exporter, equal to the SVG exporter: omit `OPEN` and `CONFLICT` rooms, build the
 * scenes, choose the page with [PlotLayout], let [encode] draw and encode it, and only then write the bytes. A failure
 * before the write leaves [OutputStream] untouched.
 */
internal class PageExportFlow(
    private val sceneBuilder: SceneBuilder,
    private val plotLayout: PlotLayout,
    private val labelFormatter: (Locale, LengthUnit) -> LabelTextFormatter,
    private val dispatchers: DispatcherProvider,
) {
    /** Runs the export of [request] in [format]. [encode] returns the file bytes or a failure. */
    suspend fun run(
        request: ExportRequest,
        format: ExportFormat,
        output: OutputStream,
        encode: (PageJob, () -> Unit) -> DomainResult<ByteArray>,
    ): DomainResult<ExportSummary> =
        try {
            when (val prepared = withContext(dispatchers.default) { prepare(request) }) {
                is DomainResult.Failure -> {
                    prepared
                }

                is DomainResult.Success -> {
                    val job = prepared.value
                    when (val bytes = withContext(dispatchers.default) { encodeActive(job, encode) }) {
                        is DomainResult.Failure -> {
                            bytes
                        }

                        is DomainResult.Success -> {
                            withContext(dispatchers.io) {
                                output.write(bytes.value)
                                output.flush()
                            }
                            DomainResult.Success(ExportSummary(format, job.plan, job.omitted))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            writeFailed()
        } catch (_: RuntimeException) {
            writeFailed()
        }

    private suspend fun prepare(request: ExportRequest): DomainResult<PageJob> {
        val context = currentCoroutineContext()
        val omitted =
            request.project.rooms.filter {
                !request.evaluations
                    .getValue(it.id)
                    .status.isExportable
            }
        val options = SceneOptions(exportableOnly = true, includeNotes = request.includeNotes)
        val scenes = ArrayList<RoomScene>()
        for (room in request.project.rooms) {
            context.ensureActive()
            val evaluation = request.evaluations.getValue(room.id)
            if (evaluation.status.isExportable) scenes += sceneBuilder.buildRoom(room, evaluation, options)
        }
        val bounds = scenes.mapNotNull { it.bounds }.reduceOrNull(WorldBounds::union)
        if (bounds == null) return DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT))
        return when (val layout = plotLayout.layout(bounds, request.selection)) {
            is DomainResult.Failure -> {
                layout
            }

            is DomainResult.Success -> {
                val painter = PagePainter(plotLayout, labelFormatter(request.locale, request.lengthUnit))
                DomainResult.Success(
                    PageJob(
                        layout.value,
                        scenes,
                        omitted.map { it.id }.toImmutableSet(),
                        request.project.name,
                        painter,
                    ),
                )
            }
        }
    }

    private suspend fun encodeActive(
        job: PageJob,
        encode: (PageJob, () -> Unit) -> DomainResult<ByteArray>,
    ): DomainResult<ByteArray> {
        val context = currentCoroutineContext()
        return encode(job) { context.ensureActive() }
    }

    private fun writeFailed(): DomainResult<ExportSummary> =
        DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED))
}
