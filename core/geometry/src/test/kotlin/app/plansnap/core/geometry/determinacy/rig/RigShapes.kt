package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.geometry.primitives.Vec2

/** True shapes of the rig with their kind, ported from `stress.truth` and `stress.boundary_shape`. */
internal object RigShapes {
    /** A generated true shape after normalization. */
    data class Truth(
        val points: List<Vec2>,
        val kind: String,
    )

    /** Random convex, concave or rectilinear shape with [minCorners] to [maxCorners] corners. */
    fun truth(
        random: PythonRandom,
        minCorners: Int = 3,
        maxCorners: Int = 12,
    ): Truth {
        while (true) {
            var n = random.randint(minCorners, maxCorners)
            val kind = random.choice(TRUTH_KINDS)
            val points =
                if (kind == "rect") {
                    n = maxOf(4, n - n % 2)
                    rectilinear(random, n)
                } else {
                    radial(random, n, kind == "convex")
                }
            if (points.size >= 3 && isValidTruth(points)) return Truth(RigGeometry.normalize(points, random), kind)
        }
    }

    /** Boundary shapes of class K4. */
    fun boundary(random: PythonRandom): Truth {
        val kind = random.choice(BOUNDARY_KINDS)
        val points =
            when (kind) {
                "triangle" -> triangle(random)
                "tiny-triangle" -> tinyTriangle(random)
                "collinear" -> collinear(random, nearly = false)
                "near-collinear" -> collinear(random, nearly = true)
                "sharp" -> sharp(random)
                else -> thin(random)
            }
        return Truth(RigGeometry.normalize(points, random), kind)
    }

    private fun isValidTruth(points: List<Vec2>): Boolean =
        RigGeometry.lengths(points).min() >= MIN_EDGE_MM &&
            RigGeometry.isSimple(points, RigGeometry.TRUTH_FEATURE_MM) &&
            RigGeometry.turns(points).all { StrictMath.abs(it) < MAX_TURN_RAD }

    private fun rectilinear(
        random: PythonRandom,
        n: Int,
    ): List<Vec2> {
        val k = maxOf(1, (n - 2) / 2)
        val widths = List(k) { random.randint(600, 3000) }
        val heights = ArrayList<Int>()
        repeat(k) {
            var h = random.randint(800, 5000)
            while (heights.isNotEmpty() && StrictMath.abs(h - heights.last()) < 300) h = random.randint(800, 5000)
            heights += h
        }
        val width = widths.sum()
        val raw = mutableListOf(0 to 0, width to 0)
        var x = width
        for (i in k - 1 downTo 0) {
            raw += x to heights[i]
            x -= widths[i]
            raw += x to heights[i]
        }
        val out = ArrayList<Pair<Int, Int>>()
        for (p in raw) if (out.isEmpty() || p != out.last()) out += p
        if (out.last() == out.first()) out.removeAt(out.size - 1)
        return out.map { Vec2(it.first.toDouble(), it.second.toDouble()) }
    }

    private fun radial(
        random: PythonRandom,
        n: Int,
        convex: Boolean,
    ): List<Vec2> {
        var cuts: List<Double>
        while (true) {
            cuts = List(n) { random.uniform(0.0, FULL_TURN) }.sorted()
            val gaps = List(n) { (cuts[(it + 1) % n] - cuts[it]).mod(FULL_TURN) }
            if (gaps.min() > MIN_GAP_RAD && gaps.max() < MAX_GAP_RAD) break
        }
        if (convex) {
            val a = random.uniform(2000.0, 6000.0)
            val b = random.uniform(2000.0, 6000.0)
            return cuts.map { Vec2(a * StrictMath.cos(it), b * StrictMath.sin(it)) }
        }
        return cuts.map {
            val rx = random.uniform(1500.0, 6000.0)
            val ry = random.uniform(1500.0, 6000.0)
            Vec2(rx * StrictMath.cos(it), ry * StrictMath.sin(it))
        }
    }

    private fun triangle(random: PythonRandom): List<Vec2> {
        val bx = random.uniform(500.0, 6000.0)
        val cx = random.uniform(-2000.0, 6000.0)
        val cy = random.uniform(300.0, 5000.0)
        return listOf(Vec2.ZERO, Vec2(bx, 0.0), Vec2(cx, cy))
    }

    private fun tinyTriangle(random: PythonRandom): List<Vec2> {
        val a = random.randint(10, 40).toDouble()
        val cx = random.uniform(0.0, a)
        val cy = random.uniform(10.0, 40.0)
        return listOf(Vec2.ZERO, Vec2(a, 0.0), Vec2(cx, cy))
    }

    private fun collinear(
        random: PythonRandom,
        nearly: Boolean,
    ): List<Vec2> {
        val w = random.randint(2000, 6000)
        val h = random.randint(2000, 5000).toDouble()
        val cut = random.randint(500, w - 500).toDouble()
        val bump = if (nearly) random.choice(listOf(-1.0, 1.0)) * random.uniform(0.2, 8.0) else 0.0
        return listOf(Vec2.ZERO, Vec2(cut, bump), Vec2(w.toDouble(), 0.0), Vec2(w.toDouble(), h), Vec2(0.0, h))
    }

    private fun sharp(random: PythonRandom): List<Vec2> {
        val w = random.randint(3000, 8000).toDouble()
        val tipX = w * random.uniform(0.2, 0.9)
        val tipY = random.uniform(120.0, 500.0)
        return listOf(Vec2.ZERO, Vec2(w, 0.0), Vec2(tipX, tipY))
    }

    private fun thin(random: PythonRandom): List<Vec2> {
        val w = random.randint(3000, 9000).toDouble()
        val h = random.randint(30, 200).toDouble()
        return listOf(Vec2.ZERO, Vec2(w, 0.0), Vec2(w, h), Vec2(0.0, h))
    }

    private val TRUTH_KINDS = listOf("convex", "concave", "rect")
    private val BOUNDARY_KINDS = listOf("triangle", "tiny-triangle", "collinear", "near-collinear", "sharp", "thin")
    private const val FULL_TURN = 2.0 * StrictMath.PI
    private const val MIN_EDGE_MM = 150.0
    private val MAX_TURN_RAD = StrictMath.toRadians(170.0)
    private const val MIN_GAP_RAD = 0.25
    private const val MAX_GAP_RAD = 2.6
}
