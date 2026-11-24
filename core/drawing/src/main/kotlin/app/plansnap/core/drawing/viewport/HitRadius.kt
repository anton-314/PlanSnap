package app.plansnap.core.drawing.viewport

/**
 * Converts a touch tolerance given in screen density-independent pixels into the world radius for
 * [HitTester.hitTest]. The tolerance stays the same on the screen at every zoom, so the world radius shrinks while
 * zooming in.
 *
 * @param radiusDp tolerance on the screen in dp, finite and not negative.
 * @param pixelsPerDp screen density in px per dp, positive and finite.
 * @return radius in world millimeters.
 * @throws IllegalArgumentException if an argument is out of range.
 */
fun Viewport.hitRadiusMm(
    radiusDp: Double = DEFAULT_HIT_RADIUS_DP,
    pixelsPerDp: Double,
): Double {
    require(radiusDp.isFinite() && radiusDp >= 0.0) { "Invalid hit radius: $radiusDp" }
    require(pixelsPerDp.isFinite() && pixelsPerDp > 0.0) { "Invalid density: $pixelsPerDp" }
    return radiusDp * pixelsPerDp / pixelsPerMm
}

/** Default hit radius in dp: half of the minimum touch target of 48 dp (docs/UX.md section 6, REQ-UX-008). */
const val DEFAULT_HIT_RADIUS_DP: Double = 24.0
