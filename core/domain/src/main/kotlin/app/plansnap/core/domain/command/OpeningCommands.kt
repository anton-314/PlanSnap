package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.result.map
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

internal fun addOpening(
    project: Project,
    edit: ProjectEdit.AddOpening,
    rules: OpeningRules,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val opening = edit.opening
        val error =
            when {
                room.walls.none { it.id == opening.wallId } -> notFound(EntityKind.WALL, opening.wallId.value)
                room.openings.any { it.id == opening.id } -> rule(DomainRule.DUPLICATE_ID)
                else -> null
            }
        error.otherwise { Unit }.flatMap { rules.check(room, opening) }.map {
            room.copy(openings = room.openings.toPersistentList().adding(opening))
        }
    }

internal fun editOpening(
    project: Project,
    edit: ProjectEdit.EditOpening,
    rules: OpeningRules,
): DomainResult<Project> =
    project.mapRoom(edit.roomId) { room ->
        val opening = edit.opening
        val index = room.openings.indexOfFirst { it.id == opening.id }
        val error =
            when {
                index < 0 -> {
                    notFound(EntityKind.OPENING, opening.id.value)
                }

                room.walls.none { it.id == opening.wallId } -> {
                    notFound(EntityKind.WALL, opening.wallId.value)
                }

                room.openings[index].wallId != opening.wallId && project.hasDockedRooms(room.id, opening.id) -> {
                    rule(DomainRule.OPENING_HAS_DOCKED_ROOMS)
                }

                else -> {
                    null
                }
            }
        error.otherwise { Unit }.flatMap { rules.check(room, opening) }.map {
            room.copy(openings = room.openings.toPersistentList().replacingAt(index, opening))
        }
    }

internal fun removeOpening(
    project: Project,
    edit: ProjectEdit.RemoveOpening,
): DomainResult<Project> {
    val opening =
        project.rooms
            .firstOrNull { it.id == edit.roomId }
            ?.openings
            ?.firstOrNull { it.id == edit.openingId }
    return if (opening == null) {
        project.mapRoom(edit.roomId) { DomainResult.Failure(notFound(EntityKind.OPENING, edit.openingId.value)) }
    } else {
        project
            .mapRoom(edit.roomId) { room ->
                DomainResult.Success(
                    room.copy(openings = room.openings.filter { it.id != edit.openingId }.toImmutableList()),
                )
            }.flatMap { removed -> releaseDockedRooms(removed, edit.roomId, opening) }
    }
}

/** Keeps the world position of rooms docked to [opening]: the link then counts from `V_i` instead of the opening. */
private fun releaseDockedRooms(
    project: Project,
    hostRoomId: RoomId,
    opening: Opening,
): DomainResult<Project> {
    val released =
        project.rooms.map { room ->
            val dock = room.placement.dock
            if (dock != null && dock.hostRoomId == hostRoomId && dock.hostOpeningId == opening.id) {
                room.releasedFrom(opening)
            } else {
                room
            }
        }
    return if (released.any { it == null }) {
        DomainResult.Failure(DomainError.InvalidValue(ValueField.OPENING_OFFSET, InvalidValueReason.NOT_REPRESENTABLE))
    } else {
        DomainResult.Success(project.copy(rooms = released.filterNotNull().toImmutableList()))
    }
}

/** Returns the room without the opening link, or `null` if the converted offset is not representable. */
private fun Room.releasedFrom(opening: Opening): Room? {
    val dock = placement.dock ?: return this
    val offset = dock.hostOffset.plusOrNull(opening.offset)
    return offset?.let { copy(placement = placement.copy(dock = dock.copy(hostOffset = it, hostOpeningId = null))) }
}

private fun Length.plusOrNull(other: Length): Length? {
    val sum = mm + other.mm
    val overflow = ((mm xor sum) and (other.mm xor sum)) < 0L
    return if (overflow) null else Length(sum)
}

private fun Project.hasDockedRooms(
    hostRoomId: RoomId,
    openingId: OpeningId,
): Boolean =
    rooms.any { room ->
        room.placement.dock?.let { it.hostRoomId == hostRoomId && it.hostOpeningId == openingId } == true
    }
