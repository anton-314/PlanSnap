package app.plansnap.feature.editor.hints

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.save.PendingSaveFailure
import app.plansnap.feature.editor.save.pendingSaveFailure
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/** Lowest area that holds the hint: the close button of 48 dp with the padding around the hint. */
internal val HINT_MIN_HEIGHT = 56.dp

private val ELEVATION = 4.dp

/**
 * The one-time hint of the current editing step with its close button (UX section 5, REQ-UX-006). At most one hint is
 * shown. Closing it sends [HintsIntent.Dismiss].
 *
 * Place it as an overlay at the top of a `Box` that holds the plan canvas only, so it can cover neither the status
 * card nor the input bar. It never grows beyond that area; a long text scrolls while the close button stays in place.
 * In an area lower than 56 dp nothing is shown and the hint stays open for later.
 *
 * It also yields to the message about a failed save for as long as that is unresolved, and to the selection of a dock
 * host on the plan. The composable emits nothing without a hint, so it can stay in the screen permanently.
 *
 * @param state state of the editor.
 * @param onIntent receives the tap on the close button.
 * @param modifier modifier of the hint, typically its alignment in the plan area.
 */
@Composable
fun HintsHost(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remembered by rememberSaveable { mutableStateOf<PendingSaveFailure?>(null) }
    val failure = pendingSaveFailure(state.save, remembered)
    SideEffect { remembered = failure }
    val hint = activeHint(state)
    if (hint == null || failure != null) return
    BoxWithConstraints(modifier = modifier) {
        if (maxHeight >= HINT_MIN_HEIGHT) {
            // A new hint starts at the top of its text.
            key(hint) {
                HintCard(hint, onClose = { onIntent(HintsIntent.Dismiss(hint)) })
            }
        }
    }
}

/** Text and close button; the text starts with the word "hint", so the kind of message does not rely on color. */
@Composable
private fun HintCard(
    hint: EditorHint,
    onClose: () -> Unit,
) {
    val spacing = PlanSnapTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.s, vertical = spacing.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = ELEVATION,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = spacing.m, top = spacing.m, bottom = spacing.s),
            ) {
                Text(
                    text = stringResource(textRes(hint)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PlanSnapIconButton(
                icon = PlanSnapIcons.Conflict,
                contentDescription = stringResource(R.string.hints_close),
                onClick = onClose,
            )
        }
    }
}

private fun textRes(hint: EditorHint): Int =
    when (hint) {
        EditorHint.NUMPAD -> R.string.hints_numpad
        EditorHint.DIRECTION -> R.string.hints_direction
        EditorHint.CLOSE -> R.string.hints_close_room
        EditorHint.DIAGONAL -> R.string.hints_diagonal
        EditorHint.ADJUSTMENT -> R.string.hints_adjustment
    }
