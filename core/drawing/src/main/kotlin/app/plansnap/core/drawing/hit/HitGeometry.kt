package app.plansnap.core.drawing.hit

import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Euclidean distances from a world point to the drawn shape of a [SceneElement], in millimeters.
 *
 * - [SceneElement.Fill]: zero inside the polygon (even-odd rule), otherwise the distance to its outline.
 * - [SceneElement.Polyline]: distance to the nearest segment, including the closing one if closed.
 * - [SceneElement.Arc]: distance to the circular arc, its end points included.
 * - [SceneElement.Label]: the text width depends on the font, so a label counts as a disc of its height in diameter
 *   around the anchor.
 */
internal object HitGeometry {
    private const val FULL_TURN = 2.0 * PI

    fun distance(
        element: SceneElement,
        point: Vec2,
    ): Double =
        when (element) {
            is SceneElement.Fill -> fillDistance(element.polygon.vertices, point)
            is SceneElement.Polyline -> polylineDistance(element.points, element.closed, point)
            is SceneElement.Arc -> arcDistance(element, point)
            is SceneElement.Label -> max(0.0, (point - element.anchor).length() - element.heightMm / 2.0)
        }

    private fun segmentDistance(
        point: Vec2,
        start: Vec2,
        end: Vec2,
    ): Double {
        // Relative to start, so that translating all points by whole millimeters gives bit-identical distances.
        val direction = end - start
        val offset = point - start
        val lengthSquared = direction.dot(direction)
        val t = if (lengthSquared > 0.0) (offset.dot(direction) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
        return Vec2(offset.x - direction.x * t, offset.y - direction.y * t).length()
    }

    /** Even-odd containment. Points exactly on the outline may fall on either side; their distance is zero anyway. */
    private fun contains(
        vertices: List<Vec2>,
        point: Vec2,
    ): Boolean {
        var inside = false
        var previous = vertices.last()
        for (current in vertices) {
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val xAtY = current.x + (point.y - current.y) * (previous.x - current.x) / (previous.y - current.y)
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun fillDistance(
        vertices: List<Vec2>,
        point: Vec2,
    ): Double = if (contains(vertices, point)) 0.0 else polylineDistance(vertices, closed = true, point = point)

    private fun polylineDistance(
        points: List<Vec2>,
        closed: Boolean,
        point: Vec2,
    ): Double {
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until points.size - 1) best = min(best, segmentDistance(point, points[i], points[i + 1]))
        if (closed) best = min(best, segmentDistance(point, points.last(), points.first()))
        return best
    }

    private fun arcDistance(
        arc: SceneElement.Arc,
        point: Vec2,
    ): Double {
        val offset = point - arc.center
        val sweep = abs(arc.sweepRad)
        val angle = StrictMath.atan2(offset.y, offset.x)
        val fromStart = if (arc.sweepRad >= 0.0) angle - arc.startRad else arc.startRad - angle
        val onSweep = sweep >= FULL_TURN || normalized(fromStart) <= sweep
        if (onSweep) return abs(offset.length() - arc.radiusMm)
        val start = Vec2(arc.radiusMm, 0.0).rotated(arc.startRad)
        val end = Vec2(arc.radiusMm, 0.0).rotated(arc.startRad + arc.sweepRad)
        return min((offset - start).length(), (offset - end).length())
    }

    /** Maps an angle into `[0, 2π)`. */
    private fun normalized(angle: Double): Double {
        val reduced = angle % FULL_TURN
        return if (reduced < 0.0) reduced + FULL_TURN else reduced
    }
}
