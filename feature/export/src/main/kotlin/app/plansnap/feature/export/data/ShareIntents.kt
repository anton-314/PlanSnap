package app.plansnap.feature.export.data

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import app.plansnap.core.export.ExportFormat

/**
 * What the share sheet needs for one exported file.
 *
 * @property uri content URI served by [ExportFileProvider].
 * @property format format of the file, which decides the MIME type.
 * @property fileName name of the file, shown by some share targets.
 */
data class ShareRequest(
    val uri: Uri,
    val format: ExportFormat,
    val fileName: String,
)

/** Builds the Android share intent (REQ-EXP-005). */
object ShareIntents {
    /**
     * `ACTION_SEND` of [request] wrapped in a chooser titled [title]. Read permission for the URI is granted to the
     * chosen target through the grant flag and the clip data, on the send intent and on the chooser.
     */
    fun chooser(
        request: ShareRequest,
        title: CharSequence,
    ): Intent {
        val clip = ClipData.newRawUri(request.fileName, request.uri)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = request.format.mimeType
                putExtra(Intent.EXTRA_STREAM, request.uri)
                putExtra(Intent.EXTRA_TITLE, request.fileName)
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, title).apply {
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
