package app.plansnap.core.geometry.polygon

import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.RobustPredicates
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Stateless [PolygonAnalyzer] after DOMAIN section 7. Results are deterministic and bit-identical on every device,
 * because roots go through [StrictMath] and all signs come from exact predicates.
 *
 * Defined results for degenerate input, none of which throws:
 * - The sign of [signedAreaMm2] and the result of [winding] are always those of the exact shoelace sum. An exactly
 *   zero sum, for instance from collinear or coincident vertices, gives area `0.0` and winding `null`.
 * - An area or perimeter beyond the double range is `+Infinity` (signed area `±Infinity`), never NaN.
 * - [isSimple] is `false` for a polygon with exactly zero area, because a simple polygon always encloses area. This
 *   covers all collinear triangles. Coincident vertices or an edge that folds back onto its neighbor make two
 *   non-adjacent edges touch, so such a polygon with four or more vertices is never simple either.
 * - A `minFeatureMm` that is zero, negative or NaN only rejects exact intersections and touches.
 *
 * Fewer than three vertices and NaN or infinite coordinates cannot occur, [Polygon2] and [Vec2] reject them.
 */
class DefaultPolygonAnalyzer : PolygonAnalyzer {
    override fun signedAreaMm2(polygon: Polygon2): Double {
        val vertices = polygon.vertices
        val exactSign = RobustPredicates.shoelaceSign(vertices)
        if (exactSign == 0) return 0.0
        val area = translatedShoelace(vertices) / 2.0
        val consistent = area.isFinite() && area.sign.toInt() == exactSign
        return if (consistent) area else RobustPredicates.exactSignedArea(vertices)
    }

    override fun areaMm2(polygon: Polygon2): Double = abs(signedAreaMm2(polygon))

    override fun perimeterMm(polygon: Polygon2): Double {
        val vertices = polygon.vertices
        var sum = 0.0
        for (i in vertices.indices) {
            val from = vertices[i]
            val to = vertices[(i + 1) % vertices.size]
            sum += StrictMath.hypot(to.x - from.x, to.y - from.y)
        }
        return sum
    }

    override fun winding(polygon: Polygon2): Winding? =
        when (RobustPredicates.shoelaceSign(polygon.vertices)) {
            Winding.COUNTER_CLOCKWISE.sign -> Winding.COUNTER_CLOCKWISE
            Winding.CLOCKWISE.sign -> Winding.CLOCKWISE
            else -> null
        }

    override fun isSimple(
        polygon: Polygon2,
        minFeatureMm: Double,
    ): Boolean {
        if (winding(polygon) == null) return false
        val vertices = polygon.vertices
        val n = vertices.size
        val feature = if (minFeatureMm > 0.0) minFeatureMm else 0.0
        return nonAdjacentEdgePairs(n).none { (i, j) ->
            edgesConflict(vertices[i], vertices[i + 1], vertices[j], vertices[(j + 1) % n], feature)
        }
    }

    /**
     * Shoelace sum relative to the first vertex, which keeps the terms small for an outline far from the origin. The
     * terms of the two edges at the first vertex vanish.
     */
    private fun translatedShoelace(vertices: List<Vec2>): Double {
        val origin = vertices[0]
        var sum = 0.0
        for (i in 1 until vertices.size - 1) {
            val a = vertices[i]
            val b = vertices[i + 1]
            sum += (a.x - origin.x) * (b.y - origin.y) - (b.x - origin.x) * (a.y - origin.y)
        }
        return sum
    }

    private companion object {
        /** Index pairs `i < j` of edges that share no vertex. Edge 0 and the closing edge `n − 1` are adjacent. */
        fun nonAdjacentEdgePairs(n: Int): Sequence<Pair<Int, Int>> =
            sequence {
                for (i in 0 until n) {
                    val last = if (i == 0) n - 2 else n - 1
                    for (j in i + 2..last) yield(i to j)
                }
            }

        /** Whether segments `p1p2` and `q1q2` intersect, touch or come closer than [feature]. */
        fun edgesConflict(
            p1: Vec2,
            p2: Vec2,
            q1: Vec2,
            q2: Vec2,
            feature: Double,
        ): Boolean {
            if (segmentsIntersect(p1, p2, q1, q2)) return true
            val distance =
                min(
                    min(pointSegmentDistance(p1, q1, q2), pointSegmentDistance(p2, q1, q2)),
                    min(pointSegmentDistance(q1, p1, p2), pointSegmentDistance(q2, p1, p2)),
                )
            return distance < feature
        }

        /** Exact intersection test, including touching endpoints and collinear overlap. */
        fun segmentsIntersect(
            p1: Vec2,
            p2: Vec2,
            q1: Vec2,
            q2: Vec2,
        ): Boolean {
            val d1 = RobustPredicates.orientation(q1, q2, p1)
            val d2 = RobustPredicates.orientation(q1, q2, p2)
            val d3 = RobustPredicates.orientation(p1, p2, q1)
            val d4 = RobustPredicates.orientation(p1, p2, q2)
            val proper = d1 * d2 < 0 && d3 * d4 < 0
            return proper ||
                (d1 == 0 && inBox(q1, q2, p1)) ||
                (d2 == 0 && inBox(q1, q2, p2)) ||
                (d3 == 0 && inBox(p1, p2, q1)) ||
                (d4 == 0 && inBox(p1, p2, q2))
        }

        /** Whether [p], known to be collinear with [a] and [b], lies on the closed segment between them. */
        fun inBox(
            a: Vec2,
            b: Vec2,
            p: Vec2,
        ): Boolean = p.x in min(a.x, b.x)..max(a.x, b.x) && p.y in min(a.y, b.y)..max(a.y, b.y)

        /** Euclidean distance from [p] to the closed segment `ab`. NaN only if coordinate differences overflow. */
        fun pointSegmentDistance(
            p: Vec2,
            a: Vec2,
            b: Vec2,
        ): Double {
            val abx = b.x - a.x
            val aby = b.y - a.y
            val apx = p.x - a.x
            val apy = p.y - a.y
            val lengthSquared = abx * abx + aby * aby
            val t = if (lengthSquared > 0.0) ((apx * abx + apy * aby) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
            return StrictMath.hypot(apx - t * abx, apy - t * aby)
        }
    }
}
