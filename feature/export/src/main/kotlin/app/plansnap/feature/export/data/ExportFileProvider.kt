package app.plansnap.feature.export.data

import androidx.core.content.FileProvider

/**
 * [FileProvider] of the export. A subclass of its own, so that the manifest entry of this module cannot clash with a
 * provider of another library.
 */
class ExportFileProvider : FileProvider()
