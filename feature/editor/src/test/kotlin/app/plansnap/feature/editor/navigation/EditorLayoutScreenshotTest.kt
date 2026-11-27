package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.feature.editor.rooms.DockDraft
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.SaveStatus
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The portrait editor at 360 x 640 dp and 360 x 740 dp, with and without status card, at 100 and 200 percent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class EditorLayoutScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `h740 plain`() = capture(layoutState(false), 1f, "layout-740.png")

    @Test
    fun `h740 card`() = capture(layoutState(true), 1f, "layout-740-card.png")

    @Test
    fun `h740 plain font 2`() = capture(layoutState(false), 2f, "layout-740-fontscale-2.png")

    @Test
    fun `h740 card font 2`() = capture(layoutState(true), 2f, "layout-740-card-fontscale-2.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 plain`() = capture(layoutState(false), 1f, "layout-640.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 card`() = capture(layoutState(true), 1f, "layout-640-card.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 plain font 2`() = capture(layoutState(false), 2f, "layout-640-fontscale-2.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 card font 2`() = capture(layoutState(true), 2f, "layout-640-card-fontscale-2.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 picking after a failed save`() = capture(pickingAfterFailedSave(), 1f, "layout-640-picking-save.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 picking font 2 after a failed save`() =
        capture(pickingAfterFailedSave(), 2f, "layout-640-picking-save-fontscale-2.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 german picking after a failed save`() =
        capture(pickingAfterFailedSave(), 1f, "layout-640-picking-save-de.png")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `h640 failed save font 2`() = capture(failedSave(), 2f, "layout-640-save-fontscale-2.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h690dp-port-notnight-mdpi")
    fun `h690 german failed save font 2`() = capture(failedSave(), 2f, "layout-690-save-de-fontscale-2.png")

    private fun failedSave(): EditorUiState =
        layoutState(true).copy(save = SaveStatus.Failed(DomainError.Storage(StorageProblem.WRITE_FAILED)))

    private fun pickingAfterFailedSave(): EditorUiState {
        val state = failedSave()
        val dock = DockDraft(guestId = checkNotNull(state.selectedRoomId), picking = true)
        return state.withSection(RoomsState::class, RoomsState(dock = dock))
    }

    private fun capture(
        state: EditorUiState,
        fontScale: Float,
        fileName: String,
    ) {
        compose.setContent { LayoutHost(state, fontScale) }
        compose.onRoot().captureRoboImage(
            filePath = fileName,
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)),
        )
    }
}
