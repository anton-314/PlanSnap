package app.plansnap.feature.export.data

import app.plansnap.core.export.ExportFormat

/** MIME type of the file written for this format, used for sharing and for the document picker. */
val ExportFormat.mimeType: String
    get() =
        when (this) {
            ExportFormat.PDF -> "application/pdf"
            ExportFormat.PNG -> "image/png"
            ExportFormat.SVG -> "image/svg+xml"
            ExportFormat.JSON -> "application/json"
        }

/** File name extension of this format without the dot. */
val ExportFormat.fileExtension: String
    get() =
        when (this) {
            ExportFormat.PDF -> "pdf"
            ExportFormat.PNG -> "png"
            ExportFormat.SVG -> "svg"
            ExportFormat.JSON -> "json"
        }

/** Builds file names for exports. */
object ExportFileNames {
    private const val FALLBACK = "plansnap"
    private const val MAX_BASE_LENGTH = 80
    private val spaces = Regex(" +")

    /**
     * File name for [projectName] and [format]: letters, digits, `-` and `_` are kept, every other run of characters
     * becomes one `_`. A blank name falls back to `plansnap`.
     */
    fun forProject(
        projectName: String,
        format: ExportFormat,
    ): String {
        val base =
            projectName
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else ' ' }
                .joinToString("")
                .trim()
                .replace(spaces, "_")
                .take(MAX_BASE_LENGTH)
                .ifBlank { FALLBACK }
        return "$base.${format.fileExtension}"
    }
}
