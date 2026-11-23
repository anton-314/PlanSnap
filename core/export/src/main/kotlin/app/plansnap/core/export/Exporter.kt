package app.plansnap.core.export

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.io.OutputStream
import java.util.Locale

/** Output format (REQ-EXP-001, REQ-EXP-003, REQ-EXP-004). */
enum class ExportFormat {
    /** Scaled vector PDF. */
    PDF,

    /** Raster image. */
    PNG,

    /** Vector image. */
    SVG,

    /** Re-importable project file. Contains all rooms and uses no plot. */
    JSON,
}

/**
 * Input of an export.
 *
 * @property evaluations evaluation of every room of [project].
 * @property lengthUnit unit of all dimension labels (REQ-UX-004), ignored for [ExportFormat.JSON].
 * @property locale number format and language of the labels, ignored for [ExportFormat.JSON].
 * @property selection scale and paper override, ignored for [ExportFormat.JSON].
 * @property includeNotes whether room notes are drawn (REQ-MOD-012).
 * @throws IllegalArgumentException if a room has no evaluation.
 */
data class ExportRequest(
    val project: Project,
    val evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    val format: ExportFormat,
    val lengthUnit: LengthUnit,
    val locale: Locale,
    val selection: PlotSelection = PlotSelection(),
    val includeNotes: Boolean = false,
) {
    init {
        require(project.rooms.all { it.id in evaluations }) { "Every room needs an evaluation" }
    }
}

/**
 * Outcome of a finished export.
 *
 * @property plan chosen page, `null` exactly for [ExportFormat.JSON].
 * @property omittedRooms `OPEN` and `CONFLICT` rooms left out of the drawing, empty for [ExportFormat.JSON].
 */
data class ExportSummary(
    val format: ExportFormat,
    val plan: PlotPlan?,
    val omittedRooms: ImmutableSet<RoomId>,
) {
    init {
        val isJson = format == ExportFormat.JSON
        require(isJson == (plan == null)) { "A plan is absent exactly for JSON" }
        require(!isJson || omittedRooms.isEmpty()) { "JSON always contains all rooms" }
    }
}

/** Opening of a specific room. Opening ids are only unique within their room. */
data class RoomOpeningRef(
    val roomId: RoomId,
    val openingId: OpeningId,
)

/**
 * Findings shown before an export (REQ-EXP-006, DOMAIN section 12).
 *
 * @property exportableRooms rooms drawn in PDF, PNG and SVG.
 * @property omittedRooms `OPEN` and `CONFLICT` rooms, disjoint from [exportableRooms].
 * @property unconfirmedRooms exportable rooms carrying the mark "shape not confirmed".
 * @property outOfBoundsOpenings openings that no longer fit on their wall, with their room.
 */
data class ExportPreflightReport(
    val exportableRooms: ImmutableSet<RoomId>,
    val omittedRooms: ImmutableSet<RoomId>,
    val unconfirmedRooms: ImmutableSet<RoomId>,
    val outOfBoundsOpenings: ImmutableSet<RoomOpeningRef>,
) {
    init {
        require(exportableRooms.none { it in omittedRooms }) { "A room is either exportable or omitted" }
        require(exportableRooms.containsAll(unconfirmedRooms)) { "Unconfirmed rooms must be exportable" }
    }

    /** Whether PDF, PNG and SVG would fail with `NOTHING_TO_EXPORT`. */
    val isDrawingEmpty: Boolean get() = exportableRooms.isEmpty()
}

/** Computes the findings before an export. */
fun interface ExportPreflight {
    /** Checks [project] with the evaluation of every room. */
    fun check(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    ): ExportPreflightReport
}

/**
 * Writes one [format] (ARCHITECTURE section 2).
 *
 * Drawing formats omit `OPEN` and `CONFLICT` rooms and fail with `NOTHING_TO_EXPORT` if none remains. Technical
 * exceptions are translated to `DomainError.Export`, `CancellationException` is never caught.
 */
interface Exporter {
    /** Format this exporter writes. */
    val format: ExportFormat

    /** Writes [request] to [output], which stays open. A request of another format is a programming error. */
    suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary>
}
