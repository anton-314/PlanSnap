package app.plansnap.core.drawing.viewport

import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.isCloseTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultViewportTransformTest {
    private val transform = DefaultViewportTransform()
    private val viewport =
        Viewport(centerWorld = Vec2(1_000.0, 2_000.0), pixelsPerMm = 0.5, widthPx = 800, heightPx = 600)

    private fun assertScreen(
        expectedX: Double,
        expectedY: Double,
        actual: ScreenPoint,
    ) {
        assertTrue(actual.x.isCloseTo(expectedX, EPS) && actual.y.isCloseTo(expectedY, EPS), "$actual")
    }

    private fun assertWorld(
        expected: Vec2,
        actual: Vec2,
    ) {
        assertTrue(actual.isCloseTo(expected, EPS), "expected $expected, was $actual")
    }

    private fun assertBounds(
        expected: WorldBounds,
        actual: WorldBounds,
    ) {
        val close =
            actual.minX.isCloseTo(expected.minX, EPS) &&
                actual.minY.isCloseTo(expected.minY, EPS) &&
                actual.maxX.isCloseTo(expected.maxX, EPS) &&
                actual.maxY.isCloseTo(expected.maxY, EPS)
        assertTrue(close, "expected $expected, was $actual")
    }

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `given viewport when the center is mapped then it lands on the screen center`() {
        assertScreen(400.0, 300.0, transform.worldToScreen(viewport, Vec2(1_000.0, 2_000.0)))
    }

    @Test
    fun `given viewport when a world point above and right is mapped then screen y points down`() {
        // 200 mm right and 100 mm up at 0.5 px/mm: 100 px right, 50 px up.
        assertScreen(500.0, 250.0, transform.worldToScreen(viewport, Vec2(1_200.0, 2_100.0)))
        assertWorld(Vec2(1_200.0, 2_100.0), transform.screenToWorld(viewport, ScreenPoint(500.0, 250.0)))
    }

    @Test
    fun `given viewport when the screen origin is mapped then it is the top left of the visible area`() {
        assertWorld(Vec2(200.0, 2_600.0), transform.screenToWorld(viewport, ScreenPoint(0.0, 0.0)))
        assertBounds(WorldBounds(200.0, 1_400.0, 1_800.0, 2_600.0), transform.visibleBounds(viewport))
    }

    @Test
    fun `given focus when zoomed by two then the focus stays and the zoom doubles`() {
        val focus = ScreenPoint(100.0, 500.0)
        val anchor = transform.screenToWorld(viewport, focus)
        val zoomed = transform.zoomAround(viewport, focus, 2.0)
        assertTrue(zoomed.pixelsPerMm.isCloseTo(1.0, EPS))
        assertScreen(100.0, 500.0, transform.worldToScreen(zoomed, anchor))
        assertEquals(viewport.widthPx, zoomed.widthPx)
        assertEquals(viewport.heightPx, zoomed.heightPx)
    }

    @Test
    fun `given zoom beyond the limits when zoomed then the zoom is clamped and the focus stays`() {
        val focus = ScreenPoint(700.0, 20.0)
        val anchor = transform.screenToWorld(viewport, focus)
        val zoomedIn = transform.zoomAround(viewport, focus, 1e9)
        assertTrue(zoomedIn.pixelsPerMm.isCloseTo(ZoomLimits.DEFAULT.maxPixelsPerMm, EPS))
        assertScreen(700.0, 20.0, transform.worldToScreen(zoomedIn, anchor))
        val zoomedOut = transform.zoomAround(viewport, focus, 1e-12)
        assertTrue(zoomedOut.pixelsPerMm.isCloseTo(ZoomLimits.DEFAULT.minPixelsPerMm, EPS))
        assertScreen(700.0, 20.0, transform.worldToScreen(zoomedOut, anchor))
    }

    @Test
    fun `given overflowing factor when zoomed then the zoom is clamped to the maximum`() {
        val huge = viewport.copy(pixelsPerMm = 50.0)
        assertTrue(
            transform.zoomAround(huge, ScreenPoint(0.0, 0.0), Double.MAX_VALUE).pixelsPerMm.isCloseTo(100.0, EPS),
        )
    }

    @Test
    fun `given invalid zoom factor when zoomed then it is rejected`() {
        val focus = ScreenPoint(0.0, 0.0)
        assertRejected { transform.zoomAround(viewport, focus, 0.0) }
        assertRejected { transform.zoomAround(viewport, focus, -1.0) }
        assertRejected { transform.zoomAround(viewport, focus, Double.NaN) }
        assertRejected { transform.zoomAround(viewport, focus, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `given pan when applied then content follows the finger and zoom is unchanged`() {
        val panned = transform.panBy(viewport, 40.0, -10.0)
        assertWorld(Vec2(920.0, 1_980.0), panned.centerWorld)
        assertEquals(viewport.pixelsPerMm, panned.pixelsPerMm)
        assertScreen(440.0, 290.0, transform.worldToScreen(panned, viewport.centerWorld))
    }

    @Test
    fun `given invalid pan when applied then it is rejected`() {
        assertRejected { transform.panBy(viewport, Double.NaN, 0.0) }
        assertRejected { transform.panBy(viewport, 0.0, Double.NEGATIVE_INFINITY) }
    }

    @Test
    fun `given wide bounds when fitted then the width fills the screen`() {
        val fitted = transform.fit(viewport, WorldBounds(0.0, 0.0, 8_000.0, 1_000.0))
        assertTrue(fitted.pixelsPerMm.isCloseTo(0.1, EPS))
        assertWorld(Vec2(4_000.0, 500.0), fitted.centerWorld)
        assertBounds(WorldBounds(0.0, -2_500.0, 8_000.0, 3_500.0), transform.visibleBounds(fitted))
    }

    @Test
    fun `given tall bounds when fitted then the height fills the screen`() {
        val fitted = transform.fit(viewport, WorldBounds(-1_000.0, -3_000.0, 1_000.0, 3_000.0))
        assertTrue(fitted.pixelsPerMm.isCloseTo(0.1, EPS))
        assertWorld(Vec2.ZERO, fitted.centerWorld)
    }

    @Test
    fun `given a point or a line when fitted then the missing extent does not limit the zoom`() {
        val point = transform.fit(viewport, WorldBounds(5.0, 7.0, 5.0, 7.0))
        assertTrue(point.pixelsPerMm.isCloseTo(100.0, EPS))
        assertWorld(Vec2(5.0, 7.0), point.centerWorld)
        val horizontal = transform.fit(viewport, WorldBounds(0.0, 7.0, 1_600.0, 7.0))
        assertTrue(horizontal.pixelsPerMm.isCloseTo(0.5, EPS))
        val vertical = transform.fit(viewport, WorldBounds(0.0, 0.0, 0.0, 1_200.0))
        assertTrue(vertical.pixelsPerMm.isCloseTo(0.5, EPS))
    }

    @Test
    fun `given huge bounds when fitted then the zoom is clamped to the minimum`() {
        val fitted = transform.fit(viewport, WorldBounds(-1e300, -1e300, 1e300, 1e300))
        assertTrue(fitted.pixelsPerMm.isCloseTo(ZoomLimits.DEFAULT.minPixelsPerMm, EPS))
        assertWorld(Vec2.ZERO, fitted.centerWorld)
    }

    @Test
    fun `given empty screen when fitted then only the center changes`() {
        val empty = viewport.copy(widthPx = 0)
        val fitted = transform.fit(empty, WorldBounds(0.0, 0.0, 10.0, 20.0))
        assertEquals(empty.pixelsPerMm, fitted.pixelsPerMm)
        assertWorld(Vec2(5.0, 10.0), fitted.centerWorld)
        val flat = transform.fit(viewport.copy(heightPx = 0), WorldBounds(0.0, 0.0, 10.0, 20.0))
        assertEquals(viewport.pixelsPerMm, flat.pixelsPerMm)
    }

    @Test
    fun `given custom limits when zoomed then they apply`() {
        val limited = DefaultViewportTransform(ZoomLimits(0.25, 1.0))
        assertEquals(ZoomLimits(0.25, 1.0), limited.zoomLimits)
        assertTrue(limited.zoomAround(viewport, ScreenPoint(0.0, 0.0), 10.0).pixelsPerMm.isCloseTo(1.0, EPS))
        assertTrue(limited.zoomAround(viewport, ScreenPoint(0.0, 0.0), 0.1).pixelsPerMm.isCloseTo(0.25, EPS))
    }

    @Test
    fun `given invalid limits when created then they are rejected`() {
        assertRejected { ZoomLimits(0.0, 1.0) }
        assertRejected { ZoomLimits(-1.0, 1.0) }
        assertRejected { ZoomLimits(Double.NaN, 1.0) }
        assertRejected { ZoomLimits(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY) }
        assertRejected { ZoomLimits(2.0, 1.0) }
        assertRejected { ZoomLimits(1.0, Double.POSITIVE_INFINITY) }
        assertRejected { ZoomLimits(1.0, Double.NaN) }
        assertRejected { ZoomLimits.DEFAULT.clamp(Double.NaN) }
        assertEquals(2.0, ZoomLimits(2.0, 2.0).clamp(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `given extreme coordinates when mapped then non-finite results are rejected`() {
        val far = viewport.copy(pixelsPerMm = 100.0)
        assertRejected { transform.worldToScreen(far, Vec2(Double.MAX_VALUE, 0.0)) }
        val wide = viewport.copy(pixelsPerMm = Double.MIN_VALUE)
        assertRejected { transform.screenToWorld(wide, ScreenPoint(1e10, 0.0)) }
    }

    @Test
    fun `given density when the hit radius is converted then it is constant on screen`() {
        assertTrue(viewport.hitRadiusMm(pixelsPerDp = 2.0).isCloseTo(96.0, EPS))
        assertTrue(viewport.copy(pixelsPerMm = 4.0).hitRadiusMm(10.0, 2.0).isCloseTo(5.0, EPS))
        assertEquals(0.0, viewport.hitRadiusMm(0.0, 3.0))
    }

    @Test
    fun `given invalid hit radius arguments when converted then they are rejected`() {
        assertRejected { viewport.hitRadiusMm(-1.0, 2.0) }
        assertRejected { viewport.hitRadiusMm(Double.NaN, 2.0) }
        assertRejected { viewport.hitRadiusMm(Double.POSITIVE_INFINITY, 2.0) }
        assertRejected { viewport.hitRadiusMm(24.0, 0.0) }
        assertRejected { viewport.hitRadiusMm(24.0, Double.NaN) }
        assertRejected { viewport.hitRadiusMm(24.0, Double.POSITIVE_INFINITY) }
    }

    private companion object {
        const val EPS = 1e-9
    }
}
