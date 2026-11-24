package app.plansnap.core.geometry.bands

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.abs
import kotlin.math.max

/**
 * Stateless [WallBandBuilder] after DOMAIN section 8 and ADR 0003.
 *
 * Wall `w_i` runs from `V_i` to `V_{i+1}` with the unit direction `d_i` and the outward normal
 * `n_i = s_F · (d_i.y, −d_i.x)`, where `s_F` is the sign of [BandInput.winding]. Corner `i` lies at `V_i` between
 * `w_{i−1}` and `w_i`, its turn satisfies `sin τ = d_{i−1} × d_i`. Each corner is classified in this order:
 *
 * 1. [CornerJoin.COLLINEAR] if `|sin τ| < NEAR_COLLINEAR_SIN`.
 * 2. [CornerJoin.CONCAVE] if the turn runs against the winding, `s_F · sin τ < 0`.
 * 3. [CornerJoin.MITER] if the outer offset lines meet at most `MITER_LIMIT_FACTOR · max(t_{i−1}, t_i)` from `V_i`.
 * 4. [CornerJoin.BEVEL] otherwise, with the triangle `V_i, V_i + t_{i−1} n_{i−1}, V_i + t_i n_i` as fill.
 *
 * A band of positive thickness is the quadrilateral `V_i, V_{i+1}, outer end, outer start`. An outer point is the miter
 * point at a [CornerJoin.MITER] corner and the perpendicular end `V + t_i n_i` at every other corner. A wall of
 * thickness zero is a [WallBand.Line]. The inner edge of every band is exactly the wall of the outline, and every band
 * lies on the outer side of its wall, so thicknesses never change the area or the perimeter of the inner contour.
 *
 * Roots go through [StrictMath], so results are bit-identical on every device. The outline must not contain a wall of
 * length zero, otherwise [build] throws [IllegalArgumentException]. Validated rooms never contain one, because every
 * wall is at least [GeometryTolerances.MIN_WALL_MM] long.
 */
class DefaultWallBandBuilder : WallBandBuilder {
    override fun build(input: BandInput): BandLayout {
        val vertices = input.outline.vertices
        val n = vertices.size
        val sign = input.winding.sign.toDouble()
        val directions = List(n) { i -> unitDirection(vertices[i], vertices[next(i, n)], i) }
        val normals = directions.map { Vec2(sign * it.y, -sign * it.x) }
        val thicknesses = input.thicknessesMm.map { it.toDouble() }
        val joints =
            List(n) { i ->
                val previous = previous(i, n)
                joint(
                    corner = vertices[i],
                    sinTurn = directions[previous].cross(directions[i]),
                    sign = sign,
                    incoming = Offset(normals[previous], thicknesses[previous]),
                    outgoing = Offset(normals[i], thicknesses[i]),
                )
            }
        val bands =
            List(n) { i ->
                val start = vertices[i]
                val end = vertices[next(i, n)]
                val thickness = thicknesses[i]
                if (input.thicknessesMm[i] == 0L) {
                    WallBand.Line(wallIndex = i, start = start, end = end)
                } else {
                    val outward = normals[i] * thickness
                    val outerStart = joints[i].miterPoint ?: (start + outward)
                    val outerEnd = joints[next(i, n)].miterPoint ?: (end + outward)
                    WallBand.Filled(wallIndex = i, shape = Polygon2(listOf(start, end, outerEnd, outerStart)))
                }
            }
        val corners =
            List(n) { i ->
                val previous = previous(i, n)
                val fill =
                    if (joints[i].join == CornerJoin.BEVEL) {
                        val corner = vertices[i]
                        Polygon2(
                            listOf(
                                corner,
                                corner + normals[previous] * thicknesses[previous],
                                corner + normals[i] * thicknesses[i],
                            ),
                        )
                    } else {
                        null
                    }
                CornerBand(cornerIndex = i, join = joints[i].join, fill = fill)
            }
        return BandLayout(bands = bands, corners = corners)
    }

    /** Outer offset line of a wall: all points `p` with `(p − V) · normal = thickness`. */
    private data class Offset(
        val normal: Vec2,
        val thickness: Double,
    )

    /** Join of one corner, [miterPoint] is present exactly for [CornerJoin.MITER]. */
    private data class Joint(
        val join: CornerJoin,
        val miterPoint: Vec2? = null,
    )

    private companion object {
        fun next(
            i: Int,
            n: Int,
        ): Int = (i + 1) % n

        fun previous(
            i: Int,
            n: Int,
        ): Int = (i + n - 1) % n

        fun unitDirection(
            from: Vec2,
            to: Vec2,
            wallIndex: Int,
        ): Vec2 {
            val delta = to - from
            val length = delta.length()
            require(length > 0.0) { "Wall $wallIndex has length zero" }
            return delta * (1.0 / length)
        }

        fun joint(
            corner: Vec2,
            sinTurn: Double,
            sign: Double,
            incoming: Offset,
            outgoing: Offset,
        ): Joint =
            when {
                abs(sinTurn) < GeometryTolerances.NEAR_COLLINEAR_SIN -> Joint(CornerJoin.COLLINEAR)
                sign * sinTurn < 0.0 -> Joint(CornerJoin.CONCAVE)
                else -> convexJoint(corner, incoming, outgoing)
            }

        /** Miter within `MITER_LIMIT_FACTOR · max(t_a, t_b)` from [corner], bevel beyond. */
        fun convexJoint(
            corner: Vec2,
            incoming: Offset,
            outgoing: Offset,
        ): Joint {
            val offset = miterOffset(incoming, outgoing)
            val limit = GeometryTolerances.MITER_LIMIT_FACTOR * max(incoming.thickness, outgoing.thickness)
            return if (offset.length() <= limit) Joint(CornerJoin.MITER, corner + offset) else Joint(CornerJoin.BEVEL)
        }

        /**
         * Solves `m · n_a = t_a` and `m · n_b = t_b` by Cramer's rule. The caller guarantees
         * `|n_a × n_b| = |sin τ| ≥ NEAR_COLLINEAR_SIN`, so the system is regular and `|m|` stays finite.
         */
        fun miterOffset(
            a: Offset,
            b: Offset,
        ): Vec2 {
            val det = a.normal.cross(b.normal)
            val x = (a.thickness * b.normal.y - b.thickness * a.normal.y) / det
            val y = (b.thickness * a.normal.x - a.thickness * b.normal.x) / det
            return Vec2(x, y)
        }
    }
}
