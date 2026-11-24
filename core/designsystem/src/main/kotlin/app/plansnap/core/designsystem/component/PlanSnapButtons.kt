package app.plansnap.core.designsystem.component

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import app.plansnap.core.designsystem.theme.NumericKeyTextStyle
import app.plansnap.core.designsystem.theme.PlanSnapTheme

/**
 * Primary action button, for example `OK`. At least 48 dp high.
 *
 * @param text Visible label, supplied by the caller from string resources.
 * @param onClick Called on tap.
 * @param modifier Modifier for the button.
 * @param enabled Whether the button reacts to input.
 */
@Composable
fun PlanSnapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.minTouchTarget(),
        enabled = enabled,
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

/**
 * Secondary action button with an outline, for example `Left` and `Right`. At least 48 dp high.
 *
 * @param text Visible label, supplied by the caller from string resources.
 * @param onClick Called on tap.
 * @param modifier Modifier for the button.
 * @param enabled Whether the button reacts to input.
 */
@Composable
fun PlanSnapOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minTouchTarget(),
        enabled = enabled,
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

/**
 * Icon-only button with a 48 dp touch target.
 *
 * @param icon Symbol to show.
 * @param contentDescription Spoken label; required because the button has no visible text.
 * @param onClick Called on tap.
 * @param modifier Modifier for the button.
 * @param enabled Whether the button reacts to input.
 */
@Composable
fun PlanSnapIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.minTouchTarget(),
        enabled = enabled,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Large key of the numeric keypad, at least 56 dp in both directions.
 *
 * @param label Visible character or word, for example `7`, `,` or `OK`.
 * @param onClick Called on tap.
 * @param modifier Modifier for the key; pass a weight to let keys share a row.
 * @param contentDescription Spoken label when [label] alone is not meaningful, for example for a backspace symbol.
 * @param enabled Whether the key reacts to input.
 */
@Composable
fun PlanSnapNumericKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val key = PlanSnapTheme.sizes.numericKey
    FilledTonalButton(
        onClick = onClick,
        modifier =
            modifier
                .sizeIn(minWidth = key, minHeight = key)
                .semanticsLabel(contentDescription),
        enabled = enabled,
    ) {
        Text(text = label, style = NumericKeyTextStyle, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Modifier.minTouchTarget(): Modifier {
    val target = PlanSnapTheme.sizes.minTouchTarget
    return sizeIn(minWidth = target, minHeight = target)
}
