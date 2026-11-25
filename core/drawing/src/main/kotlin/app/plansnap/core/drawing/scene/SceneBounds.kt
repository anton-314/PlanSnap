package app.plansnap.core.drawing.scene

import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.PI
import kotlin.math.abs

/**
 * Bounds of scene elements. A fill or polyline contributes its points, an arc its end points and every axis extreme of
 * its circle that lies on the sweep, and a label the square around the disc the hit test uses (DOMAIN section 11),
 * because the text width depends on the font.
 */
internal object SceneBounds {
    private const val FULL_TURN = 2.0 * PI
    private const val QUARTER_TURNS = 4

    /** Bounds of all [elements], `null` if there are none. */
    fun of(elements: List<SceneElement>): WorldBounds? = elements.map(::of).reduceOrNull(WorldBounds::union)

    private fun of(element: SceneElement): WorldBounds =
        when (element) {
            is SceneElement.Fill -> points(element.polygon.vertices)
            is SceneElement.Polyline -> points(element.points)
            is SceneElement.Arc -> points(arcPoints(element))
            is SceneElement.Label -> square(element.anchor, element.heightMm / 2.0)
        }

    private fun arcPoints(arc: SceneElement.Arc): List<Vec2> {
        val sweep = abs(arc.sweepRad)
        val extremes =
            (0 until QUARTER_TURNS)
                .map { it * PI / 2.0 }
                .filter { angle ->
                    val fromStart = if (arc.sweepRad >= 0.0) angle - arc.startRad else arc.startRad - angle
                    normalized(fromStart) <= sweep
                }
        val angles = listOf(arc.startRad, arc.startRad + arc.sweepRad) + extremes
        return angles.map { arc.center + Vec2(arc.radiusMm, 0.0).rotated(it) }
    }

    /** Maps an angle into `[0, 2π)`. */
    private fun normalized(angle: Double): Double {
        val reduced = angle % FULL_TURN
        return if (reduced < 0.0) reduced + FULL_TURN else reduced
    }

    private fun points(points: List<Vec2>): WorldBounds =
        WorldBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

    private fun square(
        center: Vec2,
        half: Double,
    ): WorldBounds = WorldBounds(center.x - half, center.y - half, center.x + half, center.y + half)
}
