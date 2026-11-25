package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.feature.editor.state.PlanView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the plan canvas: empty plan, one room, several rooms, zoomed in and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanCanvasScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val math = PlanViewMath()

    @Test
    fun `given an empty plan when shown then it matches the baseline`() =
        capture(CanvasTestScenes.empty(), PlanView(0.0, 0.0, 0.1), false, "canvas-empty.png")

    @Test
    fun `given a rectangle when shown then it matches the baseline`() {
        val scene = CanvasTestScenes.rectangleScene()
        capture(scene, fitted(scene), false, "canvas-rectangle.png")
    }

    @Test
    fun `given several rooms when shown then it matches the baseline`() {
        val scene = CanvasTestScenes.apartmentScene()
        capture(scene, fitted(scene), false, "canvas-rooms.png")
    }

    @Test
    fun `given a zoomed view when shown then it matches the baseline`() =
        capture(CanvasTestScenes.apartmentScene(), PlanView(2500.0, 3200.0, 0.1), false, "canvas-zoomed.png")

    @Test
    fun `given several rooms when dark then it matches the baseline`() {
        val scene = CanvasTestScenes.apartmentScene()
        capture(scene, fitted(scene), true, "canvas-dark.png")
    }

    private fun fitted(scene: DrawingScene): PlanView =
        math.planView(
            checkNotNull(math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), WIDTH, HEIGHT), scene.bounds, MARGIN)),
        )

    private fun capture(
        scene: DrawingScene,
        view: PlanView,
        dark: Boolean,
        fileName: String,
    ) {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = dark) {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    PlanCanvas(scene = scene, view = view, onViewChange = {}, onTap = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 640
        const val MARGIN = 24.0

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
