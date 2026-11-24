package app.plansnap.core.geometry.primitives

/**
 * Computed 2D point or vector in millimeters. Both components are always finite.
 *
 * @throws IllegalArgumentException if a component, including the result of an operation, is NaN or infinite.
 */
data class Vec2(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Vec2 components must be finite: ($x, $y)" }
    }

    /** Returns the component-wise sum. */
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    /** Returns the component-wise difference. */
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    /** Returns this vector scaled by [factor]. */
    operator fun times(factor: Double): Vec2 = Vec2(x * factor, y * factor)

    /** Returns the dot product. */
    fun dot(other: Vec2): Double = x * other.x + y * other.y

    /** Returns the z component of the cross product. */
    fun cross(other: Vec2): Double = x * other.y - y * other.x

    /** Returns the Euclidean length, computed with [StrictMath] so that it is bit-identical on every device. */
    fun length(): Double = StrictMath.hypot(x, y)

    /** Returns this vector rotated counter-clockwise by [radians], with [StrictMath] trigonometry. */
    fun rotated(radians: Double): Vec2 {
        val c = StrictMath.cos(radians)
        val s = StrictMath.sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    /** Whether both components are within [tolerance] of [other]. */
    fun isCloseTo(
        other: Vec2,
        tolerance: Double,
    ): Boolean = x.isCloseTo(other.x, tolerance) && y.isCloseTo(other.y, tolerance)

    /** Constants. */
    companion object {
        /** The origin. */
        val ZERO: Vec2 = Vec2(0.0, 0.0)
    }
}

/** Closed polygon in millimeters with at least three vertices. The vertex list is copied and immutable. */
class Polygon2(
    vertices: List<Vec2>,
) {
    /** Vertices in order. The closing edge runs from the last to the first vertex. */
    val vertices: List<Vec2> = java.util.Collections.unmodifiableList(ArrayList(vertices))

    init {
        require(this.vertices.size >= MIN_VERTICES) { "Polygon needs at least $MIN_VERTICES vertices" }
    }

    override fun equals(other: Any?): Boolean = other is Polygon2 && vertices == other.vertices

    override fun hashCode(): Int = vertices.hashCode()

    override fun toString(): String = "Polygon2($vertices)"

    private companion object {
        const val MIN_VERTICES = 3
    }
}

/** Orientation of a closed outline, with the sign `s` from DOMAIN section 3. */
enum class Winding(
    val sign: Int,
) {
    /** Counter-clockwise, `s = +1`. */
    COUNTER_CLOCKWISE(1),

    /** Clockwise, `s = -1`. */
    CLOCKWISE(-1),
}
