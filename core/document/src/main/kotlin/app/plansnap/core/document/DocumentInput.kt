package app.plansnap.core.document

import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val CHUNK_BYTES = 8192

/** Expected failure while reading a document. Carries the [error] that is returned to the caller. */
internal class DocumentFailure(
    val error: DomainError,
) : RuntimeException(error.toString())

internal fun documentFailure(problem: DocumentProblem): DocumentFailure = DocumentFailure(DomainError.Document(problem))

/** Runs [block] and turns a [DocumentFailure] into a failed result. */
internal inline fun <T> guarded(block: () -> T): DomainResult<T> =
    try {
        DomainResult.Success(block())
    } catch (failure: DocumentFailure) {
        DomainResult.Failure(failure.error)
    }

/**
 * Reads at most [DocumentLimits.MAX_DOCUMENT_BYTES] plus one byte from [input], never closes it, and decodes strict
 * UTF-8.
 *
 * @throws DocumentFailure `TOO_LARGE`, `MALFORMED` for invalid UTF-8 or `Storage(READ_FAILED)`.
 */
internal fun readLimitedText(input: InputStream): String = decodeUtf8(readLimitedBytes(input))

private fun readLimitedBytes(input: InputStream): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(CHUNK_BYTES)
    var total = 0L
    try {
        while (true) {
            val wanted = minOf(chunk.size.toLong(), DocumentLimits.MAX_DOCUMENT_BYTES + 1L - total).toInt()
            val read = input.read(chunk, 0, wanted)
            if (read < 0) break
            total += read
            if (total > DocumentLimits.MAX_DOCUMENT_BYTES) throw documentFailure(DocumentProblem.TOO_LARGE)
            buffer.write(chunk, 0, read)
        }
    } catch (_: IOException) {
        throw DocumentFailure(DomainError.Storage(StorageProblem.READ_FAILED))
    }
    return buffer.toByteArray()
}

private fun decodeUtf8(bytes: ByteArray): String =
    try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw documentFailure(DocumentProblem.MALFORMED)
    }
