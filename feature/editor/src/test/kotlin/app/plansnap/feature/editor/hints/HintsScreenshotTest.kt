package app.plansnap.feature.editor.hints

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hints on the whole editor screen of a 360 x 640 dp phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class HintsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `given a first start when light then it matches the baseline`() = capture("hints-numpad-light.png")

    @Test
    fun `given a first start when dark then it matches the baseline`() =
        capture("hints-numpad-dark.png", darkTheme = true)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given three walls in German when shown then it matches the baseline`() =
        capture("hints-de-close.png", Situation.THREE_WALLS)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a gap in German when shown then it matches the baseline`() =
        capture("hints-de-adjustment.png", Situation.GAP)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given German at font scale 2 when shown then it matches the baseline`() =
        capture("hints-de-fontscale-2.png", fontScale = 2f)

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a gap in German at font scale 2 when shown then it matches the baseline`() =
        capture("hints-de-adjustment-fontscale-2.png", Situation.GAP, fontScale = 2f)

    private fun capture(
        fileName: String,
        situation: Situation = Situation.EMPTY,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent { HintsEditorHost(situation.state(), {}, fontScale, darkTheme) }
        compose.onRoot().captureRoboImage(
            filePath = fileName,
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)),
        )
    }
}
