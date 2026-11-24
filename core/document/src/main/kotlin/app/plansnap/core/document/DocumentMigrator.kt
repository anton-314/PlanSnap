package app.plansnap.core.document

import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val VERSION_KEY = "formatVersion"
private const val MAJOR_KEY = "major"
private const val MINOR_KEY = "minor"

/** Parses documents, checks their version and migrates them stepwise to [FormatVersion.CURRENT]. */
internal class DocumentMigrator(
    private val json: Json,
    migrations: List<DocumentMigration>,
) {
    private val migrationsByMajor: Map<Int, DocumentMigration> = migrations.associateBy { it.fromMajor }

    init {
        require(migrationsByMajor.size == migrations.size) { "Duplicate migration source version" }
    }

    /**
     * Returns the document in the current major version.
     *
     * @throws DocumentFailure `MALFORMED`, `UNSUPPORTED_VERSION` or a failure reported by a migration.
     */
    fun toCurrent(text: String): JsonObject {
        var currentText = text
        var current = parseObject(currentText)
        var major = majorOf(current)
        while (major < FormatVersion.CURRENT.major) {
            currentText = migrateStep(major, currentText)
            current = parseObject(currentText)
            val next = majorOf(current)
            if (next != major + 1) throw documentFailure(DocumentProblem.MALFORMED)
            major = next
        }
        if (major > FormatVersion.CURRENT.major) throw documentFailure(DocumentProblem.UNSUPPORTED_VERSION)
        return current
    }

    private fun migrateStep(
        major: Int,
        text: String,
    ): String {
        val migration = migrationsByMajor[major] ?: throw documentFailure(DocumentProblem.MALFORMED)
        return when (val result = migration.migrate(text)) {
            is DomainResult.Failure -> throw DocumentFailure(result.error)
            is DomainResult.Success -> result.value
        }
    }

    private fun parseObject(text: String): JsonObject {
        val element =
            try {
                json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                throw documentFailure(DocumentProblem.MALFORMED)
            }
        return element as? JsonObject ?: throw documentFailure(DocumentProblem.MALFORMED)
    }

    private fun majorOf(root: JsonObject): Int {
        val version = root[VERSION_KEY] as? JsonObject
        val major = intOf(version?.get(MAJOR_KEY))
        val minor = intOf(version?.get(MINOR_KEY))
        if (major == null || minor == null) throw documentFailure(DocumentProblem.MALFORMED)
        if (major <= 0 || minor < 0) throw documentFailure(DocumentProblem.MALFORMED)
        return major
    }

    private fun intOf(element: JsonElement?): Int? = (element as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
}
