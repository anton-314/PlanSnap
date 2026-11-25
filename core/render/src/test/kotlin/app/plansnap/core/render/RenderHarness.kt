package app.plansnap.core.render

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Vec2
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** Draws scenes with the editor renderer for tests. */
internal class RenderHarness(
    language: String = "de",
    unit: LengthUnit = LengthUnit.CENTIMETER,
) {
    private val locale = Locale.forLanguageTag(language)
    private val resources =
        RuntimeEnvironment
            .getApplication()
            .createConfigurationContext(Configuration().apply { setLocale(locale) })
            .resources
    val renderer = SceneRenderer(LabelTextFormatter(resources, locale, unit))

    fun editor(
        rooms: List<RoomScene>,
        size: Int = 200,
        pixelsPerMm: Double = 0.1,
        appearance: EditorAppearance = EditorAppearance(RenderPalette.Light, density = 1f, fontScale = 1f),
        background: Int = 0,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(background)
        val viewport = Viewport(Vec2(0.0, 0.0), pixelsPerMm, size, size)
        renderer.drawEditor(Canvas(bitmap), rooms, viewport, appearance)
        return bitmap
    }

    fun export(
        rooms: List<RoomScene>,
        plan: PlotPlan,
        pixelsPerPoint: Float = 2f,
    ): Bitmap {
        val width = (plan.pageWidthMm * POINTS_PER_MM * pixelsPerPoint).toInt()
        val height = (plan.pageHeightMm * POINTS_PER_MM * pixelsPerPoint).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        canvas.scale(pixelsPerPoint, pixelsPerPoint)
        renderer.drawExport(canvas, rooms, plan, DefaultPlotLayout())
        return bitmap
    }

    private companion object {
        const val POINTS_PER_MM = 72f / 25.4f
    }
}
