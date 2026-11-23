package app.plansnap.core.geometry.polygon

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Winding

/** Polygon metrics and simplicity test (DOMAIN section 7). */
interface PolygonAnalyzer {
    /** Returns the shoelace area in mm², positive for a counter-clockwise polygon. */
    fun signedAreaMm2(polygon: Polygon2): Double

    /** Returns the absolute shoelace area in mm². */
    fun areaMm2(polygon: Polygon2): Double

    /** Returns the sum of all edge lengths in mm, including the closing edge. */
    fun perimeterMm(polygon: Polygon2): Double

    /** Returns the orientation, or `null` for a degenerate polygon without area. */
    fun winding(polygon: Polygon2): Winding?

    /** Whether no two non-adjacent edges intersect or come closer than [minFeatureMm]. */
    fun isSimple(
        polygon: Polygon2,
        minFeatureMm: Double = GeometryTolerances.MIN_FEATURE_MM,
    ): Boolean
}
