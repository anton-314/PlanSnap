package app.plansnap.feature.export.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Authority of [ExportFileProvider], declared in the manifest of this module. */
const val EXPORT_FILE_AUTHORITY: String = "app.plansnap.export.fileprovider"

/** Name of the cache subdirectory that [ExportFileProvider] serves (`export_file_paths.xml`). */
internal const val EXPORT_CACHE_DIRECTORY: String = "exports"

/** [ExportFiles] on top of the app cache, [FileProvider] and the content resolver. */
class AndroidExportFiles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ExportFiles {
        override fun createCacheTarget(fileName: String): ExportTarget {
            val directory = File(context.cacheDir, EXPORT_CACHE_DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, fileName)
            val uri =
                try {
                    FileProvider.getUriForFile(context, EXPORT_FILE_AUTHORITY, file)
                } catch (e: IllegalArgumentException) {
                    throw IOException("File is outside the shared directory", e)
                }
            return ExportTarget(file.outputStream(), uri) { file.delete() }
        }

        override fun openDocument(uri: Uri): ExportTarget {
            val stream =
                try {
                    context.contentResolver.openOutputStream(uri, "wt")
                } catch (e: SecurityException) {
                    throw IOException("No permission to write $uri", e)
                } ?: throw IOException("No stream for $uri")
            return ExportTarget(stream, uri) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (_: Exception) {
                    // Best effort: a provider may not support deletion, an empty document then remains.
                }
            }
        }
    }
