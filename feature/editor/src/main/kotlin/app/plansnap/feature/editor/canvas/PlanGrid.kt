package app.plansnap.feature.editor.canvas

import app.plansnap.core.drawing.viewport.Viewport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The background grid of the plan (UX section 6, token `planGrid`) and its step. The step is a 1-2-5 value in world
 * millimeters, so the lines stay at least [MIN_SPACING_DP] apart on the screen at every zoom. The same step is the
 * grid that snapping uses (REQ-UX-010), so what the user sees is what the room snaps to.
 */
internal object PlanGrid {
    /** Smallest distance between two grid lines on the screen. */
    const val MIN_SPACING_DP: Double = 24.0

    /** The smallest step, one millimeter, the resolution of the model. */
    const val MIN_STEP_MM: Double = 1.0

    private const val TWO = 2.0
    private const val FIVE = 5.0
    private const val DECADE = 10.0

    /** The steps within one decade, and the first one of the next decade. */
    private val MANTISSAS = doubleArrayOf(1.0, TWO, FIVE, DECADE)

    /** Returns the grid step in millimeters for [pixelsPerMm] on a screen of [pixelsPerDp] px per dp. */
    fun stepMm(
        pixelsPerMm: Double,
        pixelsPerDp: Double,
    ): Double {
        val minMm = MIN_SPACING_DP * pixelsPerDp / pixelsPerMm
        val decade = DECADE.pow(floor(log10(minMm)))
        val step = MANTISSAS.map { it * decade }.first { it >= minMm }
        return step.coerceAtLeast(MIN_STEP_MM)
    }

    /**
     * Grid lines in screen pixels: vertical lines at `firstX + i * spacing`, horizontal ones at `firstY + i * spacing`.
     * Prepared in composition, so the draw pass only loops.
     */
    class Lines(
        val firstX: Float,
        val firstY: Float,
        val spacing: Float,
    )

    /** Returns the lines of the grid with [stepMm] in [viewport], the first one inside the screen. */
    fun lines(
        viewport: Viewport,
        stepMm: Double,
    ): Lines {
        val scale = viewport.pixelsPerMm
        val half = viewport.widthPx / 2.0
        val halfHeight = viewport.heightPx / 2.0
        val minWorldX = viewport.centerWorld.x - half / scale
        val maxWorldY = viewport.centerWorld.y + halfHeight / scale
        val firstX = (ceil(minWorldX / stepMm) * stepMm - viewport.centerWorld.x) * scale + half
        val firstY = halfHeight - (floor(maxWorldY / stepMm) * stepMm - viewport.centerWorld.y) * scale
        return Lines(firstX.toFloat(), firstY.toFloat(), (stepMm * scale).toFloat())
    }
}
