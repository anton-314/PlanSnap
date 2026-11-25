package app.plansnap.core.drawing.scene

import app.plansnap.core.drawing.scene.SceneMetrics.ANGLE_LABEL_DISTANCE_MM
import app.plansnap.core.drawing.scene.SceneMetrics.ANGLE_LABEL_HEIGHT_MM
import app.plansnap.core.drawing.scene.SceneMetrics.DIMENSION_LABEL_HEIGHT_MM
import app.plansnap.core.drawing.scene.SceneMetrics.DIMENSION_OFFSET_MM
import app.plansnap.core.drawing.scene.SceneMetrics.EXTENSION_GAP_MM
import app.plansnap.core.drawing.scene.SceneMetrics.EXTENSION_OVERSHOOT_MM
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.RoomId
import kotlin.math.PI

/** Dimension chains, diagonals, angle labels and corner markers. */
internal object Annotations {
    /**
     * Dimension of [wall] on its outer side: the dimension line [DIMENSION_OFFSET_MM] beyond the outer face, two
     * extension lines without target and the length label one label height beyond the line. Line and label select
     * the wall.
     */
    fun dimension(
        roomId: RoomId,
        wall: WallFrame,
    ): List<SceneElement> {
        val target = SceneTarget.Wall(roomId, wall.wallId)
        val face = wall.thicknessMm
        val line = face + DIMENSION_OFFSET_MM
        val length = (wall.end - wall.start).length()
        return listOf(
            segment(wall.at(0.0, line), wall.at(length, line), SceneStyle.DIMENSION, target),
            extension(wall, 0.0, face, line),
            extension(wall, length, face, line),
            SceneElement.Label(
                anchor = wall.at(length / 2.0, line + DIMENSION_LABEL_HEIGHT_MM),
                text = LabelText.Dimension(wall.lengthMm),
                rotationRad = uprightAngle(wall.direction),
                heightMm = DIMENSION_LABEL_HEIGHT_MM,
                style = SceneStyle.DIMENSION,
                target = target,
            ),
        )
    }

    private fun extension(
        wall: WallFrame,
        alongMm: Double,
        face: Double,
        line: Double,
    ): SceneElement =
        segment(
            wall.at(alongMm, face + EXTENSION_GAP_MM),
            wall.at(alongMm, line + EXTENSION_OVERSHOOT_MM),
            SceneStyle.DIMENSION,
            null,
        )

    /** The measured [diagonal] from [from] to [to] with its raw length at the midpoint, both selecting it. */
    fun diagonal(
        roomId: RoomId,
        diagonal: Diagonal,
        from: Vec2,
        to: Vec2,
        style: SceneStyle,
    ): List<SceneElement> {
        val target = SceneTarget.Diagonal(roomId, diagonal.id)
        return listOf(
            segment(from, to, style, target),
            SceneElement.Label(
                anchor = midpoint(from, to),
                text = LabelText.Dimension(diagonal.length.mm.toDouble()),
                rotationRad = uprightAngle(to - from),
                heightMm = DIMENSION_LABEL_HEIGHT_MM,
                style = SceneStyle.DIMENSION,
                target = target,
            ),
        )
    }

    /**
     * Interior angle `180° − s_F · wrap(τ)` (DOMAIN section 3) at the corner where [outgoing] starts, placed
     * on the inner bisector `−(n_{i−1} + n_i)`, which points into the room at convex and reflex corners alike.
     */
    fun interiorAngle(
        roomId: RoomId,
        incoming: WallFrame,
        outgoing: WallFrame,
        turnRad: Double,
        windingSign: Int,
    ): SceneElement {
        val bisector = (incoming.outward + outgoing.outward) * -1.0
        val inward = bisector * (1.0 / bisector.length())
        val degrees = Math.toDegrees(PI - windingSign * wrap(turnRad))
        return SceneElement.Label(
            anchor = outgoing.start + inward * ANGLE_LABEL_DISTANCE_MM,
            text = LabelText.InteriorAngle(degrees),
            rotationRad = 0.0,
            heightMm = ANGLE_LABEL_HEIGHT_MM,
            style = SceneStyle.LABEL,
            target = SceneTarget.Corner(roomId, outgoing.index),
        )
    }

    /** Marker of corner [index] at [at]: a polyline of length zero that draws nothing but makes the corner hittable. */
    fun cornerMarker(
        roomId: RoomId,
        index: Int,
        at: Vec2,
    ): SceneElement = segment(at, at, SceneStyle.WALL_LINE, SceneTarget.Corner(roomId, index))

    /** Maps [angle] into `(−π, π]`. */
    private fun wrap(angle: Double): Double {
        val reduced = StrictMath.IEEEremainder(angle, 2.0 * PI)
        return if (reduced <= -PI) reduced + 2.0 * PI else reduced
    }
}
