package app.plansnap.core.data.repository

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.Length
import app.plansnap.core.data.db.ProjectEntity
import app.plansnap.core.data.db.RoomEntity
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.document.FormatVersion
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToLong

internal fun Project.toEntity(): ProjectEntity =
    ProjectEntity(
        id = id.value,
        name = name,
        defaultHeightMm = defaultHeight.mm.toDouble(),
        formatMajor = FormatVersion.CURRENT.major,
        formatMinor = FormatVersion.CURRENT.minor,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

internal fun Room.toEntity(
    projectId: ProjectId,
    position: Int,
    codec: DocumentCodec,
): RoomEntity =
    RoomEntity(id = id.value, projectId = projectId.value, position = position, documentJson = codec.encodeRoom(this))

internal fun Project.roomEntities(codec: DocumentCodec): List<RoomEntity> =
    rooms.mapIndexed { position, room -> room.toEntity(id, position, codec) }

/**
 * Rebuilds a project from stored rows. A row that cannot be decoded or violates a model invariant is a damaged
 * store and fails with `Storage(READ_FAILED)`.
 */
internal fun assembleProject(
    entity: ProjectEntity,
    roomRows: List<RoomEntity>,
    codec: DocumentCodec,
): DomainResult<Project> {
    val rooms = ArrayList<Room>(roomRows.size)
    for (row in roomRows) {
        val decoded = codec.decodeRoom(row.documentJson)
        if (decoded !is DomainResult.Success) {
            return DomainResult.Failure(
                DomainError.Storage(StorageProblem.READ_FAILED),
            )
        }
        rooms += decoded.value
    }
    return DomainResult.Success(
        Project(
            id = ProjectId(entity.id),
            name = entity.name,
            defaultHeight = Length(entity.defaultHeightMm.roundToLong()),
            rooms = rooms.toImmutableList(),
            createdAtEpochMs = entity.createdAtEpochMs,
            updatedAtEpochMs = entity.updatedAtEpochMs,
        ),
    )
}
