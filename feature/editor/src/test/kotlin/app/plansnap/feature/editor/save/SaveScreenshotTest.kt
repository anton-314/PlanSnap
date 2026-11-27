package app.plansnap.feature.editor.save

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.plansnap.core.common.result.DomainError
import app.plansnap.feature.editor.navigation.layoutState
import app.plansnap.feature.editor.state.SaveStatus
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The failed-save message on the whole editor screen of a 360 x 640 dp phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class SaveScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `given a failed save when light then it matches the baseline`() = capture("save-light.png")

    @Test
    fun `given a failed save when dark then it matches the baseline`() = capture("save-dark.png", darkTheme = true)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given German at font scale 2 when a save failed then it matches the baseline`() =
        capture("save-de-fontscale-2.png", fontScale = 2f)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a full device in German when shown then it matches the baseline`() =
        capture("save-de-no-space.png", error = NO_SPACE)

    private fun capture(
        fileName: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        error: DomainError = WRITE_FAILED,
    ) {
        val state = layoutState(withCard = false).copy(save = SaveStatus.Failed(error))
        compose.setContent { SaveEditorHost(state, {}, fontScale, darkTheme) }
        compose.onRoot().captureRoboImage(
            filePath = fileName,
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)),
        )
    }
}
