package app.plansnap.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of all base components: light, dark and 200 percent font scale (docs/TESTING.md section 6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h800dp-port-notnight-mdpi")
class ComponentScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given the component gallery when light then it matches the baseline`() =
        capture(false, 1f, "components-light.png")

    @Test
    fun `given the component gallery when dark then it matches the baseline`() =
        capture(true, 1f, "components-dark.png")

    @Test
    fun `given the component gallery when font scale is 2 then it matches the baseline`() =
        capture(false, 2f, "components-fontscale-2.png")

    private fun capture(
        darkTheme: Boolean,
        fontScale: Float,
        fileName: String,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ComponentGallery(darkTheme)
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
