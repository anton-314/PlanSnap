package app.plansnap.core.geometry.bands

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding

/**
 * Input of the band construction. The thickness list is copied and immutable.
 *
 * @property outline inner contour.
 * @property winding orientation of [outline].
 * @throws IllegalArgumentException if the thickness count or a thickness value is invalid.
 */
class BandInput(
    val outline: Polygon2,
    val winding: Winding,
    thicknessesMm: List<Long>,
) {
    /** Outward thickness `t_i` of each wall in mm, within `0..MAX_THICKNESS_MM`. */
    val thicknessesMm: List<Long> = java.util.Collections.unmodifiableList(ArrayList(thicknessesMm))

    init {
        require(this.thicknessesMm.size == outline.vertices.size) { "Expected one thickness per wall" }
        require(this.thicknessesMm.all { it in 0L..GeometryTolerances.MAX_THICKNESS_MM }) {
            "Wall thickness out of range"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BandInput &&
            outline == other.outline && winding == other.winding && thicknessesMm == other.thicknessesMm

    override fun hashCode(): Int = 31 * (31 * outline.hashCode() + winding.hashCode()) + thicknessesMm.hashCode()

    override fun toString(): String = "BandInput(outline=$outline, winding=$winding, thicknessesMm=$thicknessesMm)"
}

/** How two wall bands meet at a corner (DOMAIN section 8). */
enum class CornerJoin {
    /** Nearly collinear, band ends are perpendicular. */
    COLLINEAR,

    /** The outer offset lines meet within the miter limit. */
    MITER,

    /** Beyond the miter limit, a triangle fills the gap. */
    BEVEL,

    /** Concave corner, band ends are perpendicular and may overlap. */
    CONCAVE,
}

/** Drawable band of the wall at [wallIndex]. */
sealed interface WallBand {
    /** Index of the wall. */
    val wallIndex: Int

    /** Wall of thickness zero, drawn as the line from [start] to [end]. */
    data class Line(
        override val wallIndex: Int,
        val start: Vec2,
        val end: Vec2,
    ) : WallBand

    /** Wall of positive thickness, drawn as the filled [shape]. */
    data class Filled(
        override val wallIndex: Int,
        val shape: Polygon2,
    ) : WallBand
}

/**
 * Join at the corner [cornerIndex].
 *
 * @property fill bevel triangle, present exactly for [CornerJoin.BEVEL].
 */
data class CornerBand(
    val cornerIndex: Int,
    val join: CornerJoin,
    val fill: Polygon2?,
) {
    init {
        require((join == CornerJoin.BEVEL) == (fill != null)) { "A fill is required exactly for a bevel" }
        require(fill == null || fill.vertices.size == TRIANGLE_VERTICES) { "A bevel fill must be a triangle" }
    }

    private companion object {
        const val TRIANGLE_VERTICES = 3
    }
}

/** All bands and corner joins of a room, each in index order. */
data class BandLayout(
    val bands: List<WallBand>,
    val corners: List<CornerBand>,
)

/** Builds outward wall bands (DOMAIN section 8, ADR 0003). Bands never affect area or perimeter. */
fun interface WallBandBuilder {
    /** Builds the band layout for [input]. */
    fun build(input: BandInput): BandLayout
}
