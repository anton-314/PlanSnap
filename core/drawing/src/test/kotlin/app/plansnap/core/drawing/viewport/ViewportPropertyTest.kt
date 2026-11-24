package app.plansnap.core.drawing.viewport

import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.DefaultViewportTransform.Companion.ROUND_TRIP_RELATIVE_TOLERANCE
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.isCloseTo
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

class ViewportPropertyTest {
    private val transform = DefaultViewportTransform()
    private val limits = ZoomLimits.DEFAULT

    /** Magnitude `M` of the accuracy statement in the KDoc of [DefaultViewportTransform]. */
    private fun magnitude(
        viewport: Viewport,
        vararg values: Double,
    ): Double {
        var result = max(1.0, (viewport.widthPx + viewport.heightPx) / viewport.pixelsPerMm)
        result = max(result, max(abs(viewport.centerWorld.x), abs(viewport.centerWorld.y)))
        for (value in values) result = max(result, abs(value))
        return result
    }

    private fun assertScreenClose(
        expected: ScreenPoint,
        actual: ScreenPoint,
        tolerancePx: Double,
        context: Any,
    ) {
        val close = actual.x.isCloseTo(expected.x, tolerancePx) && actual.y.isCloseTo(expected.y, tolerancePx)
        assertTrue(close, "expected $expected, was $actual, tolerance $tolerancePx px, $context")
    }

    @Test
    fun `world to screen to world is the identity`() =
        runBlocking<Unit> {
            var checked = 0
            checkAll(ITERATIONS, config(SEED_WORLD_ROUND_TRIP), cases) { case ->
                val (viewport, world) = case
                val back = transform.screenToWorld(viewport, transform.worldToScreen(viewport, world))
                val tolerance = ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(viewport, world.x, world.y)
                assertTrue(back.isCloseTo(world, tolerance), "$world became $back, $viewport")
                checked++
            }
            assertEquals(ITERATIONS, checked)
        }

