package app.plansnap.core.domain.command

import app.plansnap.core.common.units.Length
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId

/**
 * Editable room details of [ProjectEdit.UpdateRoomDetails].
 *
 * @property heightOverride room height, `null` uses the project default. Its range is checked when applied.
 */
data class RoomDetails(
    val name: String,
    val typeKey: RoomTypeKey?,
    val notes: String,
    val heightOverride: Length?,
    val areaFactor: AreaFactor,
)

/**
 * Immutable description of one command from DOMAIN section 11.
 *
 * All ids are assigned when the edit is created, so that redo yields identical objects. Domain rules are not checked
 * here: a violation is reported as a `DomainError` by the [ProjectCommand] created from the edit.
 */
sealed interface ProjectEdit {
    /** Appends [wall] with its start corner `V_n`. Only the first wall may use [CornerSpec.Unspecified]. */
    data class AddWall(
        val roomId: RoomId,
        val wall: Wall,
        val startCorner: CornerSpec,
    ) : ProjectEdit

    /** Changes the raw length of a wall. Openings that stop fitting stay stored and become `OUT_OF_BOUNDS`. */
    data class EditWall(
        val roomId: RoomId,
        val wallId: WallId,
        val length: Length,
    ) : ProjectEdit

    /**
     * Removes the last wall and its corner. A closed room is reopened and loses its overlay, which undo restores.
     *
     * Fails with `NO_WALL_TO_REMOVE` without walls, and with `CORNER_HAS_DIAGONALS`, `WALL_HAS_OPENINGS` or
     * `WALL_HAS_DOCKED_ROOMS` if anything depends on the removed wall or corner, so that nothing is deleted silently.
     */
    data class RemoveLastWall(
        val roomId: RoomId,
    ) : ProjectEdit

    /** Replaces the raw input at the corner [cornerIndex]. */
    data class SetCorner(
        val roomId: RoomId,
        val cornerIndex: Int,
        val corner: CornerSpec,
    ) : ProjectEdit

    /** Adds a measured diagonal. */
    data class AddDiagonal(
        val roomId: RoomId,
        val diagonal: Diagonal,
    ) : ProjectEdit

    /** Removes a diagonal. */
    data class RemoveDiagonal(
        val roomId: RoomId,
        val diagonalId: DiagonalId,
    ) : ProjectEdit

    /** Marks the outline as closed. Requires at least three walls. */
    data class CloseRoom(
        val roomId: RoomId,
    ) : ProjectEdit

    /** Stores [adjustment] as overlay. Fails with `ADJUSTMENT_NOT_AVAILABLE` if its fingerprint is not current. */
    data class AcceptAdjustment(
        val roomId: RoomId,
        val adjustment: ClosureAdjustment,
    ) : ProjectEdit

    /** Removes only the overlay. Raw values stay bit-identical. */
    data class RevertAdjustment(
        val roomId: RoomId,
    ) : ProjectEdit

    /** Changes the outward thickness of a wall. */
    data class SetThickness(
        val roomId: RoomId,
        val wallId: WallId,
        val thickness: Length,
    ) : ProjectEdit

    /** Adds an opening that must fit on its wall without overlapping another opening. */
    data class AddOpening(
        val roomId: RoomId,
        val opening: Opening,
    ) : ProjectEdit

    /**
     * Replaces the opening with the id of [opening].
     *
     * Fails with `OPENING_HAS_DOCKED_ROOMS` if the wall would change while a room is docked to this opening, so that no
     * dock link is released silently.
     */
    data class EditOpening(
        val roomId: RoomId,
        val opening: Opening,
    ) : ProjectEdit

    /**
     * Removes an opening. Rooms docked to it keep their world position: `hostOpeningId` becomes `null` and
     * `hostOffset` is converted to the distance from `V_i` (DOMAIN section 10).
     */
    data class RemoveOpening(
        val roomId: RoomId,
        val openingId: OpeningId,
    ) : ProjectEdit

    /** Appends [room]. */
    data class AddRoom(
        val room: Room,
    ) : ProjectEdit

    /** Removes a room. Rooms docked to it keep their world position and lose the link. */
    data class RemoveRoom(
        val roomId: RoomId,
    ) : ProjectEdit

    /**
     * Places a room freely and removes its dock link. Room dimensions stay unchanged.
     *
     * @throws IllegalArgumentException if a coordinate or the rotation is not finite.
     */
    data class MoveRoom(
        val roomId: RoomId,
        val originX: Double,
        val originY: Double,
        val rotationRad: Double,
    ) : ProjectEdit {
        init {
            require(originX.isFinite() && originY.isFinite()) { "Origin must be finite" }
            require(rotationRad.isFinite()) { "Rotation must be finite" }
        }
    }

    /**
     * Docks a room to a host wall or, with [DockLink.hostOpeningId], to an opening on that wall.
     *
     * A link to the room itself is a value here and fails with `DOCK_TO_SELF` when applied. Missing targets fail with
     * `NotFound`, a foreign opening with `DOCK_OPENING_NOT_ON_WALL` and a cycle with `DOCK_CYCLE`.
     */
    data class DockRoom(
        val roomId: RoomId,
        val link: DockLink,
    ) : ProjectEdit

    /** Replaces name, type, notes, height and area factor of a room. */
    data class UpdateRoomDetails(
        val roomId: RoomId,
        val details: RoomDetails,
    ) : ProjectEdit

    /** Changes the default room height of the project. */
    data class SetProjectHeight(
        val height: Length,
    ) : ProjectEdit
}
