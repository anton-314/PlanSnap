package app.plansnap.feature.editor.canvas

import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.drawing.viewport.ZoomLimits
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.feature.editor.state.PlanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanViewMathTest {
    private val math = PlanViewMath()
    private val transform = DefaultViewportTransform()
    private val start = math.viewport(PlanView(1000.0, 500.0, 0.2), 400, 800)

    @Test
    fun `given a pan when applied then the content follows the finger and the zoom stays`() {
        val world = Vec2(1200.0, 600.0)
        val before = transform.worldToScreen(start, world)

        val moved = math.gesture(start, GestureStep(100.0, 100.0, 30.0, -20.0, 1.0))

        val after = transform.worldToScreen(moved, world)
        assertEquals(before.x + 30.0, after.x, 1e-9)
        assertEquals(before.y - 20.0, after.y, 1e-9)
        assertEquals(start.pixelsPerMm, moved.pixelsPerMm, 0.0)
    }

    @Test
    fun `given a pinch when applied then the point under the fingers stays and the zoom scales`() {
        val focus = ScreenPoint(300.0, 200.0)
        val world = transform.screenToWorld(start, focus)

        val zoomed = math.gesture(start, GestureStep(focus.x, focus.y, 0.0, 0.0, 2.0))

        assertEquals(0.4, zoomed.pixelsPerMm, 1e-12)
        val after = transform.worldToScreen(zoomed, world)
        assertEquals(focus.x, after.x, 1e-6)
        assertEquals(focus.y, after.y, 1e-6)
    }

    @Test
    fun `given a pinch with a moving centroid when applied then the point follows the centroid`() {
        val world = transform.screenToWorld(start, ScreenPoint(150.0, 150.0))

        val moved = math.gesture(start, GestureStep(170.0, 160.0, 20.0, 10.0, 1.5))

        val after = transform.worldToScreen(moved, world)
        assertEquals(170.0, after.x, 1e-6)
        assertEquals(160.0, after.y, 1e-6)
    }

    @Test
    fun `given the zoom limits when pinching beyond them then the zoom is clamped`() {
        val limits = ZoomLimits.DEFAULT
        val zoomedIn = math.gesture(start, GestureStep(0.0, 0.0, 0.0, 0.0, 1e9))
        val zoomedOut = math.gesture(start, GestureStep(0.0, 0.0, 0.0, 0.0, 1e-9))

        assertEquals(limits.maxPixelsPerMm, zoomedIn.pixelsPerMm, 0.0)
        assertEquals(limits.minPixelsPerMm, zoomedOut.pixelsPerMm, 0.0)
    }

    @Test
    fun `given invalid gesture input when applied then the viewport is unchanged`() {
        assertEquals(start, math.gesture(start, GestureStep(Double.NaN, 0.0, 1.0, 1.0, 1.0)))
        assertEquals(start, math.gesture(start, GestureStep(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 1.0)))
        assertEquals(start, math.gesture(start, GestureStep(0.0, 0.0, 0.0, 0.0, Double.NaN)))
        assertEquals(start, math.gesture(start, GestureStep(0.0, 0.0, 0.0, 0.0, -1.0)))
        assertEquals(start, math.gesture(start, GestureStep(0.0, 0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun `given content when fitted then it is centered with 24 dp margin on the limiting axis`() {
        val bounds = WorldBounds(-2000.0, -1500.0, 2000.0, 1500.0)
        val margin = 24.0 * 2.0

        val fitted = math.fit(start, bounds, margin)

        assertNotNull(fitted)
        fitted!!
        assertEquals(400, fitted.widthPx)
        assertEquals(800, fitted.heightPx)
        val min = transform.worldToScreen(fitted, Vec2(bounds.minX, bounds.maxY))
        val max = transform.worldToScreen(fitted, Vec2(bounds.maxX, bounds.minY))
        assertEquals(margin, min.x, 1e-6)
        assertEquals(400.0 - margin, max.x, 1e-6)
        assertEquals(800.0 - max.y, min.y, 1e-6)
        assertTrue(min.y >= margin - 1e-6)
    }

    @Test
    fun `given tall content when fitted then the height limits and every side keeps the margin`() {
        val bounds = WorldBounds(0.0, 0.0, 1000.0, 6000.0)
        val margin = 48.0

        val fitted = math.fit(start, bounds, margin)!!

        val top = transform.worldToScreen(fitted, Vec2(0.0, 6000.0))
        val bottom = transform.worldToScreen(fitted, Vec2(1000.0, 0.0))
        assertEquals(margin, top.y, 1e-6)
        assertEquals(800.0 - margin, bottom.y, 1e-6)
        assertTrue(top.x >= margin)
    }

    @Test
    fun `given no content when fitted then there is no new view`() {
        assertNull(math.fit(start, null, 48.0))
    }

    @Test
    fun `given a single point when fitted then it is shown centered at the maximum zoom`() {
        val fitted = math.fit(start, WorldBounds(300.0, 400.0, 300.0, 400.0), 48.0)!!

        assertEquals(Vec2(300.0, 400.0), fitted.centerWorld)
        assertEquals(ZoomLimits.DEFAULT.maxPixelsPerMm, fitted.pixelsPerMm, 0.0)
    }

    @Test
    fun `given a screen smaller than the margins when fitted then the zoom is still valid`() {
        val tiny = math.viewport(PlanView(0.0, 0.0, 1.0), 20, 20)

        val fitted = math.fit(tiny, WorldBounds(0.0, 0.0, 1000.0, 1000.0), 48.0)!!

        assertTrue(fitted.pixelsPerMm > 0.0)
        assertEquals(20, fitted.widthPx)
    }

    @Test
    fun `given a viewport when converted to a view and back then it is the same`() {
        val view = PlanView(12.5, -7.0, 0.03)

        assertEquals(view, math.planView(math.viewport(view, 100, 100)))
    }

    @Test
    fun `given rooms when one is off screen then only the visible ones are drawn`() {
        val near = CanvasTestScenes.rectangle("near", 2000, 2000)
        val far = CanvasTestScenes.rectangle("far", 2000, 2000, x = 100_000.0)
        val scene = CanvasTestScenes.scene(near, far)
        val viewport = math.viewport(PlanView(1000.0, 1000.0, 0.1), 400, 400)

        val visible = math.visibleRooms(scene.rooms, viewport, 0.0)

        assertEquals(listOf("near"), visible.map { it.roomId.value })
    }

    @Test
    fun `given all rooms visible when culled then the same list is returned`() {
        val scene = CanvasTestScenes.apartmentScene()
        val viewport = math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), 400, 400), scene.bounds, 48.0)!!

        assertSame(scene.rooms, math.visibleRooms(scene.rooms, viewport, 0.0))
    }

    @Test
    fun `given a tap on a wall when resolved then the wall is the target`() {
        val scene = CanvasTestScenes.rectangleScene()
        val viewport = math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), 400, 600), scene.bounds, 48.0)!!
        val wallPoint = transform.worldToScreen(viewport, Vec2(2000.0, 0.0))

        val tap = math.tap(scene, viewport, wallPoint.x, wallPoint.y, 2.0)

        assertTrue(tap.target is SceneTarget.Wall)
    }

    @Test
    fun `given a tap in the room when resolved then the room body is the target`() {
        val scene = CanvasTestScenes.rectangleScene()
        val viewport = math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), 400, 600), scene.bounds, 48.0)!!
        val inside = transform.worldToScreen(viewport, Vec2(2000.0, 1500.0))

        val tap = math.tap(scene, viewport, inside.x, inside.y, 2.0)

        assertTrue(tap.target is SceneTarget.RoomBody)
        assertEquals(2000.0, tap.world.x, 1e-6)
    }

    @Test
    fun `given a tap on empty ground when resolved then there is no target`() {
        val scene = CanvasTestScenes.rectangleScene()
        val viewport = math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), 400, 600), scene.bounds, 48.0)!!

        assertNull(math.tap(scene, viewport, 5.0, 5.0, 2.0).target)
    }

    @Test
    fun `given the hit radius when zoomed then 24 dp on the screen are always hit`() {
        val scene = CanvasTestScenes.rectangleScene()
        val viewport = math.fit(math.viewport(PlanView(0.0, 0.0, 1.0), 400, 600), scene.bounds, 48.0)!!
        val wall = transform.worldToScreen(viewport, Vec2(2000.0, 0.0))

        val near = math.tap(scene, viewport, wall.x, wall.y + 23.0 * 2.0 - 2.0, 2.0)
        val far = math.tap(scene, viewport, wall.x, wall.y + 24.0 * 2.0 + 60.0, 2.0)

        assertTrue(near.target is SceneTarget.Wall)
        assertTrue(far.target !is SceneTarget.Wall)
    }
}
