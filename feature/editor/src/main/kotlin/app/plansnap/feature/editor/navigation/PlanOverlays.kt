package app.plansnap.feature.editor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.plansnap.feature.editor.rooms.RoomsHost
import app.plansnap.feature.editor.save.SaveHost
import app.plansnap.feature.editor.save.saveMessageMinHeight
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * The surfaces above the plan and the status card, stacked from the top: the bar of the rooms function and below it
 * the message about a failed save, so the message never covers the bar. The message ends at [planHeight], the lower
 * edge of the plan, as long as its title and button fit above it. Otherwise it covers as much of the status card as
 * they need. A bar that fills the whole area leaves the message no room until the bar is closed.
 */
@Composable
internal fun PlanOverlays(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    planHeight: Dp,
) {
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
    val saveMaxHeight = (planHeight - barHeight).coerceAtLeast(saveMessageMinHeight())
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().onSizeChanged { barHeightPx = it.height }) {
            RoomsHost(state, onIntent, Modifier.align(Alignment.TopCenter))
        }
        SaveHost(state, onIntent, Modifier.heightIn(max = saveMaxHeight))
    }
}
