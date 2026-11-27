package app.plansnap.feature.projects.ui.states

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.projects.R

private const val SKELETON_ROWS = 3
private val SKELETON_ROW_HEIGHT = 64.dp

/** Loading state of the project list (docs/UX.md section 1): a skeleton of placeholder rows with a spoken label. */
@Composable
internal fun ProjectsLoadingState() {
    val loading = stringResource(R.string.projects_loading)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(PlanSnapTheme.spacing.l)
                .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m),
    ) {
        repeat(SKELETON_ROWS) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(SKELETON_ROW_HEIGHT),
                shape = RoundedCornerShape(PlanSnapTheme.spacing.s),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {}
        }
    }
}

/**
 * Empty state of the project list: explains what the app does. The button that starts a new project belongs to the
 * screen and stays below this state.
 */
@Composable
internal fun ProjectsEmptyState() {
    CenteredState {
        StateText(R.string.projects_empty_explanation)
    }
}

/**
 * Error state of the project list.
 *
 * @param message explanation of the failure.
 * @param onRetry loads the list again.
 */
@Composable
internal fun ProjectsFailedState(
    @StringRes message: Int,
    onRetry: () -> Unit,
) {
    CenteredState {
        StateText(message)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.projects_retry)) }
    }
}

/** Centers [content] in the available space and lets it scroll when a small window or large font does not fit it. */
@Composable
private fun CenteredState(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun StateText(
    @StringRes text: Int,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}
