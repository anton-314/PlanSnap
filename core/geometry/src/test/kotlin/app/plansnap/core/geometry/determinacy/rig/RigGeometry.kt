package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.geometry.primitives.Vec2

/** Plane geometry of the rig, ported from `stress.py` and `flow.py` so that generated rooms match the prototype. */
internal object RigGeometry {
    const val TRUTH_FEATURE_MM = 30.0
    const val CHECK_FEATURE_MM = 1.0

    /** Turns at each vertex, `atan2(a × b, a · b)` of the edges into and out of it. */
    fun turns(points: List<Vec2>): List<Double> {
        val n = points.size
        return List(n) { i ->
            val a = points[i] - points[(i + n - 1) % n]
            val b = points[(i + 1) % n] - points[i]
            StrictMath.atan2(a.cross(b), a.dot(b))
        }
    }

    /** Edge lengths `|P_{i+1} − P_i|`. */
    fun lengths(points: List<Vec2>): List<Double> {
        val n = points.size
        return List(n) { (points[(it + 1) % n] - points[it]).length() }
    }

    /** Simplicity of `flow.simple`: every pair of non-adjacent edges is at least [minFeature] apart. */
    fun isSimple(
        points: List<Vec2>,
        minFeature: Double = CHECK_FEATURE_MM,
    ): Boolean {
        val n = points.size
        for (i in 0 until n) {
            for (j in i + 2 until n) {
                if (i == 0 && j == n - 1) continue
                val distance = segmentDistance(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])
                if (distance < minFeature) return false
            }
        }
        return true
    }

    /**
     * Rotates the vertex list to a random start, mirrors it with probability one half and moves it to `V_0` at the
     * origin with `w_0` along +x, as `stress.normalize`.
     */
    fun normalize(
        points: List<Vec2>,
        random: PythonRandom,
    ): List<Vec2> {
        val k = random.randrange(points.size)
        var rotated = points.drop(k) + points.take(k)
        if (random.random() < HALF) rotated = rotated.map { Vec2(it.x, -it.y) }
        val origin = rotated[0]
        val angle = StrictMath.atan2(rotated[1].y - origin.y, rotated[1].x - origin.x)
        val c = StrictMath.cos(-angle)
        val s = StrictMath.sin(-angle)
        return rotated.map {
            val dx = it.x - origin.x
            val dy = it.y - origin.y
            Vec2(dx * c - dy * s, dx * s + dy * c)
        }
    }

    private fun segmentDistance(
        a: Vec2,
        b: Vec2,
        c: Vec2,
        d: Vec2,
    ): Double {
        val crosses = orient(a, b, c) * orient(a, b, d) < 0.0 && orient(c, d, a) * orient(c, d, b) < 0.0
        if (crosses) return 0.0
        return minOf(pointDistance(a, c, d), pointDistance(b, c, d), pointDistance(c, a, b), pointDistance(d, a, b))
    }

    private fun orient(
        p: Vec2,
        q: Vec2,
        r: Vec2,
    ): Double = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

    private fun pointDistance(
        p: Vec2,
        q: Vec2,
        r: Vec2,
    ): Double {
        val vx = r.x - q.x
        val vy = r.y - q.y
        val squared = vx * vx + vy * vy
        if (!(squared > 0.0)) return StrictMath.hypot(p.x - q.x, p.y - q.y)
        val u = (((p.x - q.x) * vx + (p.y - q.y) * vy) / squared).coerceIn(0.0, 1.0)
        return StrictMath.hypot(p.x - q.x - u * vx, p.y - q.y - u * vy)
    }

    private const val HALF = 0.5
}
