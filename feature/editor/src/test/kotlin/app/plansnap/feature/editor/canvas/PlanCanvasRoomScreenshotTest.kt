package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.PlanView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the additions of the rooms function: the grid and the preview of a dragged room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanCanvasRoomScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val view = PlanView(2000.0, 1500.0, 0.05)

    private val preview =
        RoomTransformPreview(
            roomIds = persistentSetOf(RoomId("living")),
            translationMm = Vec2(1000.0, -2500.0),
            rotationRad = Math.PI / 6.0,
            pivotMm = Vec2(2000.0, 1500.0),
        )

    @Test
    fun `given the grid when light then it matches the baseline`() = capture("rooms-canvas-grid.png")

    @Test
    fun `given the grid when dark then it matches the baseline`() = capture("rooms-canvas-grid-dark.png", dark = true)

    @Test
    fun `given a dragged room when previewed then it is drawn moved and turned`() =
        capture("rooms-canvas-preview.png", roomPreview = preview)

    private fun capture(
        fileName: String,
        dark: Boolean = false,
        roomPreview: RoomTransformPreview? = null,
    ) {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = dark) {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    PlanCanvas(
                        scene = CanvasTestScenes.rectangleScene(),
                        view = view,
                        onViewChange = {},
                        onTap = {},
                        roomPreview = roomPreview,
                        showGrid = true,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 640

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
