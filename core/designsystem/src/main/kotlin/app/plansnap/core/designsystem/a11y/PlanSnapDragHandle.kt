package app.plansnap.core.designsystem.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.R

private val TARGET_WIDTH = 64.dp
private val BAR_WIDTH = 32.dp
private val BAR_HEIGHT = 4.dp
private val BAR_PADDING = 22.dp

/**
 * Drag handle of a bottom sheet that looks like the Material one but is accessible: the control that the sheet puts
 * around its handle is 48 dp high and wider than 48 dp, and it has a spoken label (REQ-UX-008). The Material default
 * is only 32 dp wide and leaves that control without a label. Pass it as `dragHandle` of every bottom sheet.
 */
@Composable
fun PlanSnapDragHandle() {
    val label = stringResource(R.string.designsystem_a11y_drag_handle)
    Box(
        // No merging semantics here, so the label belongs to the control the sheet puts around the handle.
        modifier = Modifier.width(TARGET_WIDTH).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(vertical = BAR_PADDING),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Box(Modifier.size(width = BAR_WIDTH, height = BAR_HEIGHT))
        }
    }
}
