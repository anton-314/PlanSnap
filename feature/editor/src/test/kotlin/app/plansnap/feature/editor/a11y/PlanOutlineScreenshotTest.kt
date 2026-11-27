package app.plansnap.feature.editor.a11y

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the list of rooms and walls: light, and dark German at 200 percent font. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class PlanOutlineScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `outline light`() = capture(fontScale = 1f, fileName = "a11y-outline-light.png")

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h740dp-port-night-mdpi")
    fun `outline dark german font 2`() = capture(fontScale = 2f, fileName = "a11y-outline-dark-de-fontscale-2.png")

    private fun capture(
        fontScale: Float,
        fileName: String,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val entries = planOutline(A11yStates.furnished, resources)
        compose.setContent { PlanSnapTheme { Surface { PlanOutlineContent(entries) {} } } }
        compose.onRoot().captureRoboImage(
            filePath = fileName,
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)),
        )
    }
}