    @Test
    fun `screen to world to screen is the identity`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_SCREEN_ROUND_TRIP), cases) { case ->
                val (viewport, _, screen) = case
                val world = transform.screenToWorld(viewport, screen)
                val back = transform.worldToScreen(viewport, world)
                val tolerance =
                    ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(viewport, world.x, world.y) * viewport.pixelsPerMm
                assertScreenClose(screen, back, tolerance, case)
            }
        }

    @Test
    fun `zooming keeps the world point under the focus and stays within the limits`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ZOOM_FOCUS), cases) { case ->
                val viewport = case.viewport
                val focus = case.screen
                val factor = case.factor
                val anchor = transform.screenToWorld(viewport, focus)
                val zoomed = transform.zoomAround(viewport, focus, factor)
                assertTrue(zoomed.pixelsPerMm in limits.minPixelsPerMm..limits.maxPixelsPerMm, "$zoomed")
                val tolerance =
                    ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(zoomed, anchor.x, anchor.y) * zoomed.pixelsPerMm
                assertScreenClose(focus, transform.worldToScreen(zoomed, anchor), tolerance, case)
            }
        }

    @Test
    fun `zooming in and out by the inverse factor restores the view`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ZOOM_INVERSE), cases) { case ->
                val viewport = case.viewport
                val focus = case.screen
                val factor = case.factor
                val there = transform.zoomAround(viewport, focus, factor)
                val effective = there.pixelsPerMm / viewport.pixelsPerMm
                val back = transform.zoomAround(there, focus, 1.0 / effective)
                val scaleTolerance = ROUND_TRIP_RELATIVE_TOLERANCE * viewport.pixelsPerMm
                assertTrue(back.pixelsPerMm.isCloseTo(viewport.pixelsPerMm, scaleTolerance), "$back, $case")
                val anchor = transform.screenToWorld(viewport, focus)
                val zoomedExtent = (viewport.widthPx + viewport.heightPx) / there.pixelsPerMm
                val tolerance = ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(viewport, anchor.x, anchor.y, zoomedExtent)
                assertTrue(back.centerWorld.isCloseTo(viewport.centerWorld, tolerance), "$back, $case")
            }
        }

    @Test
    fun `two pans compose to one pan by the summed delta`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_PAN_COMPOSITION), cases) { case ->
                val viewport = case.viewport
                val first = case.screen
                val second = case.other
                val twice = transform.panBy(transform.panBy(viewport, first.x, first.y), second.x, second.y)
                val once = transform.panBy(viewport, first.x + second.x, first.y + second.y)
                assertEquals(viewport.pixelsPerMm, twice.pixelsPerMm)
                val deltas = (abs(first.x) + abs(first.y) + abs(second.x) + abs(second.y)) / viewport.pixelsPerMm
                val tolerance = ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(viewport, deltas)
                assertTrue(twice.centerWorld.isCloseTo(once.centerWorld, tolerance), "$twice vs $once, $case")
            }
        }

    @Test
    fun `panning moves every world point by the screen delta`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_PAN_SHIFT), cases) { case ->
                val (viewport, world, delta) = case
                val before = transform.worldToScreen(viewport, world)
                val panned = transform.panBy(viewport, delta.x, delta.y)
                val expected = ScreenPoint(before.x + delta.x, before.y + delta.y)
                val shift = (abs(delta.x) + abs(delta.y)) / viewport.pixelsPerMm
                val tolerance =
                    ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(viewport, world.x, world.y, shift) * viewport.pixelsPerMm
                assertScreenClose(expected, transform.worldToScreen(panned, world), tolerance, case)
            }
        }

    @Test
    fun `fitted bounds are centered and visible and touch the screen unless the zoom is at its maximum`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_FIT), cases) { case ->
                val (generated, world, extent) = case
                // At least 100 px per side keep the zoom of bounds up to 80 m above the minimum zoom.
                val viewport =
                    generated.copy(
                        widthPx = max(MIN_FIT_SCREEN_PX, generated.widthPx),
                        heightPx = max(MIN_FIT_SCREEN_PX, generated.heightPx),
                    )
                val bounds =
                    WorldBounds(world.x, world.y, world.x + abs(extent.x) * 10.0, world.y + abs(extent.y) * 10.0)
                val fitted = transform.fit(viewport, bounds)
                val visible = transform.visibleBounds(fitted)
                val tolerance =
                    ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(fitted, world.x, world.y, bounds.maxX, bounds.maxY)
                assertTrue(
                    visible.minX <= bounds.minX + tolerance && visible.maxX >= bounds.maxX - tolerance,
                    "$visible",
                )
                assertTrue(
                    visible.minY <= bounds.minY + tolerance && visible.maxY >= bounds.maxY - tolerance,
                    "$visible",
                )
                val center = Vec2((bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0)
                assertTrue(fitted.centerWorld.isCloseTo(center, tolerance), "$fitted, $bounds")
                val atMaximum =
                    fitted.pixelsPerMm.isCloseTo(limits.maxPixelsPerMm, ROUND_TRIP_RELATIVE_TOLERANCE)
                val touches =
                    visible.width.isCloseTo(bounds.width, tolerance) ||
                        visible.height.isCloseTo(bounds.height, tolerance)
                assertTrue(atMaximum || touches, "$visible for $bounds")
            }
        }

    @Test
    fun `the hit radius covers the same screen distance at every zoom`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_HIT_RADIUS), cases) { case ->
                val viewport = case.viewport
                val world = case.world
                val factor = case.factor
                val zoomed = transform.zoomAround(viewport, ScreenPoint(0.0, 0.0), factor)
                for (view in listOf(viewport, zoomed)) {
                    val radiusMm = view.hitRadiusMm(DEFAULT_HIT_RADIUS_DP, DENSITY)
                    val center = transform.worldToScreen(view, world)
                    val edge = transform.worldToScreen(view, Vec2(world.x + radiusMm, world.y))
                    val expectedPx = DEFAULT_HIT_RADIUS_DP * DENSITY
                    val tolerance =
                        ROUND_TRIP_RELATIVE_TOLERANCE * magnitude(view, world.x, world.y, radiusMm) * view.pixelsPerMm
                    assertTrue((edge.x - center.x).isCloseTo(expectedPx, tolerance), "$view, $case")
                }
            }
        }

    /** Random viewport with a world point, a screen point, a zoom factor and a second screen point or delta. */
    private data class Case(
        val viewport: Viewport,
        val world: Vec2,
        val screen: ScreenPoint,
        val factor: Double,
        val other: ScreenPoint,
    )

    private val cases: Arb<Case> =
        arbitrary { rs ->
            val width = rs.random.nextInt(0, MAX_SCREEN_PX + 1)
            val height = rs.random.nextInt(0, MAX_SCREEN_PX + 1)
            val viewport =
                Viewport(rs.worldPoint(), rs.logUniform(limits.minPixelsPerMm, limits.maxPixelsPerMm), width, height)
            Case(
                viewport = viewport,
                world = rs.worldPoint(),
                screen = rs.screenPoint(),
                factor = rs.logUniform(MIN_FACTOR, MAX_FACTOR),
                other = rs.screenPoint(),
            )
        }

    private fun RandomSource.worldPoint(): Vec2 = Vec2(signed(MAX_WORLD_MM), signed(MAX_WORLD_MM))

    private fun RandomSource.screenPoint(): ScreenPoint =
        ScreenPoint(
            signed(2.0 * MAX_SCREEN_PX),
            signed(2.0 * MAX_SCREEN_PX),
        )

    private fun RandomSource.signed(limit: Double): Double = (random.nextDouble() * 2.0 - 1.0) * limit

    private fun RandomSource.logUniform(
        min: Double,
        max: Double,
    ): Double = kotlin.math.exp(ln(min) + random.nextDouble() * (ln(max) - ln(min)))

    private companion object {
        const val ITERATIONS = 1_000
        const val MAX_SCREEN_PX = 4_000
        const val MAX_WORLD_MM = 1e6
        const val MIN_FACTOR = 1e-3
        const val MAX_FACTOR = 1e3
        const val DENSITY = 2.75
        const val MIN_FIT_SCREEN_PX = 100
        const val SEED_WORLD_ROUND_TRIP = 8_001L
        const val SEED_SCREEN_ROUND_TRIP = 8_002L
        const val SEED_ZOOM_FOCUS = 8_003L
        const val SEED_ZOOM_INVERSE = 8_004L
        const val SEED_PAN_COMPOSITION = 8_005L
        const val SEED_PAN_SHIFT = 8_006L
        const val SEED_FIT = 8_007L
        const val SEED_HIT_RADIUS = 8_008L

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)
    }
}
