package app.plansnap.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LocalPlanSnapColors = staticCompositionLocalOf { PlanSnapColors.Light }
private val LocalPlanSnapSpacing = staticCompositionLocalOf { PlanSnapSpacing() }
private val LocalPlanSnapSizes = staticCompositionLocalOf { PlanSnapSizes() }

private val PlanSnapShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

/**
 * PlanSnap theme with fixed light and dark color schemes and no Dynamic Color.
 *
 * @param darkTheme Whether to use the dark palette; follows the system setting by default.
 * @param content Content that is styled by the theme.
 */
@Composable
fun PlanSnapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) PlanSnapColors.Dark else PlanSnapColors.Light
    CompositionLocalProvider(
        LocalPlanSnapColors provides colors,
        LocalPlanSnapSpacing provides PlanSnapSpacing(),
        LocalPlanSnapSizes provides PlanSnapSizes(),
    ) {
        MaterialTheme(
            colorScheme = colors.material,
            typography = PlanSnapTypography,
            shapes = PlanSnapShapes,
            content = content,
        )
    }
}

/** Access to the PlanSnap tokens of the surrounding [PlanSnapTheme]. */
object PlanSnapTheme {
    /** Plan and status colors. */
    val colors: PlanSnapColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPlanSnapColors.current

    /** Spacing scale. */
    val spacing: PlanSnapSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalPlanSnapSpacing.current

    /** Size tokens. */
    val sizes: PlanSnapSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalPlanSnapSizes.current
}
