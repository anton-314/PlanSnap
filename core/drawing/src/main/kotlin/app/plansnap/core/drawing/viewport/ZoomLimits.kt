package app.plansnap.core.drawing.viewport

/**
 * Allowed range of [Viewport.pixelsPerMm] for the zooming operations of [DefaultViewportTransform].
 *
 * @property minPixelsPerMm smallest zoom, positive and finite.
 * @property maxPixelsPerMm largest zoom, finite and at least [minPixelsPerMm].
 * @throws IllegalArgumentException if a limit is not positive and finite or the range is empty.
 */
data class ZoomLimits(
    val minPixelsPerMm: Double,
    val maxPixelsPerMm: Double,
) {
    init {
        require(minPixelsPerMm.isFinite() && minPixelsPerMm > 0.0) { "Invalid minimum zoom: $minPixelsPerMm" }
        require(maxPixelsPerMm.isFinite() && maxPixelsPerMm >= minPixelsPerMm) {
            "Invalid maximum zoom: $maxPixelsPerMm"
        }
    }

    /** Returns [pixelsPerMm] limited to this range. NaN is not accepted. */
    fun clamp(pixelsPerMm: Double): Double {
        require(!pixelsPerMm.isNaN()) { "Zoom must not be NaN" }
        return pixelsPerMm.coerceIn(minPixelsPerMm, maxPixelsPerMm)
    }

    /** Defaults. */
    companion object {
        /**
         * Default range. `1e-4` px/mm shows a plan of 10 km on 1 000 px, far beyond any project of walls of at most
         * 100 m. `100` px/mm shows a single millimeter as 100 px, enough to tell a 10 mm wall apart from its corners.
         */
        val DEFAULT: ZoomLimits = ZoomLimits(minPixelsPerMm = 1e-4, maxPixelsPerMm = 100.0)
    }
}
