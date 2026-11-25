package app.plansnap.feature.editor.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.plansnap.core.designsystem.theme.PlanSnapColors
import app.plansnap.core.render.RenderPalette

/** Opacity of the room interior tint, the same 15 percent as `RenderPalette.Light` and `RenderPalette.Dark`. */
private const val ROOM_BODY_ALPHA = 0x26 / 255f

/**
 * Maps the plan tokens of the theme (UX section 6) to the ARGB palette of `:core:render`, which must not depend on
 * `:core:designsystem`.
 *
 * @param surface plan background, which also fills the opening symbols.
 */
internal fun PlanSnapColors.toRenderPalette(surface: Color): RenderPalette =
    RenderPalette(
        wall = planWall.toArgb(),
        roomBody = planDimension.copy(alpha = ROOM_BODY_ALPHA).toArgb(),
        openingFill = surface.toArgb(),
        dimension = planDimension.toArgb(),
        warning = warning.toArgb(),
        conflict = conflict.toArgb(),
    )
