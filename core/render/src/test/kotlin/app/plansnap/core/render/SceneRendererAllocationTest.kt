package app.plansnap.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.ShapeStatus
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory

/**
 * Proves that the draw path allocates nothing per element: the heap bytes that the drawing thread allocates for a
 * frame must not grow with the number of rooms and elements. A constant per-frame share (coordinate mapping, JNI
 * glue of the canvas) is allowed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w200dp-h200dp-mdpi")
class SceneRendererAllocationTest {
    private val harness = RenderHarness()
    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val appearance = EditorAppearance(RenderPalette.Light, density = 1f, fontScale = 1f)
    private val viewport = Viewport(Vec2(0.0, 0.0), 0.05, 400, 400)

    private fun rooms(count: Int): List<RoomScene> =
        List(count) { TestScenes.room(ShapeStatus.entries[it % ShapeStatus.entries.size], withOpenings = true) }

    private fun bytesPerFrame(rooms: List<RoomScene>): Long {
        repeat(WARM_UP_FRAMES) {
            harness.renderer.drawEditor(canvas, rooms, viewport, appearance)
        }
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(MEASURED_FRAMES) {
            harness.renderer.drawEditor(canvas, rooms, viewport, appearance)
        }
        return (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_FRAMES
    }

    @Test
    fun `given ten times as many elements when drawing a frame then the allocated bytes do not grow`() {
        val small = rooms(SMALL_ROOMS)
        val large = rooms(LARGE_ROOMS)
        val elementsSmall = small.sumOf { it.elements.size }
        val elementsLarge = large.sumOf { it.elements.size }
        val smallBytes = bytesPerFrame(small)
        val largeBytes = bytesPerFrame(large)
        val growth = largeBytes - smallBytes
        assertTrue(
            "$smallBytes B for $elementsSmall elements, $largeBytes B for $elementsLarge elements, growth $growth B",
            growth <= ALLOWED_GROWTH_BYTES,
        )
    }

    private companion object {
        const val SMALL_ROOMS = 8
        const val LARGE_ROOMS = 80
        const val WARM_UP_FRAMES = 5
        const val MEASURED_FRAMES = 10
        const val ALLOWED_GROWTH_BYTES = 512L
    }
}
