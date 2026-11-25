package app.plansnap.core.render

import android.graphics.Color
import app.plansnap.core.drawing.scene.SceneStyle

/** Dash pattern in line units. */
internal class Dash(
    val on: Float,
    val off: Float,
)

/**
 * The look of every [SceneStyle]: the single mapping from semantic style to color, line weight, cap and dash. Each
 * function is an exhaustive `when` without `else`, so a new style fails to compile until it has a look. Weights are
 * in line units (see [SceneRenderer]).
 *
 * Status is never conveyed by color alone (UX section 6): unconfirmed shapes are dashed, conflicts are heavier than
 * walls, openings out of bounds are dashed, and their labels carry the text marks.
 */
internal object StyleLook {
    /** Diameter of the dot that marks a corner, in line units. */
    const val MARKER_UNITS = 3f

    private const val THIN = 1f
    private const val WALL = 2f
    private const val HEAVY = 3f
    private const val ROOM_OUTLINE_UNCONFIRMED = 1.5f

    private const val DASH_NONE = 0
    private const val DASH_WALL_UNCONFIRMED = 1
    private const val DASH_DIAGONAL = 2
    private const val DASH_OUT_OF_BOUNDS = 3
    private const val DASH_ROOM_UNCONFIRMED = 4

    /** On and off lengths in line units per dash index, `null` for a solid line. */
    val DASH_PATTERNS: Array<Dash?> =
        arrayOf(
            null,
            Dash(on = 6f, off = 4f),
            Dash(on = 2f, off = 3f),
            Dash(on = 3f, off = 3f),
            Dash(on = 5f, off = 3f),
        )

    fun fillColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int =
        when (style) {
            SceneStyle.WALL_BAND -> palette.wall

            SceneStyle.OPENING, SceneStyle.OPENING_OUT_OF_BOUNDS -> palette.openingFill

            SceneStyle.ROOM_BODY, SceneStyle.ROOM_BODY_UNCONFIRMED -> palette.roomBody

            SceneStyle.WALL_LINE,
            SceneStyle.WALL_UNCONFIRMED,
            SceneStyle.DIAGONAL,
            SceneStyle.DIMENSION,
            SceneStyle.LABEL,
            SceneStyle.WARNING,
            SceneStyle.CONFLICT,
            -> Color.TRANSPARENT
        }

    /** Whether a filled polygon of [style] also gets an outline. */
    fun outlined(style: SceneStyle): Boolean =
        when (style) {
            SceneStyle.WALL_BAND -> false

            SceneStyle.OPENING,
            SceneStyle.OPENING_OUT_OF_BOUNDS,
            SceneStyle.ROOM_BODY,
            SceneStyle.ROOM_BODY_UNCONFIRMED,
            SceneStyle.WALL_LINE,
            SceneStyle.WALL_UNCONFIRMED,
            SceneStyle.DIAGONAL,
            SceneStyle.DIMENSION,
            SceneStyle.LABEL,
            SceneStyle.WARNING,
            SceneStyle.CONFLICT,
            -> true
        }

    fun strokeColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int =
        when (style) {
            SceneStyle.WALL_BAND,
            SceneStyle.WALL_LINE,
            SceneStyle.WALL_UNCONFIRMED,
            SceneStyle.OPENING,
            SceneStyle.ROOM_BODY,
            SceneStyle.ROOM_BODY_UNCONFIRMED,
            -> palette.wall

            SceneStyle.DIAGONAL, SceneStyle.DIMENSION -> palette.dimension

            SceneStyle.LABEL -> palette.wall

            SceneStyle.WARNING -> palette.warning

            SceneStyle.OPENING_OUT_OF_BOUNDS, SceneStyle.CONFLICT -> palette.conflict
        }

    fun strokeUnits(style: SceneStyle): Float =
        when (style) {
            SceneStyle.WALL_BAND,
            SceneStyle.WALL_LINE,
            SceneStyle.WALL_UNCONFIRMED,
            -> WALL

            SceneStyle.CONFLICT -> HEAVY

            SceneStyle.ROOM_BODY_UNCONFIRMED -> ROOM_OUTLINE_UNCONFIRMED

            SceneStyle.OPENING,
            SceneStyle.OPENING_OUT_OF_BOUNDS,
            SceneStyle.DIAGONAL,
            SceneStyle.DIMENSION,
            SceneStyle.LABEL,
            SceneStyle.WARNING,
            SceneStyle.ROOM_BODY,
            -> THIN
        }

    /** Round caps only for solid wall lines, so corner markers and joints look closed. */
    fun roundCap(style: SceneStyle): Boolean =
        when (style) {
            SceneStyle.WALL_LINE, SceneStyle.CONFLICT -> true

            SceneStyle.WALL_BAND,
            SceneStyle.WALL_UNCONFIRMED,
            SceneStyle.OPENING,
            SceneStyle.OPENING_OUT_OF_BOUNDS,
            SceneStyle.DIAGONAL,
            SceneStyle.DIMENSION,
            SceneStyle.LABEL,
            SceneStyle.WARNING,
            SceneStyle.ROOM_BODY,
            SceneStyle.ROOM_BODY_UNCONFIRMED,
            -> false
        }

    /** Index into [DASH_PATTERNS]. */
    fun dashIndex(style: SceneStyle): Int =
        when (style) {
            SceneStyle.WALL_UNCONFIRMED -> DASH_WALL_UNCONFIRMED

            SceneStyle.DIAGONAL -> DASH_DIAGONAL

            SceneStyle.OPENING_OUT_OF_BOUNDS -> DASH_OUT_OF_BOUNDS

            SceneStyle.ROOM_BODY_UNCONFIRMED -> DASH_ROOM_UNCONFIRMED

            SceneStyle.WALL_BAND,
            SceneStyle.WALL_LINE,
            SceneStyle.OPENING,
            SceneStyle.DIMENSION,
            SceneStyle.LABEL,
            SceneStyle.WARNING,
            SceneStyle.CONFLICT,
            SceneStyle.ROOM_BODY,
            -> DASH_NONE
        }

    fun textColor(
        style: SceneStyle,
        palette: RenderPalette,
    ): Int =
        when (style) {
            SceneStyle.DIMENSION, SceneStyle.DIAGONAL -> palette.dimension

            SceneStyle.WARNING -> palette.warning

            SceneStyle.CONFLICT, SceneStyle.OPENING_OUT_OF_BOUNDS -> palette.conflict

            SceneStyle.LABEL,
            SceneStyle.WALL_BAND,
            SceneStyle.WALL_LINE,
            SceneStyle.WALL_UNCONFIRMED,
            SceneStyle.OPENING,
            SceneStyle.ROOM_BODY,
            SceneStyle.ROOM_BODY_UNCONFIRMED,
            -> palette.wall
        }
}
