package app.plansnap.core.document

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import java.io.InputStream

/**
 * Version of the JSON format (ADR 0006).
 *
 * @property major major version, positive. Newer major versions are rejected.
 * @property minor minor version, not negative. Unknown fields of the same major version are ignored.
 */
data class FormatVersion(
    val major: Int,
    val minor: Int,
) : Comparable<FormatVersion> {
    init {
        require(major > 0) { "Major version must be positive: $major" }
        require(minor >= 0) { "Minor version must not be negative: $minor" }
    }

    override fun compareTo(other: FormatVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor })

    /** Constants. */
    companion object {
        /** Version written by this app. */
        val CURRENT: FormatVersion = FormatVersion(1, 0)
    }
}

/** Limits for untrusted documents (ARCHITECTURE section 10). */
object DocumentLimits {
    /** Largest accepted document in bytes, 20 MiB. */
    const val MAX_DOCUMENT_BYTES: Long = 20L * 1024L * 1024L
}

/**
 * Lossless codec of the versioned JSON format for database rows, backups and the JSON export (ADR 0006).
 *
 * Decoding treats its input as untrusted and never lets an exception of a model constructor escape:
 * [IllegalArgumentException] is translated to `DomainError.Document(INVALID_VALUES)`. Limits of `GeometryTolerances`,
 * dock targets and cycles are outside this module and are checked afterwards by `ProjectValidator`.
 *
 * Failures: `MALFORMED` for invalid content, `UNSUPPORTED_VERSION` for a newer major version, `TOO_LARGE` beyond
 * [DocumentLimits.MAX_DOCUMENT_BYTES] and `INVALID_VALUES` for values outside their ranges.
 */
interface DocumentCodec {
    /** Encodes a complete project, including all rooms, in [FormatVersion.CURRENT]. */
    fun encodeProject(project: Project): String

    /**
     * Decodes a complete project and migrates it stepwise if needed.
     *
     * Reads at most one byte beyond the limit and does not close [input]. An [java.io.IOException] from [input] is
     * returned as `DomainError.Storage(READ_FAILED)`.
     */
    fun decodeProject(input: InputStream): DomainResult<Project>

    /** Encodes one room as stored in the `rooms` table, in [FormatVersion.CURRENT]. */
    fun encodeRoom(room: Room): String

    /** Decodes one stored room and migrates it stepwise if needed. */
    fun decodeRoom(json: String): DomainResult<Room>
}

/** One migration step from major version [fromMajor] to `fromMajor + 1`. */
interface DocumentMigration {
    /** Source major version, positive. */
    val fromMajor: Int

    /** Migrates the JSON text, or returns a `DomainError.Document` failure. */
    fun migrate(json: String): DomainResult<String>
}
