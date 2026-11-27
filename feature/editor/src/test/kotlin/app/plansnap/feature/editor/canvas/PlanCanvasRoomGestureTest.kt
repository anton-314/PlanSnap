package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.state.PlanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The long-press gesture on the real canvas next to its pan, pinch and tap gestures (UX section 4). The screen is 360
 * by 640 dp at 1 px/dp and the view shows the 4 m by 3 m room at 0.05 px/mm, so 1 px is 20 mm.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanCanvasRoomGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val transform = DefaultViewportTransform()
    private val math = PlanViewMath()
    private val start = PlanView(2000.0, 1500.0, SCALE)
    private var view by mutableStateOf(start)
    private val events = mutableListOf<String>()
    private val starts = mutableListOf<RoomGestureStart>()
    private val updates = mutableListOf<RoomGestureUpdate>()
    private val taps = mutableListOf<PlanTap>()

    private val callbacks =
        RoomGestureCallbacks(
            onStart = {
                starts += it
                events += "start"
            },
            onUpdate = {
                updates += it
                events += "update"
            },
            onEnd = { events += "end" },
            onCancel = { events += "cancel" },
        )

    private fun show(gestures: RoomGestureCallbacks = callbacks) {
        composeRule.setContent {
            PlanSnapTheme(darkTheme = false) {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    PlanCanvas(
                        scene = CanvasTestScenes.rectangleScene(),
                        view = view,
                        onViewChange = { view = it },
                        onTap = { taps += it },
                        modifier = Modifier.testTag(TAG),
                        roomGestures = gestures,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(TAG).performTouchInput(block)
        composeRule.waitForIdle()
    }

    private fun pause(millis: Long = LONG_PRESS_MS) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    /** The screen position of the world point ([x], [y]) in the start view. */
    private fun screen(
        x: Double,
        y: Double,
    ): Offset {
        val point = transform.worldToScreen(math.viewport(start, WIDTH, HEIGHT), Vec2(x, y))
        return Offset(point.x.toFloat(), point.y.toFloat())
    }

    private val inside get() = screen(2000.0, 1500.0)
    private val ground get() = Offset(20f, 20f)

    @Test
    fun `given a long press on a room when dragged then the movement is reported in millimeters`() {
        show()

        touch { down(inside) }
        pause()
        touch { moveBy(Offset(30f, 0f)) }
        touch { moveBy(Offset(20f, -40f)) }
        touch { up() }

        assertEquals(listOf("start", "update", "update", "update", "end"), events)
        val begun = starts.single()
        assertEquals(RoomId("living"), begun.roomId)
        assertEquals("16 dp in mm", 16.0 / SCALE, begun.snapDistanceMm, 1e-9)
        assertEquals("grid step of at least 24 dp", 500.0, begun.gridStepMm, 1e-9)
        assertEquals(50.0 / SCALE, updates.last().translationMm.x, 1e-3)
        assertEquals(40.0 / SCALE, updates.last().translationMm.y, 1e-3)
        assertEquals(0.0, updates.last().rotationRad, 0.0)
        assertEquals("the view does not pan", start, view)
        assertTrue("no tap", taps.isEmpty())
    }

    @Test
    fun `given a long press on a room when released without movement then nothing is reported to move`() {
        show()

        touch { down(inside) }
        pause()
        touch { moveBy(Offset(3f, 3f)) }
        touch { up() }
        pause()

        assertEquals(listOf("start", "end"), events)
        assertEquals(start, view)
        assertTrue("a long press is no tap", taps.isEmpty())
    }

    @Test
    fun `given a running gesture when a second finger twists then the rotation is reported`() {
        show()

        touch { down(0, inside) }
        pause()
        touch { down(1, inside + Offset(60f, 0f)) }
        // The second finger circles down, clockwise on the screen, the center follows half of it.
        touch { moveTo(1, inside + Offset(0f, 60f)) }
        touch {
            up(1)
            up(0)
        }

        assertEquals("end", events.last())
        assertEquals(-Math.PI / 2.0, updates.last().rotationRad, 1e-3)
        assertEquals("the view neither pans nor zooms", start, view)
    }

    @Test
    fun `given a running gesture when the system cancels it then it is cancelled and not ended`() {
        show()

        touch { down(inside) }
        pause()
        touch { moveBy(Offset(60f, 0f)) }
        touch { cancel() }

        assertEquals(listOf("start", "update", "cancel"), events)
    }

    @Test
    fun `given a drag without a long press then the view pans and no room moves`() {
        show()

        touch { swipe(inside, inside + Offset(80f, 0f), 200) }

        assertTrue(events.isEmpty())
        assertTrue("panned: ${view.centerXMm}", view.centerXMm < start.centerXMm - 500.0)
    }

    @Test
    fun `given a tap on a room then it is a tap and no room gesture`() {
        show()

        touch { click(inside) }
        pause()

        assertTrue(events.isEmpty())
        assertEquals(1, taps.size)
    }

    @Test
    fun `given a long press on empty ground when dragged then no room gesture starts`() {
        show()

        touch { down(ground) }
        pause()
        touch { moveBy(Offset(80f, 0f)) }
        touch { up() }

        assertTrue(events.isEmpty())
        assertTrue("the view pans as usual", view.centerXMm < start.centerXMm)
    }

    @Test
    fun `given two resting fingers when they pinch afterwards then the view zooms and no room moves`() {
        show()

        touch {
            down(0, inside - Offset(30f, 0f))
            down(1, inside + Offset(30f, 0f))
        }
        pause()
        repeat(PINCH_STEPS) {
            touch {
                moveBy(0, Offset(-10f, 0f))
                moveBy(1, Offset(10f, 0f))
            }
        }
        touch {
            up(0)
            up(1)
        }

        assertTrue(events.isEmpty())
        assertTrue("zoomed in: ${view.pixelsPerMm}", view.pixelsPerMm > start.pixelsPerMm * 1.5)
    }

    @Test
    fun `given disabled room gestures when a finger rests and lifts then it stays a tap`() {
        show(RoomGestureCallbacks.None)

        touch { down(inside) }
        pause()
        touch { up() }
        pause()

        assertTrue(events.isEmpty())
        assertEquals(1, taps.size)
    }

    private companion object {
        const val TAG = "plan"
        const val WIDTH = 360
        const val HEIGHT = 640
        const val SCALE = 0.05
        const val LONG_PRESS_MS = 800L
        const val PINCH_STEPS = 5
    }
}
