package app.plansnap.feature.export.data

import android.net.Uri
import java.io.IOException
import java.io.OutputStream

/**
 * Destination of one export.
 *
 * @property stream stream to write into. The writer closes it.
 * @property uri address of the file for the share target or the chosen document.
 * @property discard removes the partial file after a failed or cancelled export. Never throws.
 */
class ExportTarget(
    val stream: OutputStream,
    val uri: Uri,
    val discard: () -> Unit,
)

/** File access of the export: a temporary file for sharing and a document chosen by the user for saving. */
interface ExportFiles {
    /**
     * Creates the cache file [fileName] for sharing. Older exports in the cache are removed first.
     *
     * @throws IOException if the file cannot be created.
     */
    fun createCacheTarget(fileName: String): ExportTarget

    /**
     * Opens the document [uri] chosen with the system file picker for writing, replacing its content.
     *
     * @throws IOException if the document cannot be opened.
     */
    fun openDocument(uri: Uri): ExportTarget
}
