package app.plansnap.feature.export.ui

import androidx.annotation.StringRes
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat
import app.plansnap.feature.export.R

/** Explanation of [error] for the user, with the next step where there is one. */
@StringRes
internal fun errorMessage(error: DomainError): Int =
    when (error) {
        is DomainError.Export -> {
            when (error.problem) {
                ExportProblem.NOTHING_TO_EXPORT -> R.string.export_error_nothing_to_export
                ExportProblem.PLAN_TOO_LARGE -> R.string.export_error_plan_too_large
                ExportProblem.PLAN_DOES_NOT_FIT_SELECTION -> R.string.export_error_plan_does_not_fit
                ExportProblem.WRITE_FAILED -> R.string.export_error_write_failed
                ExportProblem.ENCODING_FAILED -> R.string.export_error_encoding_failed
                ExportProblem.NO_SPACE -> R.string.export_error_no_space
            }
        }

        is DomainError.Storage, is DomainError.NotFound, is DomainError.Document -> {
            R.string.export_error_load_failed
        }

        else -> {
            R.string.export_error_generic
        }
    }

@StringRes
internal fun formatLabel(format: ExportFormat): Int =
    when (format) {
        ExportFormat.PDF -> R.string.export_format_pdf
        ExportFormat.PNG -> R.string.export_format_png
        ExportFormat.SVG -> R.string.export_format_svg
        ExportFormat.JSON -> R.string.export_format_json
    }

@StringRes
internal fun formatDescription(format: ExportFormat): Int =
    when (format) {
        ExportFormat.PDF -> R.string.export_format_pdf_description
        ExportFormat.PNG -> R.string.export_format_png_description
        ExportFormat.SVG -> R.string.export_format_svg_description
        ExportFormat.JSON -> R.string.export_format_json_description
    }

@StringRes
internal fun scaleLabel(scale: PlotScale): Int =
    when (scale) {
        PlotScale.ONE_TO_50 -> R.string.export_scale_1_50
        PlotScale.ONE_TO_100 -> R.string.export_scale_1_100
        PlotScale.ONE_TO_200 -> R.string.export_scale_1_200
    }

@StringRes
internal fun paperLabel(paper: PaperSize): Int =
    when (paper) {
        PaperSize.A4 -> R.string.export_paper_a4
        PaperSize.A3 -> R.string.export_paper_a3
    }

@StringRes
internal fun orientationLabel(orientation: PageOrientation): Int =
    when (orientation) {
        PageOrientation.PORTRAIT -> R.string.export_orientation_portrait
        PageOrientation.LANDSCAPE -> R.string.export_orientation_landscape
    }
