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
         * Default range, docs/UX.md section 4.
         *
         * - `1e-4` px/mm: a closed room of at most 128 walls of 100 m spans at most half its perimeter, 6.4 km, which
         *   fits on 640 px. Double-tap fitting therefore shows every valid room completely on a phone.
         * - `100` px/mm: the shortest wall of 10 mm spans 1 000 px, so its two corners are far more than one touch
         *   target of 48 dp apart even at 4 px/dp, and the 24 dp hit radius is at most 0.96 mm in the world.
         */
        val DEFAULT: ZoomLimits = ZoomLimits(minPixelsPerMm = 1e-4, maxPixelsPerMm = 100.0)
    }
}
