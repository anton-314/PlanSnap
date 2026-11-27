package app.plansnap.feature.editor.canvas

import androidx.compose.runtime.Immutable
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableSet

/**
 * A long press that hit a room: the room that is about to be moved and the snap parameters at the current zoom. The
 * canvas converts 16 dp into millimeters, because only it knows density and zoom. Everything else (snapping, the
 * preview, the command) is decided outside the canvas.
 *
 * @property roomId room under the finger.
 * @property snapDistanceMm snap distance in world millimeters (16 dp, UX section 4).
 * @property gridStepMm step of the grid that is drawn at this zoom, see [PlanGrid.stepMm].
 */
data class RoomGestureStart(
    val roomId: RoomId,
    val snapDistanceMm: Double,
    val gridStepMm: Double,
)

/**
 * Progress of a long-press gesture, relative to its start. The canvas never applies it to the model.
 *
 * @property translationMm movement of the fingers' center in world millimeters (x to the right, y up).
 * @property rotationRad counter-clockwise twist of two fingers in world terms, 0 with one finger.
 */
data class RoomGestureUpdate(
    val translationMm: Vec2,
    val rotationRad: Double,
)

/**
 * Callbacks of the long-press gesture on a room (UX section 4: long press and drag moves the room, a second finger
 * twists it). A gesture is one [onStart], any number of [onUpdate] and exactly one of [onEnd] (all fingers lifted) or
 * [onCancel] (the gesture was interrupted). Gestures without a long press never reach these callbacks.
 *
 * The default [None] disables the gesture: the canvas then does not even listen for it, so a long press behaves
 * like any other touch.
 */
@Immutable
class RoomGestureCallbacks(
    val onStart: (RoomGestureStart) -> Unit = {},
    val onUpdate: (RoomGestureUpdate) -> Unit = {},
    val onEnd: () -> Unit = {},
    val onCancel: () -> Unit = {},
) {
    /** Whether the canvas listens for the gesture, `false` for [None]. */
    val isActive: Boolean get() = this !== None

    /** The disabled gesture. */
    companion object {
        /** Callbacks that disable the gesture. */
        val None: RoomGestureCallbacks = RoomGestureCallbacks()
    }
}

/**
 * The rigid transform a canvas applies to some rooms while they are dragged, without touching the scene or the model:
 * `p' = R(rotation) · (p - pivot) + pivot + translation` in world coordinates.
 *
 * @property roomIds rooms that move: the dragged room and the rooms docked to it.
 * @property translationMm shift in world millimeters.
 * @property rotationRad counter-clockwise rotation around [pivotMm].
 * @property pivotMm center of the rotation in world millimeters.
 */
@Immutable
data class RoomTransformPreview(
    val roomIds: ImmutableSet<RoomId>,
    val translationMm: Vec2,
    val rotationRad: Double,
    val pivotMm: Vec2,
)
