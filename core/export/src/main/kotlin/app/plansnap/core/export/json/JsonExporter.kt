package app.plansnap.core.export.json

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.Exporter
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * Writes the whole project as the versioned JSON document of [DocumentCodec] (REQ-EXP-003, ADR 0006), so the file can
 * be imported again. All rooms are included whatever their status, and no plot is involved. The output is UTF-8 and
 * deterministic.
 *
 * @param codec the codec that also reads the document on import.
 * @param dispatchers dispatchers for encoding and writing.
 */
class JsonExporter(
    private val codec: DocumentCodec,
    private val dispatchers: DispatcherProvider,
) : Exporter {
    override val format: ExportFormat = ExportFormat.JSON

    override suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary> {
        require(request.format == ExportFormat.JSON) { "JsonExporter writes JSON, not ${request.format}" }
        return try {
            val bytes =
                withContext(dispatchers.default) { codec.encodeProject(request.project).toByteArray(Charsets.UTF_8) }
            withContext(dispatchers.io) {
                output.write(bytes)
                output.flush()
            }
            DomainResult.Success(ExportSummary(ExportFormat.JSON, null, persistentSetOf()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            failure()
        } catch (_: RuntimeException) {
            failure()
        }
    }

    private fun failure(): DomainResult<ExportSummary> =
        DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED))
}
