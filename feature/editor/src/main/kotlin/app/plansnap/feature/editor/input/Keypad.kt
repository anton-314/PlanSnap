package app.plansnap.feature.editor.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.a11y.FittingText

private const val KEYPAD_ROWS = 3
private const val KEYS_PER_ROW = 3
private const val FIRST_ROW_DIGIT = 7
private val KEY_TEXT_STYLE =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
private val KEY_PADDING = PaddingValues(horizontal = 4.dp, vertical = 4.dp)

/**
 * A further key in the right column of the keypad, for example the wall thickness or a diagonal. The keypad shows
 * at most two of them. The sub-functions of the editor contribute them, the keypad does not know what they do.
 *
 * @property label visible text of the key.
 * @property contentDescription spoken label of the key.
 * @property onClick called on tap.
 */
@Immutable
data class ExtraKey(
    val label: String,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Taps on the keypad.
 *
 * @property onKey called for a key that edits the input text.
 * @property onConfirm called for `OK`.
 * @property confirmDescription spoken label of `OK` if it confirms something other than a wall, `null` for a wall.
 */
internal class KeypadActions(
    val onKey: (KeypadKey) -> Unit,
    val onConfirm: () -> Unit,
    val confirmDescription: String? = null,
)

/**
 * The numeric keypad of UX section 2: digits, decimal separator, delete and confirm. Every key is at least 56 dp
 * high and wide and has no system keyboard behind it.
 *
 * @param separator decimal separator of the device language.
 * @param enabled whether the keys react; `false` while no wall can be added.
 * @param canConfirm whether `OK` is available, which needs a valid input.
 * @param extraKeys up to two further keys in the right column, between delete and `OK`.
 * @param actions receives the taps.
 */
@Composable
internal fun Keypad(
    separator: Char,
    enabled: Boolean,
    canConfirm: Boolean,
    extraKeys: List<ExtraKey>,
    actions: KeypadActions,
) {
    val onKey = actions.onKey
    val haptic = LocalHapticFeedback.current
    val gap = PlanSnapTheme.spacing.s
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until KEYPAD_ROWS) {
            KeyRow(gap) {
                for (column in 0 until KEYS_PER_ROW) {
                    val digit = FIRST_ROW_DIGIT - row * KEYS_PER_ROW + column
                    DigitKey(digit, enabled, onKey)
                }
                when (row) {
                    0 -> BackspaceKey(enabled) { onKey(KeypadKey.Backspace) }
                    else -> ExtraKeyCell(extraKeys.getOrNull(row - 1), enabled)
                }
            }
        }
        KeyRow(gap) {
            SeparatorKey(separator, enabled) { onKey(KeypadKey.Separator) }
            DigitKey(0, enabled, onKey)
            ConfirmKey(
                description = actions.confirmDescription ?: stringResource(R.string.input_key_confirm),
                enabled = enabled && canConfirm,
                modifier = Modifier.weight(2f).fillMaxHeight(),
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                actions.onConfirm()
            }
        }
    }
}

@Composable
private fun KeyRow(
    gap: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(
    digit: Int,
    enabled: Boolean,
    onKey: (KeypadKey) -> Unit,
) {
    Key(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        enabled = enabled,
        onClick = { onKey(KeypadKey.Digit(digit)) },
    ) { KeyLabel(digit.toString()) }
}

@Composable
private fun RowScope.SeparatorKey(
    separator: Char,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description =
        stringResource(if (separator == ',') R.string.input_key_comma else R.string.input_key_point)
    Key(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        enabled = enabled,
        description = description,
        onClick = onClick,
    ) { KeyLabel(separator.toString()) }
}

@Composable
private fun RowScope.BackspaceKey(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Key(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        enabled = enabled,
        description = stringResource(R.string.input_key_backspace),
        onClick = onClick,
    ) { Icon(imageVector = InputIcons.Backspace, contentDescription = null) }
}

@Composable
private fun RowScope.ExtraKeyCell(
    key: ExtraKey?,
    enabled: Boolean,
) {
    if (key == null) {
        Spacer(Modifier.weight(1f))
    } else {
        Key(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = enabled,
            description = key.contentDescription,
            onClick = key.onClick,
        ) { KeyLabel(key.label, shrinkToFit = true) }
    }
}

@Composable
private fun ConfirmKey(
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .sizeIn(minWidth = PlanSnapTheme.sizes.numericKey, minHeight = PlanSnapTheme.sizes.numericKey)
                .semantics { contentDescription = description },
        contentPadding = KEY_PADDING,
    ) { KeyLabel(stringResource(R.string.input_key_confirm_label)) }
}

@Composable
private fun Key(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val size = PlanSnapTheme.sizes.numericKey
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .sizeIn(minWidth = size, minHeight = size)
                .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
        contentPadding = KEY_PADDING,
        content = content,
    )
}

@Composable
private fun KeyLabel(
    text: String,
    shrinkToFit: Boolean = false,
) {
    // A word such as "Diag" does not always fit a key at 200 percent; "D" has room and scales like the digits.
    if (shrinkToFit) {
        FittingText(text, KEY_TEXT_STYLE)
    } else {
        Text(text = text, style = KEY_TEXT_STYLE, textAlign = TextAlign.Center)
    }
}
