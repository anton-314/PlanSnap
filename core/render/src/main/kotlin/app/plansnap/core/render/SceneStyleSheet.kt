package app.plansnap.core.render

import app.plansnap.core.drawing.scene.SceneStyle

/**
 * Color role of the plan, resolved to ARGB by a [RenderPalette] with [argb]. A style names roles, not colors, so one
 * table serves the light and the dark palette.
 */
enum class PlanInk {
    /** Fully transparent, nothing is painted. */
    NONE,

    /** [RenderPalette.wall]. */
    WALL,

    /** [RenderPalette.roomBody]. */
    ROOM_BODY,

    /** [RenderPalette.openingFill]. */
    OPENING_FILL,

    /** [RenderPalette.dimension]. */
    DIMENSION,

    /** [RenderPalette.warning]. */
    WARNING,

    /** [RenderPalette.conflict]. */
    CONFLICT,
}

/** ARGB value of [ink] in this palette, `0` (transparent) for [PlanInk.NONE]. */
fun RenderPalette.argb(ink: PlanInk): Int =
    when (ink) {
        PlanInk.NONE -> SceneStyleSheet.TRANSPARENT_ARGB
        PlanInk.WALL -> wall
        PlanInk.ROOM_BODY -> roomBody
        PlanInk.OPENING_FILL -> openingFill
        PlanInk.DIMENSION -> dimension
        PlanInk.WARNING -> warning
        PlanInk.CONFLICT -> conflict
    }

/** End of an open stroke. */
enum class LineCap {
    /** Square end exactly at the end point. */
    BUTT,

    /** Half circle around the end point. */
    ROUND,
}

/**
 * Dash pattern in line units, starting with a dash at phase zero.
 *
 * @property onUnits length of a dash, positive.
 * @property offUnits length of a gap, positive.
 */
data class DashPattern(
    val onUnits: Double,
    val offUnits: Double,
) {
    init {
        require(onUnits > 0.0 && offUnits > 0.0) { "Dash and gap must be positive" }
    }
}

/**
 * How one [SceneStyle] is drawn in every output (ADR 0008, ADR 0022). Weights and dashes are in line units, see
 * [SceneStyleSheet.PAPER_LINE_UNIT_MM] and [SceneStyleSheet.EDITOR_LINE_UNIT_DP].
 *
 * @property fill fill of a `Fill` element, [PlanInk.NONE] for an unfilled one.
 * @property outlined whether a `Fill` element also gets a stroke along its polygon.
 * @property stroke color of strokes: polylines, arcs, outlines and corner markers.
 * @property strokeUnits stroke weight in line units, positive.
 * @property cap end of open strokes.
 * @property dash dash pattern of strokes, `null` for a solid line.
 * @property text color of a `Label` element.
 */
data class SceneStyleSpec(
    val fill: PlanInk,
    val outlined: Boolean,
    val stroke: PlanInk,
    val strokeUnits: Double,
    val cap: LineCap,
    val dash: DashPattern?,
    val text: PlanInk,
) {
    init {
        require(strokeUnits > 0.0 && strokeUnits.isFinite()) { "Stroke weight must be positive" }
        require(stroke != PlanInk.NONE && text != PlanInk.NONE) { "Strokes and text must be visible" }
    }
}

/**
 * The single source of the plan look (ADR 0022): every [SceneStyle] mapped to a [SceneStyleSpec], plus the page
 * constants every export shares. The editor renderer, the PDF and PNG exports (through the same renderer) and the SVG
 * writer read only this table, so they cannot drift apart.
 *
 * The table is free of Android types. Status is never conveyed by color alone (UX section 6): unconfirmed shapes are
 * dashed, conflicts are heavier than walls, openings out of bounds are dashed, and their labels carry the text marks.
 */
object SceneStyleSheet {
    /** Line unit on paper in millimeters (PDF, PNG and SVG). */
    const val PAPER_LINE_UNIT_MM: Double = 0.18

    /** Line unit of the editor in dp, scaled by the display density. */
    const val EDITOR_LINE_UNIT_DP: Double = 1.0

    /** Diameter of the dot that marks a corner, in line units, painted in the stroke color. */
    const val MARKER_UNITS: Double = 3.0

    /** ARGB of a fully transparent color. */
    const val TRANSPARENT_ARGB: Int = 0

