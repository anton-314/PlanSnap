package app.plansnap.core.drawing.viewport

import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.min

/**
 * Affine mapping between world millimeters (x right, y up) and screen pixels (x right, y down), ARCHITECTURE
 * section 8.
 *
 * With the zoom `s` = [Viewport.pixelsPerMm], the screen size `(w, h)` and the center `c` = [Viewport.centerWorld]:
 *
 * ```
 * screen.x = w / 2 + (world.x − c.x) · s
 * screen.y = h / 2 − (world.y − c.y) · s
 * ```
 *
 * **Accuracy:** both directions are exact inverses up to rounding. For a world point `p`, the round trip
 * `screenToWorld(worldToScreen(p))` differs from `p` per component by at most [ROUND_TRIP_RELATIVE_TOLERANCE] times
 * the magnitude `M = max(1, |c.x|, |c.y|, |p.x|, |p.y|, (w + h) / s)`. The round trip from the screen differs by at
 * most `ROUND_TRIP_RELATIVE_TOLERANCE · M · s` pixels.
 *
 * Zooming ([zoomAround], [fit]) keeps the zoom within [zoomLimits]. Panning never changes the zoom.
 *
 * @property zoomLimits allowed zoom range.
 */
class DefaultViewportTransform(
    val zoomLimits: ZoomLimits = ZoomLimits.DEFAULT,
) : ViewportTransform {
    /**
     * @throws IllegalArgumentException if the screen point is not finite, which only happens for world points
     * beyond about `1e308 / pixelsPerMm` mm.
     */
    override fun worldToScreen(
        viewport: Viewport,
        world: Vec2,
    ): ScreenPoint {
        val scale = viewport.pixelsPerMm
        return ScreenPoint(
            x = viewport.widthPx / 2.0 + (world.x - viewport.centerWorld.x) * scale,
            y = viewport.heightPx / 2.0 - (world.y - viewport.centerWorld.y) * scale,
        )
    }

    /** @throws IllegalArgumentException if the world point is not finite, which needs an extreme zoom-out. */
    override fun screenToWorld(
        viewport: Viewport,
        screen: ScreenPoint,
    ): Vec2 {
        val scale = viewport.pixelsPerMm
        return Vec2(
            x = viewport.centerWorld.x + (screen.x - viewport.widthPx / 2.0) / scale,
            y = viewport.centerWorld.y - (screen.y - viewport.heightPx / 2.0) / scale,
        )
    }

    /**
     * The new zoom is `pixelsPerMm · factor` clamped to [zoomLimits]. The world point under [focus] stays under
     * [focus] within the accuracy described on the class, also when the zoom is clamped.
     *
     * @throws IllegalArgumentException if [factor] is not positive and finite.
     */
    override fun zoomAround(
        viewport: Viewport,
        focus: ScreenPoint,
        factor: Double,
    ): Viewport {
        require(factor.isFinite() && factor > 0.0) { "Invalid zoom factor: $factor" }
        val anchor = screenToWorld(viewport, focus)
        val scale = zoomLimits.clamp(viewport.pixelsPerMm * factor)
        val center =
            Vec2(
                x = anchor.x - (focus.x - viewport.widthPx / 2.0) / scale,
                y = anchor.y + (focus.y - viewport.heightPx / 2.0) / scale,
            )
        return viewport.copy(centerWorld = center, pixelsPerMm = scale)
    }

    /**
     * The content follows the finger: a world point shown at screen point `q` is shown at `q + (deltaXPx, deltaYPx)`
     * afterwards. The zoom is unchanged.
     *
     * @throws IllegalArgumentException if a delta is not finite.
     */
    override fun panBy(
        viewport: Viewport,
        deltaXPx: Double,
        deltaYPx: Double,
    ): Viewport {
        require(deltaXPx.isFinite() && deltaYPx.isFinite()) { "Pan delta must be finite: ($deltaXPx, $deltaYPx)" }
        val scale = viewport.pixelsPerMm
        val center =
            Vec2(
                x = viewport.centerWorld.x - deltaXPx / scale,
                y = viewport.centerWorld.y + deltaYPx / scale,
            )
        return viewport.copy(centerWorld = center)
    }

    /**
     * Centers [bounds] and chooses the largest zoom at which they fit completely, without margin, clamped to
     * [zoomLimits]. A caller that wants a screen margin of `m` px, as the editor does (docs/UX.md section 4), fits
     * into a viewport whose width and height are reduced by `2m` and then restores the original size. The center
     * does not depend on the size, so this keeps [bounds] centered.
     *
     * An extent of zero places no limit on the zoom along its axis, so a single point is shown at the maximum zoom.
     * On a screen without area only the center changes, the zoom stays as it is.
     */
    override fun fit(
        viewport: Viewport,
        bounds: WorldBounds,
    ): Viewport {
        // Halving first cannot overflow, even for bounds spanning the whole Double range.
        val center = Vec2(bounds.minX / 2.0 + bounds.maxX / 2.0, bounds.minY / 2.0 + bounds.maxY / 2.0)
        if (viewport.widthPx == 0 || viewport.heightPx == 0) return viewport.copy(centerWorld = center)
        val scale =
            zoomLimits.clamp(
                min(axisScale(viewport.widthPx, bounds.width), axisScale(viewport.heightPx, bounds.height)),
            )
        return viewport.copy(centerWorld = center, pixelsPerMm = scale)
    }

    /** Returns the world rectangle covered by the screen `[0, widthPx] × [0, heightPx]`. */
    override fun visibleBounds(viewport: Viewport): WorldBounds {
        val halfWidth = viewport.widthPx / 2.0 / viewport.pixelsPerMm
        val halfHeight = viewport.heightPx / 2.0 / viewport.pixelsPerMm
        val center = viewport.centerWorld
        return WorldBounds(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
    }

    private fun axisScale(
        screenPx: Int,
        extentMm: Double,
    ): Double = if (extentMm > 0.0) screenPx / extentMm else Double.POSITIVE_INFINITY

    /** Constants. */
    companion object {
        /** Relative bound of the round-trip error, see the class documentation. */
        const val ROUND_TRIP_RELATIVE_TOLERANCE: Double = 1e-12
    }
}
