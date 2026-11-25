package app.plansnap.core.render

import android.graphics.Bitmap
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.model.ShapeStatus
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images of the renderer. Qualifiers are fixed so that device language and density cannot influence the
 * result; the harness picks the language explicitly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w200dp-h200dp-port-notnight-mdpi")
class SceneRendererScreenshotTest {
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))

    private fun editor(
        status: ShapeStatus,
        dark: Boolean,
        withOpenings: Boolean = false,
        language: String = "en",
    ): Bitmap {
        val palette = if (dark) RenderPalette.Dark else RenderPalette.Light
        return RenderHarness(language).editor(
            rooms = listOf(TestScenes.room(status, withOpenings)),
            size = EDITOR_SIZE,
            pixelsPerMm = 0.1,
            appearance = EditorAppearance(palette, density = 2f, fontScale = 1f),
            background = palette.openingFill,
        )
    }

    private fun capture(
        bitmap: Bitmap,
        name: String,
    ) = bitmap.captureRoboImage("$name.png", options)

    @Test
    fun `given each shape status when drawn in the light editor then it matches the baselines`() {
        capture(editor(ShapeStatus.CONFIRMED, dark = false), "editor-confirmed-light")
        capture(editor(ShapeStatus.NEEDS_ADJUSTMENT, dark = false), "editor-unconfirmed-light")
        capture(editor(ShapeStatus.OPEN, dark = false), "editor-open-light")
        capture(editor(ShapeStatus.CONFLICT, dark = false), "editor-conflict-light")
    }

    @Test
    fun `given each shape status when drawn in the dark editor then it matches the baselines`() {
        capture(editor(ShapeStatus.CONFIRMED, dark = true), "editor-confirmed-dark")
        capture(editor(ShapeStatus.NEEDS_ADJUSTMENT, dark = true), "editor-unconfirmed-dark")
        capture(editor(ShapeStatus.OPEN, dark = true), "editor-open-dark")
        capture(editor(ShapeStatus.CONFLICT, dark = true), "editor-conflict-dark")
    }

    @Test
    fun `given a room with doors and windows when drawn then it matches the baselines`() {
        capture(editor(ShapeStatus.CONFIRMED, dark = false, withOpenings = true), "editor-openings-light")
        capture(editor(ShapeStatus.CONFIRMED, dark = true, withOpenings = true), "editor-openings-dark")
    }

    @Test
    fun `given German when the room is unconfirmed or in conflict then the marks are German`() {
        capture(editor(ShapeStatus.NEEDS_ADJUSTMENT, dark = false, language = "de"), "editor-unconfirmed-de")
        capture(editor(ShapeStatus.CONFLICT, dark = false, language = "de"), "editor-conflict-de")
    }

    @Test
    fun `given the export when drawn at 1 to 50 and 1 to 200 then it matches the baselines`() {
        val room = TestScenes.room(ShapeStatus.CONFIRMED, withOpenings = true)
        val bounds = WorldBounds(-2400.0, -2400.0, 2400.0, 1700.0)
        val harness = RenderHarness("en")
        for ((scale, name) in listOf(PlotScale.ONE_TO_50 to "export-1-50", PlotScale.ONE_TO_200 to "export-1-200")) {
            val plan = PlotPlan(PaperSize.A4, PageOrientation.PORTRAIT, scale, bounds)
            capture(harness.export(listOf(room), plan), name)
        }
    }

    private companion object {
        const val EDITOR_SIZE = 500
    }
}
