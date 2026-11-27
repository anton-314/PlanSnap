package app.plansnap.feature.editor.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.plansnap.feature.editor.R

/**
 * What a screen reader says about the value above the keypad, announced when it changes. The content description
 * replaces the texts below it, so the wall thickness and the hint are spoken as the state of the value.
 *
 * @param value the wall and the typed value.
 * @param thickness the wall thickness as shown.
 * @param unitSpoken spoken name of the unit of [thickness].
 * @param hint the sentence that says what to do, `null` if none is shown.
 */
@Composable
internal fun Modifier.spokenInputValue(
    value: String,
    thickness: String,
    unitSpoken: String,
    hint: String?,
): Modifier {
    val spokenThickness = stringResource(R.string.a11y_input_thickness_spoken, thickness, unitSpoken)
    val state =
        if (hint == null) {
            spokenThickness
        } else {
            stringResource(R.string.a11y_input_state_with_hint, spokenThickness, hint)
        }
    return semantics(mergeDescendants = true) {
        contentDescription = value
        stateDescription = state
        liveRegion = LiveRegionMode.Polite
    }
}
