package app.plansnap.feature.editor.a11y

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val FITTING_LINE_HEIGHT = 1.15.em
private val FITTING_STEP = 1.sp

/** Size of small body text; at the normal font size no word becomes smaller than this. */
private val SMALLEST_SIZE = 14.sp

/**
 * The word on a button that shares its row with others. It has the size of [style] where that fits. Where it does not,
 * on a narrow window or with a large font, it shrinks until it fits on one line instead of breaking in the middle of
 * the word. It never becomes smaller than small body text at the normal font size (UX section 7).
 *
 * @param text the word.
 * @param style style of the text with a font size in sp, by default the style of the surrounding button.
 */
@Composable
internal fun FittingText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val smallest = if (style.fontSize.value < SMALLEST_SIZE.value) style.fontSize else SMALLEST_SIZE
    Text(
        text = text,
        // A fixed line height would stay high while the letters shrink.
        style = if (scale > 1f) style.copy(lineHeight = FITTING_LINE_HEIGHT) else style,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = smallest / scale,
                maxFontSize = style.fontSize,
                stepSize = FITTING_STEP,
            ),
    )
}
