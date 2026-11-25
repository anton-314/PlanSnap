package app.plansnap.core.render

import app.plansnap.core.drawing.scene.SceneStyle

/** Dash pattern in line units. */
internal class Dash(
    val on: Float,
    val off: Float,
)

/**
 * Canvas view of [SceneStyleSheet]: the same look, precomputed per style as floats and dash indices so a draw call
 * allocates nothing. It holds no look of its own (ADR 0022). Weights are in line units (see [SceneRenderer]).
 */
internal object StyleLook {
    /** Diameter of the dot that marks a corner, in line units. */
    val MARKER_UNITS: Float = SceneStyleSheet.MARKER_UNITS.toFloat()

    private val specs: Array<SceneStyleSpec> = SceneStyle.entries.map(SceneStyleSheet::spec).toTypedArray()

    private val patterns: List<DashPattern?> = listOf(null) + specs.mapNotNull { it.dash }.distinct()

    /** On and off lengths in line units per dash index, `null` for a solid line at index 0. */
    val DASH_PATTERNS: Array<Dash?> =
        patterns.map { it?.let { p -> Dash(p.onUnits.toFloat(), p.offUnits.toFloat()) } }.toTypedArray()

    private val dashIndices: IntArray = IntArray(specs.size) { patterns.indexOf(specs[it].dash) }

    private val strokeUnits: FloatArray = FloatArray(specs.size) { specs[it].strokeUnits.toFloat() }

    fun fillColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int = palette.argb(specs[style.ordinal].fill)

    /** Whether a filled polygon of [style] also gets an outline. */
    fun outlined(style: SceneStyle): Boolean = specs[style.ordinal].outlined

    fun strokeColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int = palette.argb(specs[style.ordinal].stroke)

    fun strokeUnits(style: SceneStyle): Float = strokeUnits[style.ordinal]

    fun roundCap(style: SceneStyle): Boolean = specs[style.ordinal].cap == LineCap.ROUND

    /** Index into [DASH_PATTERNS]. */
    fun dashIndex(style: SceneStyle): Int = dashIndices[style.ordinal]

    fun textColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int = palette.argb(specs[style.ordinal].text)
}
