package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.atan2

/**
 * Detects the long-press gesture on a room: a long press of a single finger on a room starts it ([begin] decides
 * whether a room is there), that finger then moves the room and a second finger twists it. While it runs, every
 * pointer change is consumed, so pan, pinch and tap detectors of the same canvas do not react. A touch that is not
 * such a long press is left untouched.
 *
 * Progress is reported only once the fingers leave the rest position, see [RoomGestureTracker.exceeds]. A long press
 * that is released without movement therefore reports a start and an end only, so nothing can snap or move.
 */
internal suspend fun PointerInputScope.detectRoomGestures(
    begin: (Offset) -> Boolean,
    pixelsPerMm: () -> Double,
    callbacks: State<RoomGestureCallbacks>,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = awaitLongPressOrCancellation(down.id)
        // Two resting fingers belong to the view (zoom and pan), never to a room.
        val single = currentEvent.changes.count { it.pressed } == 1
        if (press != null && single && begin(press.position)) {
            press.consume()
            val tracker = RoomGestureTracker(pixelsPerMm())
            tracker.move(1, press.position.x.toDouble(), press.position.y.toDouble(), 0.0)
            track(tracker, callbacks)
        }
    }
}

/**
 * Follows a started gesture until all fingers are lifted ([RoomGestureCallbacks.onEnd]) or it is interrupted
 * ([RoomGestureCallbacks.onCancel]): by a cancel of the system, which arrives as an already consumed change, or by
 * the end of the pointer input itself.
 */
private suspend fun AwaitPointerEventScope.track(
    tracker: RoomGestureTracker,
    callbacks: State<RoomGestureCallbacks>,
) {
    var finished = false
    try {
        if (follow(tracker, callbacks)) {
            finished = true
            callbacks.value.onEnd()
        }
    } finally {
        if (!finished) callbacks.value.onCancel()
    }
}

/** Reports the progress until all fingers are lifted (`true`) or the gesture is interrupted (`false`). */
private suspend fun AwaitPointerEventScope.follow(
    tracker: RoomGestureTracker,
    callbacks: State<RoomGestureCallbacks>,
): Boolean {
    var engaged = false
    var event = awaitPointerEvent()
    while (event.changes.none { it.isConsumed }) {
        val pressed = event.changes.fastAny { it.pressed }
        feed(tracker, event)
        event.changes.fastForEach { it.consume() }
        engaged = engaged || tracker.exceeds(viewConfiguration.touchSlop)
        if (engaged) callbacks.value.onUpdate(tracker.update())
        if (!pressed) return true
        event = awaitPointerEvent()
    }
    return false
}

private fun feed(
    tracker: RoomGestureTracker,
    event: PointerEvent,
) {
    var count = 0
    var sumX = 0.0
    var sumY = 0.0
    var firstX = 0.0
    var firstY = 0.0
    var angle = 0.0
    event.changes.fastForEach { change ->
        if (change.pressed) {
            val x = change.position.x.toDouble()
            val y = change.position.y.toDouble()
            if (count == 0) {
                firstX = x
                firstY = y
            } else if (count == 1) {
                angle = atan2(y - firstY, x - firstX)
            }
            count++
            sumX += x
            sumY += y
        }
    }
    if (count > 0) tracker.move(count, sumX / count, sumY / count, angle) else tracker.move(0, 0.0, 0.0, 0.0)
}
