package app.plansnap.feature.editor.save

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The message about a failed save with the button that stores the changes again (REQ-DATA-002).
 *
 * It appears when [EditorUiState.save] reports a failure and stays, also while a further save is running and across
 * configuration changes, until a save succeeded. It cannot be dismissed. Without an unresolved failure nothing is
 * composed. The button sends [CoreIntent.RetrySave].
 *
 * Place it as an overlay at the top of the plan area, never over the input bar. The host needs a bounded height and
 * takes no more than that: if the text does not fit, it scrolls while the button stays in place.
 *
 * @param state state of the editor.
 * @param onIntent receives the tap on the button.
 * @param modifier modifier of the message, typically its alignment in the plan area.
 */
@Composable
fun SaveHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remembered by rememberSaveable { mutableStateOf<PendingSaveFailure?>(null) }
    val failure = pendingSaveFailure(state.save, remembered)
    SideEffect { remembered = failure }
    if (failure != null) {
        SaveFailureMessage(failure, onRetry = { onIntent(CoreIntent.RetrySave) }, modifier = modifier)
    }
}

/** Symbol, title and explanation above the button, in the error colors, so color is never the only signal. */
@Composable
private fun SaveFailureMessage(
    failure: PendingSaveFailure,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val spacing = PlanSnapTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.s),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = ELEVATION,
    ) {
        Column(modifier = Modifier.padding(start = spacing.m, top = spacing.xs, end = spacing.m)) {
            SaveFailureText(
                failure = failure,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End).heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
                // Less vertical padding than the default: title and button must fit the plan area at 200 percent.
                contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.xs),
            ) {
                Text(text = stringResource(R.string.save_retry), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Announced by screen readers when it appears, without taking the focus away from the input. */
@Composable
private fun SaveFailureText(
    failure: PendingSaveFailure,
    modifier: Modifier,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PlanSnapIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(PlanSnapTheme.sizes.statusIcon),
            )
            Text(text = stringResource(R.string.save_failed_title), style = MaterialTheme.typography.titleSmall)
        }
        Text(text = stringResource(explanationRes(failure)), style = MaterialTheme.typography.bodySmall)
    }
}

private fun explanationRes(failure: PendingSaveFailure): Int =
    when (failure) {
        PendingSaveFailure.GENERAL -> R.string.save_failed_general
        PendingSaveFailure.NO_SPACE -> R.string.save_failed_no_space
    }

private val ELEVATION = 4.dp
