package app.plansnap.testing.fixtures

import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/**
 * Independent textbook formulas used only to check fixtures. They read plain coordinates and never call production
 * geometry code, so a mistake in a fixture cannot hide behind the same mistake in the code under test.
 */
object IndependentGeometry {
    /** Shoelace area of a polygon, `½ |Σ (x_i y_{i+1} − x_{i+1} y_i)|`. */
    fun shoelaceArea(points: List<Vec2>): Double =
        abs(
            points.indices.sumOf { i ->
                points[i].x * points[(i + 1) % points.size].y -
                    points[(i + 1) % points.size].x * points[i].y
            },
        ) /
            2.0

    /** Signed shoelace area, positive for counter-clockwise order. */
    fun signedArea(points: List<Vec2>): Double =
        points.indices.sumOf { i ->
            points[i].x * points[(i + 1) % points.size].y -
                points[(i + 1) % points.size].x * points[i].y
        } /
            2.0

    /** Length of every edge `V_i -> V_{i+1}`. */
    fun edgeLengths(points: List<Vec2>): List<Double> =
        points.indices.map { i ->
            val next = points[(i + 1) % points.size]
            hypot(next.x - points[i].x, next.y - points[i].y)
        }

    /** Interior angle in degrees at every vertex of a counter-clockwise polygon, `180° − turn`. */
    fun interiorAnglesDeg(points: List<Vec2>): List<Double> =
        points.indices.map { i ->
            val previous = points[(i + points.size - 1) % points.size]
            val next = points[(i + 1) % points.size]
            val inX = points[i].x - previous.x
            val inY = points[i].y - previous.y
            val outX = next.x - points[i].x
            val outY = next.y - points[i].y
            val turn = atan2(inX * outY - inY * outX, inX * outX + inY * outY)
            180.0 - Math.toDegrees(turn)
        }

    /**
     * Distance between the end of the raw traverse and its start. The traverse uses the wall lengths and the entered
     * corners of `V_1 … V_{n-1}` with `τ = d · (180° − α)`, as defined in DOMAIN section 3, and ignores `V_0`.
     */
    fun traverseClosureGapMm(room: Room): Double {
        var heading = 0.0
        var x = 0.0
        var y = 0.0
        room.walls.forEachIndexed { i, wall ->
            if (i > 0) heading += turnRad(room.corners[i])
            x += wall.length.mm * cos(heading)
            y += wall.length.mm * sin(heading)
        }
        return hypot(x, y)
    }

    private fun turnRad(corner: CornerSpec): Double {
        val alphaDeg =
            when (corner) {
                is CornerSpec.DeclaredRight -> RIGHT_ANGLE_DEG
                is CornerSpec.Measured -> corner.angle.milliDegrees / MILLI_PER_DEGREE
                CornerSpec.Unspecified -> error("Only V_0 is unspecified")
            }
        return Math.toRadians(corner.direction!!.sign * (STRAIGHT_DEG - alphaDeg))
    }

    /** Whether the closed polygon is simple, i.e. no two non-adjacent edges touch or cross. */
    fun isSimple(points: List<Vec2>): Boolean {
        val n = points.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val adjacent = j == i + 1 || (i == 0 && j == n - 1)
                if (!adjacent &&
                    segmentsTouch(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun segmentsTouch(
        a: Vec2,
        b: Vec2,
        c: Vec2,
        d: Vec2,
    ): Boolean {
        val d1 = orientation(c, d, a)
        val d2 = orientation(c, d, b)
        val d3 = orientation(a, b, c)
        val d4 = orientation(a, b, d)
        val properCross = d1 * d2 < 0.0 && d3 * d4 < 0.0
        val touching =
            (d1 == 0.0 && onSegment(c, d, a)) ||
                (d2 == 0.0 && onSegment(c, d, b)) ||
                (d3 == 0.0 && onSegment(a, b, c)) ||
                (d4 == 0.0 && onSegment(a, b, d))
        return properCross || touching
    }

    private fun orientation(
        a: Vec2,
        b: Vec2,
        c: Vec2,
    ): Double = sign((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))

    private fun onSegment(
        a: Vec2,
        b: Vec2,
        p: Vec2,
    ): Boolean = p.x in minOf(a.x, b.x)..maxOf(a.x, b.x) && p.y in minOf(a.y, b.y)..maxOf(a.y, b.y)

    private const val RIGHT_ANGLE_DEG = 90.0
    private const val STRAIGHT_DEG = 180.0
    private const val MILLI_PER_DEGREE = 1000.0
}
