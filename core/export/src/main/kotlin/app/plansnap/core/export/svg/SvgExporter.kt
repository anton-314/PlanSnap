package app.plansnap.core.export.svg

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
import app.plansnap.core.export.Exporter
import app.plansnap.core.geometry.primitives.Vec2
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

/**
 * Writes the plan as a scaled SVG (REQ-EXP-004): the page of the [PlotPlan] in millimeters, every element of every
 * exportable room, the title block with scale bar and scale, and the text marks of unconfirmed rooms.
 *
 * `OPEN` and `CONFLICT` rooms are left out and listed in the attribute `data-omitted-rooms` of the root and in
 * [ExportSummary.omittedRooms]. Label text follows the rules of the renderer: block lines sit at
 * `blockAnchor + k · (anchor − blockAnchor)` with the export text factor `k` (ADR 0008, CR-W2-05). The output is UTF-8,
 * locale-independent in its numbers and deterministic.
 *
 * @param sceneBuilder source of the room scenes.
 * @param plotLayout scale and page choice and the conversion from world to page.
 * @param labelFormatter creates the label formatter for the locale and unit of a request, for example from the
 * resources of that locale.
 * @param dispatchers dispatchers for the scene work and for writing.
 */
class SvgExporter(
    private val sceneBuilder: SceneBuilder,
    private val plotLayout: PlotLayout,
    private val labelFormatter: (Locale, LengthUnit) -> LabelTextFormatter,
    private val dispatchers: DispatcherProvider,
) : Exporter {
    override val format: ExportFormat = ExportFormat.SVG

    override suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary> {
        require(request.format == ExportFormat.SVG) { "SvgExporter writes SVG, not ${request.format}" }
        return try {
            val prepared = withContext(dispatchers.default) { prepare(request) }
            when (prepared) {
                is DomainResult.Failure -> {
                    prepared
                }

                is DomainResult.Success -> {
                    val bytes = withContext(dispatchers.default) { compose(request, prepared.value) }
                    withContext(dispatchers.io) {
                        output.write(bytes)
                        output.flush()
                    }
                    DomainResult.Success(ExportSummary(ExportFormat.SVG, prepared.value.plan, prepared.value.omitted))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            failure()
        } catch (_: RuntimeException) {
            failure()
        }
    }

    private suspend fun prepare(request: ExportRequest): DomainResult<Prepared> {
        val omitted =
            request.project.rooms.filter {
                !request.evaluations
                    .getValue(it.id)
                    .status.isExportable
            }
        val options = SceneOptions(exportableOnly = true, includeNotes = request.includeNotes)
        val scenes = ArrayList<RoomScene>()
        for (room in request.project.rooms) {
            currentCoroutineContext().ensureActive()
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
                DomainResult.Success(Prepared(layout.value, scenes, omitted.map { it.id }.toImmutableSet()))
            }
        }
    }

    private suspend fun compose(
        request: ExportRequest,
        prepared: Prepared,
    ): ByteArray {
        val context = currentCoroutineContext()
        val formatter = labelFormatter(request.locale, request.lengthUnit)
        val document = SvgDocument(mapping(prepared.plan), formatter::format) { context.ensureActive() }
        val content =
            SvgContent(
                prepared.plan,
                request.project.name,
                prepared.rooms,
                request.project.rooms
                    .map { it.id }
                    .filter { it in prepared.omitted },
            )
        return document.render(content).toByteArray(Charsets.UTF_8)
    }

    private fun mapping(plan: PlotPlan): PageMapping {
        val bounds: WorldBounds = plan.worldBounds
        val center = Vec2(bounds.minX + bounds.width / 2.0, bounds.minY + bounds.height / 2.0)
        val origin = plotLayout.worldToPagePoints(plan, center)
        val step = plotLayout.worldToPagePoints(plan, Vec2(center.x + 1.0, center.y + 1.0))
        return PageMapping(
            centerX = center.x,
            centerY = center.y,
            originX = origin.x / POINTS_PER_MM,
            originY = origin.y / POINTS_PER_MM,
            mmPerWorldX = (step.x - origin.x) / POINTS_PER_MM,
            mmPerWorldY = (step.y - origin.y) / POINTS_PER_MM,
        )
    }

    private fun failure(): DomainResult<ExportSummary> =
        DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED))

    private class Prepared(
        val plan: PlotPlan,
        val rooms: List<RoomScene>,
        val omitted: ImmutableSet<RoomId>,
    )

    private companion object {
        const val POINTS_PER_MM = 72.0 / 25.4
    }
}
