package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

/** New ids for the children of every room, keyed by the old room id because child ids are only unique per room. */
private class IdMaps(
    project: Project,
    ids: IdGenerator,
) {
    val rooms: Map<RoomId, RoomId> = project.rooms.associate { it.id to RoomId(ids.newId()) }
    val walls: Map<RoomId, Map<WallId, WallId>> =
        project.rooms.associate { room -> room.id to room.walls.associate { it.id to WallId(ids.newId()) } }
    val diagonals: Map<RoomId, Map<DiagonalId, DiagonalId>> =
        project.rooms.associate { room -> room.id to room.diagonals.associate { it.id to DiagonalId(ids.newId()) } }
    val openings: Map<RoomId, Map<OpeningId, OpeningId>> =
        project.rooms.associate { room -> room.id to room.openings.associate { it.id to OpeningId(ids.newId()) } }

    fun wall(
        room: RoomId,
        wall: WallId,
    ): WallId = walls[room]?.get(wall) ?: wall

    fun opening(
        room: RoomId,
        opening: OpeningId,
    ): OpeningId = openings[room]?.get(opening) ?: opening
}

/**
 * Deep copy with new ids for the project, its rooms and all of their walls, diagonals and openings. All internal
 * references (opening to wall, dock link to host room, wall and opening) are redirected to the new ids, references
 * to rooms outside the project are kept. Raw values, adjustments and placements are copied unchanged.
 */
internal fun Project.copyWithNewIds(
    ids: IdGenerator,
    nowEpochMs: Long,
): Project {
    val newProjectId = ProjectId(ids.newId())
    val maps = IdMaps(this, ids)
    return copy(
        id = newProjectId,
        rooms = rooms.map { it.remapped(maps) }.toImmutableList(),
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}

private fun Room.remapped(maps: IdMaps): Room {
    val oldId = id
    return copy(
        id = maps.rooms.getValue(oldId),
        walls = walls.map { it.copy(id = maps.wall(oldId, it.id)) }.toImmutableList(),
        diagonals =
            diagonals
                .map { it.copy(id = maps.diagonals.getValue(oldId).getValue(it.id)) }
                .toImmutableList(),
        openings =
            openings
                .map { it.copy(id = maps.opening(oldId, it.id), wallId = maps.wall(oldId, it.wallId)) }
                .toImmutableList(),
        placement = placement.copy(dock = placement.dock?.remapped(oldId, maps)),
    )
}

private fun DockLink.remapped(
    ownRoom: RoomId,
    maps: IdMaps,
): DockLink =
    DockLink(
        hostRoomId = maps.rooms[hostRoomId] ?: hostRoomId,
        hostWallId = maps.wall(hostRoomId, hostWallId),
        ownWallId = maps.wall(ownRoom, ownWallId),
        hostOffset = hostOffset,
        hostOpeningId = hostOpeningId?.let { maps.opening(hostRoomId, it) },
    )
