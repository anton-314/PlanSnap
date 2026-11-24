package app.plansnap.core.document

import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.InputStream

/**
 * [DocumentCodec] writing compact JSON with a fixed field order (deterministic output) and reading it strictly.
 *
 * Unknown fields of the same major version are ignored (ADR 0006). Older documents are migrated stepwise with
 * `migrations`, one step per major version.
 *
 * @param migrations available migration steps, at most one per source major version.
 */
class JsonDocumentCodec(
    migrations: List<DocumentMigration> = emptyList(),
) : DocumentCodec {
    private val migrator = DocumentMigrator(FORMAT, migrations)

    override fun encodeProject(project: Project): String =
        FORMAT.encodeToString(ProjectDocumentDto(CURRENT_DTO, project.toDto()))

    override fun encodeRoom(room: Room): String = FORMAT.encodeToString(RoomDocumentDto(CURRENT_DTO, room.toDto()))

    override fun decodeProject(input: InputStream): DomainResult<Project> =
        guarded {
            decodeBody(readLimitedText(input), PROJECT_KEY) { FORMAT.decodeFromJsonElement<ProjectDto>(it).toModel() }
        }

    override fun decodeRoom(json: String): DomainResult<Room> =
        guarded {
            if (json.length > DocumentLimits.MAX_DOCUMENT_BYTES) throw documentFailure(DocumentProblem.TOO_LARGE)
            decodeBody(json, ROOM_KEY) { FORMAT.decodeFromJsonElement<RoomDto>(it).toModel() }
        }

    private fun <T> decodeBody(
        text: String,
        key: String,
        build: (JsonElement) -> T,
    ): T {
        val body = migrator.toCurrent(text)[key] ?: throw documentFailure(DocumentProblem.MALFORMED)
        return translating { build(body) }
    }

    private inline fun <T> translating(block: () -> T): T =
        try {
            block()
        } catch (_: SerializationException) {
            throw documentFailure(DocumentProblem.MALFORMED)
        } catch (_: IllegalArgumentException) {
            throw documentFailure(DocumentProblem.INVALID_VALUES)
        }

    private companion object {
        const val PROJECT_KEY = "project"
        const val ROOM_KEY = "room"
        val CURRENT_DTO = VersionDto(FormatVersion.CURRENT.major, FormatVersion.CURRENT.minor)
        val FORMAT =
            Json {
                encodeDefaults = true
                explicitNulls = true
                ignoreUnknownKeys = true
                prettyPrint = false
            }
    }
}
