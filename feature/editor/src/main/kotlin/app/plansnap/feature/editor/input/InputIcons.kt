package app.plansnap.feature.editor.input

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2.2f

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

/** Stroked symbols of the editor, tinted by the caller. Every use gives a spoken label. */
internal object InputIcons {
    /** Backspace: a tag shape with a cross. */
    val Backspace: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Backspace", "M21 5 L9 5 L3 12 L9 19 L21 19 Z M12.5 9 L17.5 15 M17.5 9 L12.5 15")
    }

    /** Curved arrow to the left: undo. */
    val Undo: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Undo", "M9 6 L4 11 L9 16 M4 11 L14 11 C18 11 20 13.5 20 18")
    }

    /** Curved arrow to the right: redo. */
    val Redo: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("Redo", "M15 6 L20 11 L15 16 M20 11 L10 11 C6 11 4 13.5 4 18")
    }

    /** Three dots in a column: further actions. */
    val More: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        symbol("More", "M12 5 L12 5.1 M12 12 L12 12.1 M12 19 L12 19.1")
    }
}
