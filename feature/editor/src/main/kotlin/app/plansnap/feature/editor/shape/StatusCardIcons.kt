package app.plansnap.feature.editor.shape

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2.5f

/** Builds a 24 dp stroked chevron from SVG path data; the color is applied by the caller through `tint`. */
private fun chevron(
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

/** Chevrons of the toggle in the header of the status card. */
internal object StatusCardIcons {
    /** Points down: the card is collapsed and opens. */
    val ChevronDown: ImageVector by lazy(LazyThreadSafetyMode.NONE) { chevron("ChevronDown", "M5 9 L12 16 L19 9") }

    /** Points up: the card is expanded and collapses. */
    val ChevronUp: ImageVector by lazy(LazyThreadSafetyMode.NONE) { chevron("ChevronUp", "M5 15 L12 8 L19 15") }
}
