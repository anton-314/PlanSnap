package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.plansnap.feature.editor.state.EditorUiState
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
