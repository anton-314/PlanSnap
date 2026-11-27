package app.plansnap.feature.editor.rooms

import androidx.compose.runtime.Immutable
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.domain.snap.SnapKind
import app.plansnap.core.domain.snap.SnapTargets
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorSectionState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The dock sheet while it is open: what the user chose so far (REQ-MOD-006).
 *
 * @property guestId the room that is docked.
 * @property hostRoomId chosen host room.
 * @property hostWallId chosen host wall.
 * @property hostOpeningId chosen opening on [hostWallId], `null` to dock to the wall itself.
 * @property ownWallId wall of the guest that is placed against the host.
 * @property offsetText magnitude of the offset as typed, with the keypad rules. The start is "0" (flush).
 * @property offsetNegative whether the offset runs against the measuring direction of the host wall.
 * @property offsetInvalid whether [offsetText] is not a length.
 * @property error why the chosen link is not possible, `null` if it is, or if it is incomplete.
 * @property picking whether the sheet is hidden so the host can be tapped on the plan.
 */
@Immutable
data class DockDraft(
    val guestId: RoomId,
    val hostRoomId: RoomId? = null,
    val hostWallId: WallId? = null,
    val hostOpeningId: OpeningId? = null,
    val ownWallId: WallId? = null,
    val offsetText: String = DEFAULT_OFFSET_TEXT,
    val offsetNegative: Boolean = false,
    val offsetInvalid: Boolean = false,
    val error: DomainError? = null,
    val picking: Boolean = false,
) {
    /** Whether the host wall and the own wall are chosen. */
    val isComplete: Boolean get() = hostRoomId != null && hostWallId != null && ownWallId != null

    /** Whether the dock can be confirmed: complete, a valid offset and no error. */
    val canConfirm: Boolean get() = isComplete && !offsetInvalid && error == null

    /** Constants. */
    companion object {
        /** The offset the sheet starts with: flush with the start of the wall or the opening (DOMAIN section 10). */
        const val DEFAULT_OFFSET_TEXT: String = "0"
    }
}

/**
 * The room that is being moved or rotated by a long press. It holds the data of the preview only: the model changes
 * once, when the gesture ends.
 *
 * @property roomId the dragged room.
 * @property movingIds the dragged room and the docked rooms that follow it when it is placed.
 * @property startPlacement placement of the room when the gesture began.
 * @property pivot center of the rotation in world millimeters.
 * @property corners world corners of the room at the start, used to snap them.
 * @property targets snap candidates: corners and walls of the rooms that stay.
 * @property snapDistanceMm snap distance in world millimeters.
 * @property gridStepMm step of the grid.
 * @property translation shift of the preview in world millimeters, snapping included.
 * @property rotationRad rotation of the preview, exactly as the fingers twisted.
 * @property snap what the preview snapped to, `null` if it did not.
 */
@Immutable
@Suppress("LongParameterList") // One value per fact of the gesture, a data holder.
class RoomDrag(
    val roomId: RoomId,
    val movingIds: ImmutableSet<RoomId>,
    val startPlacement: RoomPlacement,
    val pivot: Vec2,
    val corners: ImmutableList<Vec2>,
    val targets: SnapTargets,
    val snapDistanceMm: Double,
    val gridStepMm: Double,
    val translation: Vec2 = Vec2(0.0, 0.0),
    val rotationRad: Double = 0.0,
    val snap: SnapKind? = null,
) {
    /** Returns this drag with a new preview result. */
    fun withResult(
        translation: Vec2,
        rotationRad: Double,
        snap: SnapKind?,
    ): RoomDrag =
        RoomDrag(
            roomId,
            movingIds,
            startPlacement,
            pivot,
            corners,
            targets,
            snapDistanceMm,
            gridStepMm,
            translation,
            rotationRad,
            snap,
        )
}

/**
 * State of the rooms function, stored in `EditorUiState.sections`.
 *
 * @property dock the open dock sheet, `null` if none is open.
 * @property drag the gesture in progress, `null` if there is none.
 */
@Immutable
data class RoomsState(
    val dock: DockDraft? = null,
    val drag: RoomDrag? = null,
) : EditorSectionState

/** Replaces the [RoomsState] of the editor by the result of [transform], starting from an empty one if needed. */
internal fun EditorHost.updateRooms(transform: (RoomsState) -> RoomsState) {
    update { it.withSection(RoomsState::class, transform(it.section(RoomsState::class) ?: RoomsState())) }
}
