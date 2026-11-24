package app.plansnap.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2.5f

/** Builds a 24 dp stroked symbol from SVG path data; the color is applied by the caller through `tint`. */
private fun symbol(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).addPath(
            pathData = addPathNodes(pathData),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

/** Status symbols drawn as vectors, so the design system needs no icon dependency. Tinted by the caller. */
object PlanSnapIcons {
    /** Check mark: shape confirmed. */
    val Check: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Check", "M4 12.5 L9.5 18 L20 6.5")
    }

    /** Triangle with exclamation mark: shape not confirmed. */
    val Warning: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Warning", "M12 3.5 L21.5 20 L2.5 20 Z M12 10 L12 14.5 M12 17.2 L12 17.3")
    }

    /** Cross: contradictory measurements. */
    val Conflict: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Conflict", "M5 5 L19 19 M19 5 L5 19")
    }

    /** Left-pointing arrow, for example for a back button. */
    val Back: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Back", "M20 12 L4 12 M10 6 L4 12 L10 18")
    }
}
