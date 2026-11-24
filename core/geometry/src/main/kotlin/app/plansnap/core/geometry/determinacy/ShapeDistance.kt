package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.atan2

/**
 * Distance between two shapes with the same corner order in mm (DOMAIN section 5.6).
 *
 * Both outlines are moved to their vertex centroid, and [first] is turned by the rotation that minimizes the sum of
 * squared vertex distances. The result is the largest remaining vertex distance. It does not depend on the local origin
 * or the orientation of either outline. A mirror image is not aligned and counts as a different shape.
 *
 * @throws IllegalArgumentException if the vertex counts differ.
 */
fun shapeDistanceMm(
    first: Polygon2,
    second: Polygon2,
): Double {
    require(first.vertices.size == second.vertices.size) { "Shapes must have the same vertex count" }
    val a = centered(first.vertices)
    val b = centered(second.vertices)
    val dot = a.indices.sumOf { a[it].dot(b[it]) }
    val cross = a.indices.sumOf { a[it].cross(b[it]) }
    val angle = atan2(cross, dot)
    return a.indices.maxOf { (a[it].rotated(angle) - b[it]).length() }
}

private fun centered(points: List<Vec2>): List<Vec2> {
    val center = Vec2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
    return points.map { it - center }
}
