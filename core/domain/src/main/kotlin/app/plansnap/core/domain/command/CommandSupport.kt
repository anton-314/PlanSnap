package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.result.map
import app.plansnap.core.common.units.Length
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toPersistentList

/** Violation of the domain [rule]. */
internal fun rule(rule: DomainRule): DomainError = DomainError.RuleViolation(rule)

/** Failure for a missing entity. */
internal fun notFound(
    entity: EntityKind,
    id: String,
): DomainError = DomainError.NotFound(entity, id)

/** Success with the value of [block] if this is `null`, otherwise the failure with this error. */
internal inline fun <T> DomainError?.otherwise(block: () -> T): DomainResult<T> =
    if (this == null) DomainResult.Success(block()) else DomainResult.Failure(this)

/** Applies [transform] to the room [roomId], or fails with `NotFound`. The other rooms keep their order. */
internal inline fun Project.mapRoom(
    roomId: RoomId,
    transform: (Room) -> DomainResult<Room>,
): DomainResult<Project> {
    val index = rooms.indexOfFirst { it.id == roomId }
    return if (index < 0) {
        DomainResult.Failure(notFound(EntityKind.ROOM, roomId.value))
    } else {
        transform(rooms[index]).map { copy(rooms = rooms.toPersistentList().replacingAt(index, it)) }
    }
}

/** Error for a wall length outside `MIN_WALL_MM..MAX_WALL_MM`, or `null`. */
internal fun wallLengthError(length: Length): DomainError? =
    when {
        length.mm < GeometryTolerances.MIN_WALL_MM -> {
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)
        }

        length.mm > GeometryTolerances.MAX_WALL_MM -> {
            DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)
        }

        else -> {
            null
        }
    }

/** Error for a wall thickness outside `0..MAX_THICKNESS_MM`, or `null`. */
internal fun thicknessError(thickness: Length): DomainError? =
    when {
        thickness.mm < 0L -> {
            DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_SMALL)
        }

        thickness.mm > GeometryTolerances.MAX_THICKNESS_MM -> {
            DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE)
        }

        else -> {
            null
        }
    }

/** Error for a room or project height that is not positive, or `null`. */
internal fun heightError(height: Length): DomainError? =
    if (height > Length.ZERO) null else DomainError.InvalidValue(ValueField.ROOM_HEIGHT, InvalidValueReason.TOO_SMALL)

/** Whether a dock link of the room itself or of another room refers to [wallId] of [room]. */
internal fun Project.hasDockOnWall(
    room: Room,
    wallId: WallId,
): Boolean =
    room.placement.dock?.ownWallId == wallId ||
        rooms.any { other ->
            other.placement.dock?.let { it.hostRoomId == room.id && it.hostWallId == wallId } == true
        }
