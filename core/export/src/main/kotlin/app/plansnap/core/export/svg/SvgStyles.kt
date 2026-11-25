package app.plansnap.core.export.svg

import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.render.LineCap
import app.plansnap.core.render.PlanInk
import app.plansnap.core.render.SceneStyleSheet
import app.plansnap.core.render.SceneStyleSpec
import app.plansnap.core.render.argb

/** How a [SceneStyle] is drawn: its style spec with colors resolved by the export palette, weights in line units. */
internal class SvgLook(
    spec: SceneStyleSpec,
    val fillArgb: Int,
    val strokeArgb: Int,
    val textArgb: Int,
) {
    val outlined: Boolean = spec.outlined
    val strokeUnits: Double = spec.strokeUnits
    val roundCap: Boolean = spec.cap == LineCap.ROUND
    val dash: List<Double>? = spec.dash?.let { listOf(it.onUnits, it.offUnits) }
}

/**
 * SVG view of [SceneStyleSheet], the look shared with the canvas renderer (ADR 0022). It holds no look of its own; it
 * only resolves colors with [SceneStyleSheet.EXPORT_PALETTE].
 */
internal object SvgStyles {
    /** Line unit on paper in millimeters. */
    const val UNIT_MM = SceneStyleSheet.PAPER_LINE_UNIT_MM

    /** Diameter of a corner marker in line units. */
    const val MARKER_UNITS = SceneStyleSheet.MARKER_UNITS

    private val palette = SceneStyleSheet.EXPORT_PALETTE

    private val looks: List<SvgLook> =
        SceneStyle.entries.map { style ->
            val spec = SceneStyleSheet.spec(style)
            SvgLook(spec, palette.argb(spec.fill), palette.argb(spec.stroke), palette.argb(spec.text))
        }

    /** Look of [style]. */
    fun look(style: SceneStyle): SvgLook = looks[style.ordinal]

    /** CSS class name of [style], for example `wall-band`. */
    fun className(style: SceneStyle): String = SceneStyleSheet.className(style)

    /** ARGB of [ink] in the export palette. */
    fun argb(ink: PlanInk): Int = palette.argb(ink)
}
