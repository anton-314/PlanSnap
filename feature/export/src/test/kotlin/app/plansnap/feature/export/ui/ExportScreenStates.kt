package app.plansnap.feature.export.ui

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat
import kotlinx.collections.immutable.persistentListOf

/** States of the screen shared by the UI and screenshot tests. */
internal object ExportScreenStates {
    val fits = PlanFit.Fits(PlotScale.ONE_TO_50, PaperSize.A4, PageOrientation.LANDSCAPE)

    val standard =
        ExportUiState.Ready(
            projectName = "Flat",
            format = ExportFormat.PDF,
            scale = null,
            paper = null,
            unit = LengthUnit.CENTIMETER,
            findings = PreflightFindings(),
            fit = fits,
        )

    val warnings =
        standard.copy(
            findings =
                PreflightFindings(
                    omittedRooms = persistentListOf(RoomRef(3, "Shed"), RoomRef(4, "")),
                    unconfirmedRooms = persistentListOf(RoomRef(2, "Living room")),
                    outOfBoundsOpenings = persistentListOf(OpeningFinding(RoomRef(1, "Kitchen"), 2)),
                ),
        )

    val emptyDrawing =
        standard.copy(
            findings = PreflightFindings(isDrawingEmpty = true),
            fit = PlanFit.Unavailable(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)),
        )

    val doesNotFit =
        standard.copy(
            scale = PlotScale.ONE_TO_50,
            paper = PaperSize.A4,
            fit = PlanFit.Unavailable(DomainError.Export(ExportProblem.PLAN_DOES_NOT_FIT_SELECTION)),
        )

    val tooLarge = standard.copy(fit = PlanFit.Unavailable(DomainError.Export(ExportProblem.PLAN_TOO_LARGE)))
}
