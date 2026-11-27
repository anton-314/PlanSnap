package app.plansnap.feature.editor.canvas

import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pure arithmetic of the long-press gesture: it turns the movement of the fingers on the screen into the world
 * translation and rotation of [RoomGestureUpdate]. Free of Compose, so the JVM can test it.
 */
internal class RoomGestureTracker(
    private val pixelsPerMm: Double,
) {
    private var translationPxX = 0.0
    private var translationPxY = 0.0
    private var rotationScreenRad = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastAngle = 0.0
    private var lastCount = 0

    /**
     * Feeds the pressed fingers of one event: [count] fingers with the center ([centerX], [centerY]) and, for two or
     * more, the angle [angle] of the line between the first two. A change of the finger count only re-bases the
     * tracking, so lifting or adding a finger never makes the room jump.
     */
    fun move(
        count: Int,
        centerX: Double,
        centerY: Double,
        angle: Double,
    ) {
        if (count > 0 && count == lastCount) {
            translationPxX += centerX - lastX
            translationPxY += centerY - lastY
            if (count >= 2) rotationScreenRad += wrapped(angle - lastAngle)
        }
        lastCount = count
        lastX = centerX
        lastY = centerY
        lastAngle = angle
    }

    /**
     * Whether the fingers left the rest position: moved further than [slopPx] or twisted further than
     * [TWIST_SLOP_RAD]. Below that, the movement is the jitter of a resting finger.
     */
    fun exceeds(slopPx: Float): Boolean =
        hypot(translationPxX, translationPxY) > slopPx || abs(rotationScreenRad) > TWIST_SLOP_RAD

    /** The progress so far. Screen y points down and screen angles run clockwise, so both flip in world terms. */
    fun update(): RoomGestureUpdate =
        RoomGestureUpdate(
            // Adding 0.0 turns a negative zero into zero.
            Vec2(translationPxX / pixelsPerMm + 0.0, -translationPxY / pixelsPerMm + 0.0),
            -rotationScreenRad + 0.0,
        )

    private fun wrapped(delta: Double): Double {
        var result = delta
        while (result > PI) result -= 2.0 * PI
        while (result <= -PI) result += 2.0 * PI
        return result
    }

    /** Constants. */
    companion object {
        /** Twist of two fingers that counts as intended: 3 degrees. */
        const val TWIST_SLOP_RAD: Double = PI / 60.0
    }
}
