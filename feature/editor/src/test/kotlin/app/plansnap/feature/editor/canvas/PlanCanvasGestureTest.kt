package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.feature.editor.state.PlanView
import app.plansnap.feature.editor.state.WallPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gestures on the plan canvas. The host mimics the state layer: it keeps the view and stores what the canvas reports.
 * The screen is 360 by 640 dp at 1 px/dp, so dp and px agree.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanCanvasGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val transform = DefaultViewportTransform()
    private val math = PlanViewMath()
    private val reported = mutableListOf<PlanView>()
    private val taps = mutableListOf<PlanTap>()
    private var view by mutableStateOf<PlanView?>(null)

    private fun show(
        scene: DrawingScene,
        initial: PlanView?,
        preview: WallPreview? = null,
    ) {
        view = initial
        composeRule.setContent {
            PlanSnapTheme(darkTheme = false) {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    PlanCanvas(
                        scene = scene,
                        view = view,
                        onViewChange = {
                            reported += it
                            view = it
                        },
                        onTap = { taps += it },
                        modifier = Modifier.testTag(TAG),
                        preview = preview,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun fitted(scene: DrawingScene): PlanView =
        math.planView(
            checkNotNull(math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), WIDTH, HEIGHT), scene.bounds, MARGIN)),
        )

    private fun viewport(planView: PlanView) = math.viewport(planView, WIDTH, HEIGHT)

    @Test
    fun `given a plan without a view when shown then the canvas reports a fitted view once`() {
        show(CanvasTestScenes.rectangleScene(), initial = null)

        assertEquals(1, reported.size)
        val fitted = viewport(reported.single())
        val bounds = CanvasTestScenes.rectangleScene().bounds!!
        val topLeft = transform.worldToScreen(fitted, Vec2(bounds.minX, bounds.maxY))
        val bottomRight = transform.worldToScreen(fitted, Vec2(bounds.maxX, bounds.minY))
        assertEquals(MARGIN, minOf(topLeft.x, topLeft.y, WIDTH - bottomRight.x, HEIGHT - bottomRight.y), 1.0)
    }

    @Test
    fun `given an empty plan without a view when shown then nothing is reported`() {
        show(CanvasTestScenes.empty(), initial = null)

        assertTrue(reported.isEmpty())
    }

    @Test
    fun `given a view when a finger drags then the plan follows and the zoom stays`() {
        val start = PlanView(2000.0, 1500.0, 0.1)
        show(CanvasTestScenes.rectangleScene(), start)

        composeRule.onNodeWithTag(TAG).performTouchInput { swipe(Offset(100f, 300f), Offset(200f, 350f), 300) }
        composeRule.waitForIdle()

        val end = checkNotNull(view)
        assertTrue("center moved left: ${end.centerXMm}", end.centerXMm < start.centerXMm - 500.0)
        assertTrue("center moved up: ${end.centerYMm}", end.centerYMm > start.centerYMm + 200.0)
        assertEquals(start.pixelsPerMm, end.pixelsPerMm, 0.0)
        assertTrue("a drag is no tap", taps.isEmpty())
    }

    @Test
    fun `given two fingers moving apart when pinching then the plan zooms around the fingers`() {
        val start = PlanView(2000.0, 1500.0, 0.1)
        show(CanvasTestScenes.rectangleScene(), start)
        val worldBefore = transform.screenToWorld(viewport(start), ScreenPoint(180.0, 320.0))

        composeRule.onNodeWithTag(TAG).performTouchInput {
            pinch(Offset(150f, 320f), Offset(90f, 320f), Offset(210f, 320f), Offset(270f, 320f), 300)
        }
        composeRule.waitForIdle()

        val end = checkNotNull(view)
        assertTrue("zoomed in: ${end.pixelsPerMm}", end.pixelsPerMm > start.pixelsPerMm * 1.5)
        val worldAfter = transform.screenToWorld(viewport(end), ScreenPoint(180.0, 320.0))
        assertEquals(worldBefore.x, worldAfter.x, 50.0)
        assertEquals(worldBefore.y, worldAfter.y, 50.0)
    }

    @Test
    fun `given the maximum zoom when pinching further out then the zoom stays at the limit`() {
        show(CanvasTestScenes.rectangleScene(), PlanView(2000.0, 1500.0, 99.0))

        composeRule.onNodeWithTag(TAG).performTouchInput {
            pinch(Offset(170f, 320f), Offset(100f, 320f), Offset(190f, 320f), Offset(260f, 320f), 300)
        }
        composeRule.waitForIdle()

        assertEquals(100.0, checkNotNull(view).pixelsPerMm, 0.0)
    }

    @Test
    fun `given the minimum zoom when pinching further in then the zoom stays at the limit`() {
        show(CanvasTestScenes.rectangleScene(), PlanView(2000.0, 1500.0, 1.05e-4))

        composeRule.onNodeWithTag(TAG).performTouchInput {
            pinch(Offset(100f, 320f), Offset(170f, 320f), Offset(260f, 320f), Offset(190f, 320f), 300)
        }
        composeRule.waitForIdle()

        assertEquals(1e-4, checkNotNull(view).pixelsPerMm, 0.0)
    }

    @Test
    fun `given a plan when double tapped then it is fitted with 24 dp margin on every side`() {
        val scene = CanvasTestScenes.apartmentScene()
        show(scene, PlanView(0.0, 0.0, 5.0))

        composeRule.onNodeWithTag(TAG).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        val fitted = viewport(checkNotNull(view))
        val bounds = checkNotNull(scene.bounds)
        val topLeft = transform.worldToScreen(fitted, Vec2(bounds.minX, bounds.maxY))
        val bottomRight = transform.worldToScreen(fitted, Vec2(bounds.maxX, bounds.minY))
        val margins = listOf(topLeft.x, topLeft.y, WIDTH - bottomRight.x, HEIGHT - bottomRight.y)
        assertTrue("no side closer than 24 dp: $margins", margins.all { it >= MARGIN - 0.5 })
        assertEquals("the limiting side has exactly 24 dp", MARGIN, margins.min(), 0.5)
        assertEquals("centered horizontally", topLeft.x, WIDTH - bottomRight.x, 0.5)
        assertEquals("centered vertically", topLeft.y, HEIGHT - bottomRight.y, 0.5)
        assertTrue("a double tap is no tap", taps.isEmpty())
    }

    @Test
    fun `given an empty plan when double tapped then the view does not change`() {
        val start = PlanView(123.0, -45.0, 0.2)
        show(CanvasTestScenes.empty(), start)

        composeRule.onNodeWithTag(TAG).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(start, view)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `given a tap on a wall when released then the wall is reported`() {
        val scene = CanvasTestScenes.rectangleScene()
        show(scene, fitted(scene))
        val wall = transform.worldToScreen(viewport(checkNotNull(view)), Vec2(2000.0, 0.0))

        composeRule.onNodeWithTag(TAG).performTouchInput { click(Offset(wall.x.toFloat(), wall.y.toFloat())) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.waitForIdle()

        val hit = taps.single().target
        assertTrue("wall expected but was $hit", hit is SceneTarget.Wall)
        assertEquals(2000.0, taps.single().world.x, 30.0)
    }

    @Test
    fun `given a tap on empty ground when released then no target is reported`() {
        val scene = CanvasTestScenes.rectangleScene()
        show(scene, fitted(scene))

        composeRule.onNodeWithTag(TAG).performTouchInput { click(Offset(10f, 10f)) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.waitForIdle()

        assertNull(taps.single().target)
        assertTrue("a tap does not change the view", reported.isEmpty())
    }

    @Test
    fun `given a wall preview when shown then it is part of the drawn plan`() {
        val extra = CanvasTestScenes.rectangle("extra", 2000, 2000, x = 6000.0)
        val scene = CanvasTestScenes.rectangleScene()
        show(scene, PlanView(2000.0, 1500.0, 0.1), preview = CanvasTestScenes.preview(extra))

        composeRule.onNodeWithTag(TAG).assertContentDescriptionEquals("Floor plan with 2 rooms and 8 walls")
    }

    @Test
    fun `given a plan when described then the semantics name rooms and walls`() {
        show(CanvasTestScenes.rectangleScene(), PlanView(2000.0, 1500.0, 0.1))

        composeRule.onNodeWithTag(TAG).assertContentDescriptionEquals("Floor plan with 1 room and 4 walls")
    }

    @Test
    fun `given an empty plan when described then the semantics say it is empty`() {
        show(CanvasTestScenes.empty(), PlanView(0.0, 0.0, 0.1))

        composeRule.onNodeWithTag(TAG).assertContentDescriptionEquals("Floor plan, empty")
    }

    @Test
    fun `given the accessibility action when invoked then the plan is fitted`() {
        show(CanvasTestScenes.apartmentScene(), PlanView(0.0, 0.0, 5.0))
        val node = composeRule.onNodeWithTag(TAG).fetchSemanticsNode()
        val action = checkNotNull(node.config.getOrNull(SemanticsActions.CustomActions)).single()
        assertEquals("Fit plan to screen", action.label)

        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()

        assertNotNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue("fitted to a smaller zoom: ${view?.pixelsPerMm}", checkNotNull(view).pixelsPerMm < 1.0)
    }

    private companion object {
        const val TAG = "plan"
        const val WIDTH = 360
        const val HEIGHT = 640
        const val MARGIN = 24.0
        const val DOUBLE_TAP_WAIT_MS = 600L
    }
}