    /** Background of an exported page and of the light segments of the scale bar: opaque white. */
    const val PAGE_BACKGROUND_ARGB: Int = 0xFFFFFFFF.toInt()

    /** Ink of the title block: frame, texts and the dark segments of the scale bar. */
    val TITLE_BLOCK_INK: PlanInk = PlanInk.WALL

    /** Palette of every export (UX section 6). */
    val EXPORT_PALETTE: RenderPalette = RenderPalette.Light

    private const val THIN = 1.0
    private const val WALL = 2.0
    private const val HEAVY = 3.0
    private const val ROOM_OUTLINE_UNCONFIRMED = 1.5

    private val DASH_WALL_UNCONFIRMED = DashPattern(onUnits = 6.0, offUnits = 4.0)
    private val DASH_DIAGONAL = DashPattern(onUnits = 2.0, offUnits = 3.0)
    private val DASH_OUT_OF_BOUNDS = DashPattern(onUnits = 3.0, offUnits = 3.0)
    private val DASH_ROOM_UNCONFIRMED = DashPattern(onUnits = 5.0, offUnits = 3.0)

    private val specs: List<SceneStyleSpec> = SceneStyle.entries.map(::define)

    /** Look of [style]. Allocation free. */
    fun spec(style: SceneStyle): SceneStyleSpec = specs[style.ordinal]

    /** CSS class name of [style] in SVG, for example `wall-band`. */
    fun className(style: SceneStyle): String = style.name.lowercase().replace('_', '-')

    /** Exhaustive `when` without `else`: a new style fails to compile until it has a look. */
    private fun define(style: SceneStyle): SceneStyleSpec =
        when (style) {
            SceneStyle.WALL_BAND -> {
                spec(fill = PlanInk.WALL, outlined = false, stroke = PlanInk.WALL, units = WALL)
            }

            SceneStyle.WALL_LINE -> {
                spec(stroke = PlanInk.WALL, units = WALL, cap = LineCap.ROUND)
            }

            SceneStyle.WALL_UNCONFIRMED -> {
                spec(stroke = PlanInk.WALL, units = WALL, dash = DASH_WALL_UNCONFIRMED)
            }

            SceneStyle.OPENING -> {
                spec(fill = PlanInk.OPENING_FILL, stroke = PlanInk.WALL, units = THIN)
            }

            SceneStyle.OPENING_OUT_OF_BOUNDS -> {
                spec(
                    fill = PlanInk.OPENING_FILL,
                    stroke = PlanInk.CONFLICT,
                    units = THIN,
                    dash = DASH_OUT_OF_BOUNDS,
                    text = PlanInk.CONFLICT,
                )
            }

            SceneStyle.DIAGONAL -> {
                spec(stroke = PlanInk.DIMENSION, units = THIN, dash = DASH_DIAGONAL, text = PlanInk.DIMENSION)
            }

            SceneStyle.DIMENSION -> {
                spec(stroke = PlanInk.DIMENSION, units = THIN, text = PlanInk.DIMENSION)
            }

            SceneStyle.LABEL -> {
                spec(stroke = PlanInk.WALL, units = THIN)
            }

            SceneStyle.WARNING -> {
                spec(stroke = PlanInk.WARNING, units = THIN, text = PlanInk.WARNING)
            }

            SceneStyle.CONFLICT -> {
                spec(stroke = PlanInk.CONFLICT, units = HEAVY, cap = LineCap.ROUND, text = PlanInk.CONFLICT)
            }

            SceneStyle.ROOM_BODY -> {
                spec(fill = PlanInk.ROOM_BODY, stroke = PlanInk.WALL, units = THIN)
            }

            SceneStyle.ROOM_BODY_UNCONFIRMED -> {
                spec(
                    fill = PlanInk.ROOM_BODY,
                    stroke = PlanInk.WALL,
                    units = ROOM_OUTLINE_UNCONFIRMED,
                    dash = DASH_ROOM_UNCONFIRMED,
                )
            }
        }

    @Suppress("LongParameterList") // Named defaults per SceneStyleSpec field keep the table readable.
    private fun spec(
        stroke: PlanInk,
        units: Double,
        fill: PlanInk = PlanInk.NONE,
        outlined: Boolean = true,
        cap: LineCap = LineCap.BUTT,
        dash: DashPattern? = null,
        text: PlanInk = PlanInk.WALL,
    ) = SceneStyleSpec(fill, outlined, stroke, units, cap, dash, text)
}
