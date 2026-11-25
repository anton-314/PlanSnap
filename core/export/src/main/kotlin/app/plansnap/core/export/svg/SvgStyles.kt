package app.plansnap.core.export.svg

import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.render.RenderPalette

/** How a [SceneStyle] is drawn, in line units of [SvgStyles.UNIT_MM]. */
internal class SvgLook(
    val fillArgb: Int,
    val strokeArgb: Int,
    val strokeUnits: Double,
    val roundCap: Boolean,
    val dash: List<Double>?,
    val textArgb: Int,
)

/**
 * Mapping of every [SceneStyle] to color, weight, cap and dash. It mirrors the look of `:core:render`, whose table is
 * internal, so the SVG shows the same plan as the other outputs (ADR 0008). The export always uses the light palette.
 */
internal object SvgStyles {
    /** Line unit on paper in millimeters. */
    const val UNIT_MM = 0.18

    /** Diameter of a corner marker in line units. */
    const val MARKER_UNITS = 3.0

    private const val THIN = 1.0
    private const val WALL = 2.0
    private const val HEAVY = 3.0
    private const val ROOM_OUTLINE_UNCONFIRMED = 1.5
    private const val TRANSPARENT = 0
    private val palette = RenderPalette.Light
    private const val SHORT = 2.0
    private const val MEDIUM = 3.0
    private const val FIVE = 5.0
    private const val LONG = 6.0
    private const val GAP = 4.0
    private val dashWallUnconfirmed = listOf(LONG, GAP)
    private val dashDiagonal = listOf(SHORT, MEDIUM)
    private val dashOutOfBounds = listOf(MEDIUM, MEDIUM)
    private val dashRoomUnconfirmed = listOf(FIVE, MEDIUM)

    /** Look of [style]. */
    fun look(style: SceneStyle): SvgLook =
        SvgLook(
            fillArgb = fill(style),
            strokeArgb = stroke(style),
            strokeUnits = weight(style),
            roundCap = style == SceneStyle.WALL_LINE || style == SceneStyle.CONFLICT,
            dash = dash(style),
            textArgb = text(style),
        )

    /** CSS class name of [style], for example `wall-band`. */
    fun className(style: SceneStyle): String = style.name.lowercase().replace('_', '-')

    private fun fill(style: SceneStyle): Int =
        when (style) {
            SceneStyle.WALL_BAND -> palette.wall
            SceneStyle.OPENING, SceneStyle.OPENING_OUT_OF_BOUNDS -> palette.openingFill
            SceneStyle.ROOM_BODY, SceneStyle.ROOM_BODY_UNCONFIRMED -> palette.roomBody
            else -> TRANSPARENT
        }

    private fun stroke(style: SceneStyle): Int =
        when (style) {
            SceneStyle.DIAGONAL, SceneStyle.DIMENSION -> palette.dimension
            SceneStyle.WARNING -> palette.warning
            SceneStyle.OPENING_OUT_OF_BOUNDS, SceneStyle.CONFLICT -> palette.conflict
            else -> palette.wall
        }

    private fun text(style: SceneStyle): Int =
        when (style) {
            SceneStyle.DIMENSION, SceneStyle.DIAGONAL -> palette.dimension
            SceneStyle.WARNING -> palette.warning
            SceneStyle.CONFLICT, SceneStyle.OPENING_OUT_OF_BOUNDS -> palette.conflict
            else -> palette.wall
        }

    private fun weight(style: SceneStyle): Double =
        when (style) {
            SceneStyle.WALL_BAND, SceneStyle.WALL_LINE, SceneStyle.WALL_UNCONFIRMED -> WALL
            SceneStyle.CONFLICT -> HEAVY
            SceneStyle.ROOM_BODY_UNCONFIRMED -> ROOM_OUTLINE_UNCONFIRMED
            else -> THIN
        }

    private fun dash(style: SceneStyle): List<Double>? =
        when (style) {
            SceneStyle.WALL_UNCONFIRMED -> dashWallUnconfirmed
            SceneStyle.DIAGONAL -> dashDiagonal
            SceneStyle.OPENING_OUT_OF_BOUNDS -> dashOutOfBounds
            SceneStyle.ROOM_BODY_UNCONFIRMED -> dashRoomUnconfirmed
            else -> null
        }
}
