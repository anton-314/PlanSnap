package app.plansnap.feature.export.ui

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Way the finished file leaves the app. */
enum class ExportAction {
    /** Hand the file to the Android share sheet. */
    SHARE,

    /** Write the file to a document the user chose. */
    SAVE,
}

/**
 * Room as named in the findings: its [name] and its position in the project, so that unnamed rooms stay
 * distinguishable.
 *
 * @property position one-based position of the room in the project.
 * @property name name of the room, blank if the user gave none.
 */
data class RoomRef(
    val position: Int,
    val name: String,
)

/**
 * Openings of one room that no longer fit on their wall.
 *
 * @property room the room.
 * @property count number of such openings, at least one.
 */
data class OpeningFinding(
    val room: RoomRef,
    val count: Int,
)

/**
 * Findings of the preflight before an export (REQ-EXP-006), prepared for display.
 *
 * @property omittedRooms rooms left out of PDF, PNG and SVG because they are open or contradictory.
 * @property unconfirmedRooms drawn rooms marked "shape not confirmed".
 * @property outOfBoundsOpenings openings per room that no longer fit on their wall.
 * @property isDrawingEmpty whether no room can be drawn, which blocks PDF, PNG and SVG.
 */
data class PreflightFindings(
    val omittedRooms: ImmutableList<RoomRef> = persistentListOf(),
    val unconfirmedRooms: ImmutableList<RoomRef> = persistentListOf(),
    val outOfBoundsOpenings: ImmutableList<OpeningFinding> = persistentListOf(),
    val isDrawingEmpty: Boolean = false,
) {
    /** Whether there is anything to warn about. */
    val hasFindings: Boolean
        get() = omittedRooms.isNotEmpty() || unconfirmedRooms.isNotEmpty() || outOfBoundsOpenings.isNotEmpty()
}

/** Page that the export would use with the current choices. */
sealed interface PlanFit {
    /** The format has no page, as for JSON. */
    data object NotApplicable : PlanFit

    /** The page is being computed. */
    data object Checking : PlanFit

    /**
     * The plan fits.
     *
     * @property scale chosen or automatic scale.
     * @property paper chosen or automatic paper.
     * @property orientation orientation, always chosen automatically.
     */
    data class Fits(
        val scale: PlotScale,
        val paper: PaperSize,
        val orientation: PageOrientation,
    ) : PlanFit

    /**
     * The plan cannot be exported with the current choices.
     *
     * @property error `NOTHING_TO_EXPORT`, `PLAN_TOO_LARGE` or `PLAN_DOES_NOT_FIT_SELECTION`.
     */
    data class Unavailable(
        val error: DomainError,
    ) : PlanFit
}

/** Message that stays visible after an export until the user dismisses it. */
sealed interface ExportNotice {
    /** The file was written to the chosen document. */
    data class Saved(
        val format: ExportFormat,
    ) : ExportNotice
}

/** State of the export dialog. */
sealed interface ExportUiState {
    /** The project is being read and evaluated. */
    data object Loading : ExportUiState

    /** The project could not be read or evaluated. */
    data class Failed(
        val error: DomainError,
    ) : ExportUiState

    /** The project has no room yet (docs/UX.md section 1). */
    data class Empty(
        val projectName: String,
    ) : ExportUiState

    /**
     * The options of the export.
     *
     * @property format chosen format.
     * @property scale chosen scale, `null` for automatic.
     * @property paper chosen paper, `null` for automatic.
     * @property unit unit of the dimension labels, from the settings.
     * @property findings preflight findings.
     * @property fit page with the current choices.
     * @property running action that is being carried out, `null` if idle.
     * @property awaitingDocument whether the document picker is open.
     * @property error failure of the last export, `null` if none.
     * @property notice confirmation of the last export, `null` if none.
     */
    data class Ready(
        val projectName: String,
        val format: ExportFormat,
        val scale: PlotScale?,
        val paper: PaperSize?,
        val unit: LengthUnit,
        val findings: PreflightFindings,
        val fit: PlanFit,
        val running: ExportAction? = null,
        val awaitingDocument: Boolean = false,
        val error: DomainError? = null,
        val notice: ExportNotice? = null,
    ) : ExportUiState {
        /** Whether a new export must not start. */
        val isBusy: Boolean get() = running != null || awaitingDocument

        /** Whether the options choose a page, so that the scale and paper sections apply. */
        val isDrawingFormat: Boolean get() = format != ExportFormat.JSON

        /** Whether sharing and saving are offered. */
        val canExport: Boolean
            get() = !isBusy && (format == ExportFormat.JSON || fit is PlanFit.Fits)
    }
}
