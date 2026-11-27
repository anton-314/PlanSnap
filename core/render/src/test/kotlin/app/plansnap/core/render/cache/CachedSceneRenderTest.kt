package app.plansnap.core.render.cache

import android.graphics.Bitmap
import android.graphics.Canvas
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.cache.CachingSceneBuilder
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.core.render.EditorAppearance
import app.plansnap.core.render.RenderHarness
import app.plansnap.core.render.RenderPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory

/**
 * The render side of the scene cache and of room culling under the load of REQ-NFR-001. Times are only printed: they
 * come from the software canvas of the JVM and say nothing about the frame rate on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xxhdpi")
class CachedSceneRenderTest {
    private val transform = DefaultViewportTransform()
    private val appearance = EditorAppearance(RenderPalette.Light, DENSITY, fontScale = 1f)
    private val project = LargePlan.project()
    private val evaluations = LargePlan.evaluations(project)
    private val uncached = DefaultSceneBuilder().build(project, evaluations, SceneOptions())

    private class Counting(
        private val delegate: SceneBuilder = DefaultSceneBuilder(),
    ) : SceneBuilder by delegate {
        var builds = 0

        override fun buildRoom(
            room: Room,
            evaluation: RoomEvaluation,
            options: SceneOptions,
        ): RoomScene {
            builds++
            return delegate.buildRoom(room, evaluation, options)
        }
    }

    @Test
    fun `given a change of unit when cached scenes are drawn then nothing is rebuilt and the pixels match`() {
        val counting = Counting()
        val cache = CachingSceneBuilder(counting)
        cache.build(project, evaluations, SceneOptions())
        val viewport = viewport(Vec2(3_000.0, 2_000.0), CLOSE_PIXELS_PER_MM)

        val frames =
            listOf(LengthUnit.CENTIMETER, LengthUnit.METER).map { unit ->
                val harness = RenderHarness(unit = unit)
                val cached = cache.build(project, evaluations, SceneOptions())
                val expected = draw(harness, uncached.rooms, viewport)
                val actual = draw(harness, cached.rooms, viewport)
                assertTrue("$unit differs from the uncached scene", expected.sameAs(actual))
                actual
            }

        assertEquals(project.rooms.size, counting.builds)
        assertFalse("The unit must show in the drawn texts", frames[0].sameAs(frames[1]))
    }

    @Test
    fun `given any viewport when rooms outside it are culled then the frame is pixel identical`() {
        val harness = RenderHarness()
        var culled = 0
        for (pixelsPerMm in ZOOMS) {
            for (center in CENTERS) {
                val viewport = viewport(center, pixelsPerMm)
                val visible = visible(uncached.rooms, viewport)
                culled += uncached.rooms.size - visible.size

                val all = draw(harness, uncached.rooms, viewport)
                val some = draw(harness, visible, viewport)

                assertTrue("zoom $pixelsPerMm at $center with ${visible.size} rooms", all.sameAs(some))
            }
        }
        assertTrue("culled only $culled rooms", culled > uncached.rooms.size)
    }

    @Test
    fun `given 200 walls in 30 rooms when frames are drawn then time and allocation are reported`() {
        val harness = RenderHarness()
        val overview = transform.fit(viewport(Vec2.ZERO, 1.0), checkNotNull(uncached.bounds))
        val close = viewport(Vec2(8_000.0, 8_000.0), CLOSE_PIXELS_PER_MM)
        assertEquals(200, project.rooms.sumOf { it.walls.size })
        assertEquals(uncached.rooms.size, visible(uncached.rooms, overview).size)
        assertTrue(visible(uncached.rooms, close).size < uncached.rooms.size / 2)

        println("DRAW elements=${uncached.rooms.sumOf { it.elements.size }}")
        measure("overview all   ", harness, uncached.rooms, overview)
        measure("close all      ", harness, uncached.rooms, close)
        measure("close culled   ", harness, visible(uncached.rooms, close), close)
        measureCulling(close)
    }

    private fun viewport(
        center: Vec2,
        pixelsPerMm: Double,
    ) = Viewport(center, pixelsPerMm, WIDTH_PX, HEIGHT_PX)

    /** Room culling by bounds with the margin of 48 dp that the editor canvas uses. */
    private fun visible(
        rooms: List<RoomScene>,
        viewport: Viewport,
    ): List<RoomScene> {
        val area = transform.visibleBounds(viewport)
        val margin = MARGIN_DP * DENSITY / viewport.pixelsPerMm
        val widened = WorldBounds(area.minX - margin, area.minY - margin, area.maxX + margin, area.maxY + margin)
        return rooms.filter { it.bounds?.intersects(widened) == true }
    }

    private fun draw(
        harness: RenderHarness,
        rooms: List<RoomScene>,
        viewport: Viewport,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        harness.renderer.drawEditor(Canvas(bitmap), rooms, viewport, appearance)
        return bitmap
    }

    private fun measure(
        name: String,
        harness: RenderHarness,
        rooms: List<RoomScene>,
        viewport: Viewport,
    ) {
        val canvas = Canvas(Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888))
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        repeat(WARM_UP) { harness.renderer.drawEditor(canvas, rooms, viewport, appearance) }
        val times = LongArray(FRAMES)
        val before = threads.currentThreadAllocatedBytes
        for (i in times.indices) {
            val start = System.nanoTime()
            harness.renderer.drawEditor(canvas, rooms, viewport, appearance)
            times[i] = System.nanoTime() - start
        }
        val bytes = (threads.currentThreadAllocatedBytes - before) / FRAMES
        times.sort()
        println(
            "DRAW $name rooms=${rooms.size} medianUs=${times[FRAMES / 2] / 1000} " +
                "p95Us=${times[FRAMES * 95 / 100] / 1000} bytes=$bytes",
        )
    }

    private fun measureCulling(viewport: Viewport) {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        repeat(CULL_RUNS) { visible(uncached.rooms, viewport) }
        val before = threads.currentThreadAllocatedBytes
        val start = System.nanoTime()
        repeat(CULL_RUNS) { visible(uncached.rooms, viewport) }
        val nanos = (System.nanoTime() - start) / CULL_RUNS
        val bytes = (threads.currentThreadAllocatedBytes - before) / CULL_RUNS
        println("DRAW culling meanNs=$nanos bytes=$bytes")
    }

    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 2000
        const val DENSITY = 3f
        const val MARGIN_DP = 48.0
        const val CLOSE_PIXELS_PER_MM = 0.2
        const val WARM_UP = 30
        const val FRAMES = 60
        const val CULL_RUNS = 10_000
        val ZOOMS = listOf(0.01, 0.04, 0.2, 0.8)
        val CENTERS =
            listOf(Vec2(0.0, 0.0), Vec2(8_000.0, 8_000.0), Vec2(26_000.0, 33_000.0), Vec2(-4_000.0, 20_000.0))
    }
}
