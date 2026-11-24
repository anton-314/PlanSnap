package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

internal fun addRoom(
    project: Project,
    edit: ProjectEdit.AddRoom,
): DomainResult<Project> {
    val room = edit.room
    return if (project.rooms.any { it.id == room.id }) {
        DomainResult.Failure(rule(DomainRule.DUPLICATE_ID))
    } else {
        val extended = project.copy(rooms = project.rooms.toPersistentList().adding(room))
        room.placement.dock
            ?.let { dockError(extended, room, it) }
            .otherwise { extended }
    }
}

internal fun removeRoom(
    project: Project,
    edit: ProjectEdit.RemoveRoom,
): DomainResult<Project> =
    if (project.rooms.none { it.id == edit.roomId }) {
        DomainResult.Failure(notFound(EntityKind.ROOM, edit.roomId.value))
    } else {
        DomainResult.Success(
            project.copy(
                rooms =
                    project.rooms
                        .filter { it.id != edit.roomId }
                        .map { it.withoutDockTo(edit.roomId) }
                        .toImmutableList(),
            ),
        )
    }

/** Keeps the world placement of a room whose host is removed and only drops the link. */
private fun Room.withoutDockTo(hostRoomId: RoomId): Room =
    if (placement.dock?.hostRoomId == hostRoomId) copy(placement = placement.copy(dock = null)) else this

internal fun moveRoom(
    project: Project,
    edit: ProjectEdit.MoveRoom,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        DomainResult.Success(
            room.copy(placement = RoomPlacement(edit.originX, edit.originY, edit.rotationRad, dock = null)),
        )
    }

internal fun dockRoom(
    project: Project,
    edit: ProjectEdit.DockRoom,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        dockError(project, room, edit.link).otherwise { room.copy(placement = room.placement.copy(dock = edit.link)) }
    }

/** Checks [link] of [room], which must be part of [project], in the order of DOMAIN section 10. */
private fun dockError(
    project: Project,
    room: Room,
    link: DockLink,
): DomainError? {
    val host = project.rooms.firstOrNull { it.id == link.hostRoomId }
    val openingId = link.hostOpeningId
    val hostOpening = host?.openings?.firstOrNull { it.id == openingId }
    return when {
        link.hostRoomId == room.id -> rule(DomainRule.DOCK_TO_SELF)
        host == null -> notFound(EntityKind.ROOM, link.hostRoomId.value)
        host.walls.none { it.id == link.hostWallId } -> notFound(EntityKind.WALL, link.hostWallId.value)
        room.walls.none { it.id == link.ownWallId } -> notFound(EntityKind.WALL, link.ownWallId.value)
        openingId != null && hostOpening == null -> notFound(EntityKind.OPENING, openingId.value)
        hostOpening != null && hostOpening.wallId != link.hostWallId -> rule(DomainRule.DOCK_OPENING_NOT_ON_WALL)
        project.hostChainReaches(link.hostRoomId, room.id) -> rule(DomainRule.DOCK_CYCLE)
        else -> null
    }
}

/** Whether following the dock links upwards from [start] reaches [target]. Bounded, so it ends on any input. */
private fun Project.hostChainReaches(
    start: RoomId,
    target: RoomId,
): Boolean {
    val hostOf = rooms.associate { it.id to it.placement.dock?.hostRoomId }
    return generateSequence(start) { hostOf[it] }.take(rooms.size + 1).any { it == target }
}

internal fun updateRoomDetails(
    project: Project,
    edit: ProjectEdit.UpdateRoomDetails,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val details = edit.details
        details.heightOverride?.let(::heightError).otherwise {
            room.copy(
                name = details.name,
                typeKey = details.typeKey,
                notes = details.notes,
                heightOverride = details.heightOverride,
                areaFactor = details.areaFactor,
            )
        }
    }

internal fun setProjectHeight(
    project: Project,
    edit: ProjectEdit.SetProjectHeight,
): DomainResult<Project> = heightError(edit.height).otherwise { project.copy(defaultHeight = edit.height) }
