package app.plansnap.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Fixed PlanSnap color tokens (docs/UX.md section 6). There is deliberately no Dynamic Color: the blueprint accent
 * and the plan palette must look the same on every device.
 *
 * [material] feeds the Material 3 components; the remaining fields are the plan and status tokens that Material 3
 * has no role for. Status is never conveyed by color alone, so every status color is used together with a symbol
 * and a text (see `PlanSnapStatusBadge`).
 *
 * @property material Material 3 scheme derived from the same tokens.
 * @property planWall Wall strokes in the plan.
 * @property planGrid Background grid; decorative, therefore exempt from the contrast requirement.
 * @property planDimension Dimension lines and dimension text.
 * @property planHighlight Selection and emphasis in the plan.
 * @property warning Warning state, for example "shape not confirmed".
 * @property success Success state, for example "closed and exact".
 * @property conflict Contradictory or impossible measurements.
 * @property isDark Whether this is the dark palette.
 */
@Immutable
data class PlanSnapColors(
    val material: ColorScheme,
    val planWall: Color,
    val planGrid: Color,
    val planDimension: Color,
    val planHighlight: Color,
    val warning: Color,
    val success: Color,
    val conflict: Color,
    val isDark: Boolean,
) {
    companion object {
        /** Light palette. */
        val Light: PlanSnapColors =
            PlanSnapColors(
                material =
                    lightColorScheme(
                        primary = Color(0xFF1B5FAA),
                        onPrimary = Color(0xFFFFFFFF),
                        primaryContainer = Color(0xFFD6E3FF),
                        onPrimaryContainer = Color(0xFF001B3E),
                        secondary = Color(0xFF555F71),
                        onSecondary = Color(0xFFFFFFFF),
                        secondaryContainer = Color(0xFFD9E3F8),
                        onSecondaryContainer = Color(0xFF121C2B),
                        tertiary = Color(0xFF17693A),
                        onTertiary = Color(0xFFFFFFFF),
                        background = Color(0xFFF8F9FC),
                        onBackground = Color(0xFF191C20),
                        surface = Color(0xFFF8F9FC),
                        onSurface = Color(0xFF191C20),
                        surfaceVariant = Color(0xFFE0E2EC),
                        onSurfaceVariant = Color(0xFF44474E),
                        outline = Color(0xFF6B6E76),
                        outlineVariant = Color(0xFFC4C6D0),
                        error = Color(0xFFBA1A1A),
                        onError = Color(0xFFFFFFFF),
                        errorContainer = Color(0xFFFFDAD6),
                        onErrorContainer = Color(0xFF410002),
                        surfaceContainerLowest = Color(0xFFFFFFFF),
                        surfaceContainerLow = Color(0xFFF2F3F7),
                        surfaceContainer = Color(0xFFECEDF1),
                        surfaceContainerHigh = Color(0xFFE6E8EB),
                        surfaceContainerHighest = Color(0xFFE1E2E6),
                    ),
                planWall = Color(0xFF1F2A37),
                planGrid = Color(0xFFDDE3EC),
                planDimension = Color(0xFF1B5FAA),
                planHighlight = Color(0xFF006A6A),
                warning = Color(0xFF8A5000),
                success = Color(0xFF17693A),
                conflict = Color(0xFFBA1A1A),
                isDark = false,
            )

        /** Dark palette. */
        val Dark: PlanSnapColors =
            PlanSnapColors(
                material =
                    darkColorScheme(
                        primary = Color(0xFFA6C8FF),
                        onPrimary = Color(0xFF003060),
                        primaryContainer = Color(0xFF00468A),
                        onPrimaryContainer = Color(0xFFD6E3FF),
                        secondary = Color(0xFFBDC7DC),
                        onSecondary = Color(0xFF273141),
                        secondaryContainer = Color(0xFF3D4758),
                        onSecondaryContainer = Color(0xFFD9E3F8),
                        tertiary = Color(0xFF7FDB9F),
                        onTertiary = Color(0xFF00391D),
                        background = Color(0xFF11141A),
                        onBackground = Color(0xFFE2E2E9),
                        surface = Color(0xFF11141A),
                        onSurface = Color(0xFFE2E2E9),
                        surfaceVariant = Color(0xFF44474E),
                        onSurfaceVariant = Color(0xFFC4C6D0),
                        outline = Color(0xFF8E9099),
                        outlineVariant = Color(0xFF44474E),
                        error = Color(0xFFFFB4AB),
                        onError = Color(0xFF690005),
                        errorContainer = Color(0xFF93000A),
                        onErrorContainer = Color(0xFFFFDAD6),
                        surfaceContainerLowest = Color(0xFF0C0E13),
                        surfaceContainerLow = Color(0xFF191C22),
                        surfaceContainer = Color(0xFF1D2026),
                        surfaceContainerHigh = Color(0xFF272A31),
                        surfaceContainerHighest = Color(0xFF32353C),
                    ),
                planWall = Color(0xFFDCE3EC),
                planGrid = Color(0xFF2A313B),
                planDimension = Color(0xFFA6C8FF),
                planHighlight = Color(0xFF4DD9D9),
                warning = Color(0xFFFFB95C),
                success = Color(0xFF7FDB9F),
                conflict = Color(0xFFFFB4AB),
                isDark = true,
            )
    }
}
