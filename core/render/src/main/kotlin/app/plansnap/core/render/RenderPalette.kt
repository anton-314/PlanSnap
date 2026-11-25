package app.plansnap.core.render

/**
 * Colors of the plan drawing as ARGB integers.
 *
 * `:core:render` must not depend on `:core:designsystem` (ARCHITECTURE section 2), so the caller passes the plan tokens
 * of docs/UX.md section 6 in this object. [Light] and [Dark] carry the values of those tokens; the export always uses
 * [Light] (UX section 6). Status is never conveyed by color alone: the renderer additionally varies dash patterns and
 * line weights and the scene carries the text marks.
 *
 * @property wall wall bands, wall lines, opening symbols, room outlines and label text (`planWall`).
 * @property roomBody translucent interior tint of a room.
 * @property openingFill fill of opening symbols, the plan background (`surface`).
 * @property dimension dimension lines, diagonals and dimension text (`planDimension`).
 * @property warning warning marks and unconfirmed-shape text (`warning`).
 * @property conflict conflict marks and openings out of bounds (`error`).
 */
class RenderPalette(
    val wall: Int,
    val roomBody: Int,
    val openingFill: Int,
    val dimension: Int,
    val warning: Int,
    val conflict: Int,
) {
    /** Default palettes taken from the design tokens. */
    companion object {
        /** Light plan palette, also used by every export. */
        val Light: RenderPalette =
            RenderPalette(
                wall = 0xFF1F2A37.toInt(),
                roomBody = 0x261B5FAA,
                openingFill = 0xFFF8F9FC.toInt(),
                dimension = 0xFF1B5FAA.toInt(),
                warning = 0xFF8A5000.toInt(),
                conflict = 0xFFBA1A1A.toInt(),
            )

        /** Dark plan palette for the editor. */
        val Dark: RenderPalette =
            RenderPalette(
                wall = 0xFFDCE3EC.toInt(),
                roomBody = 0x26A6C8FF,
                openingFill = 0xFF11141A.toInt(),
                dimension = 0xFFA6C8FF.toInt(),
                warning = 0xFFFFB95C.toInt(),
                conflict = 0xFFFFB4AB.toInt(),
            )
    }
}
