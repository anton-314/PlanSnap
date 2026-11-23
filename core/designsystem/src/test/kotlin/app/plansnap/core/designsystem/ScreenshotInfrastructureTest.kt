package app.plansnap.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the Roborazzi pipeline end to end with a deliberately minimal element (no text, no theme), so the
 * baseline does not depend on fonts or on the design system. Qualifiers are fixed so that neither device
 * language nor time zone can influence the image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w120dp-h120dp-port-notnight-mdpi")
class ScreenshotInfrastructureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `given a colored square on a light background when captured then it matches the baseline`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Box(Modifier.size(80.dp).background(Color(0xFF1B6EF3)))
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "colored-square.png",
            roborazziOptions = STRICT_OPTIONS,
        )
    }

    private companion object {
        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
