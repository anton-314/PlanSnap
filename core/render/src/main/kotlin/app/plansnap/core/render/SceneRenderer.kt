package app.plansnap.core.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.RectF
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.drawing.viewport.ViewportTransform
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.min

/**
 * Appearance of the editor surface.
 *
 * @property palette plan colors, [RenderPalette.Light] or [RenderPalette.Dark].
 * @property density pixels per dp, which is also the line unit of the editor.
 * @property fontScale user font scale, applied to the 12 sp reference text size.
 */
data class EditorAppearance(
    val palette: RenderPalette,
    val density: Float,
    val fontScale: Float,
)

/**
 * Draws room scenes on an [android.graphics.Canvas]. The editor and every export (PDF, PNG) use this one renderer
 * (ADR 0008), so they show the same plan.
 *
 * All [Paint], [Path] and [RectF] objects are allocated once, and a draw call allocates nothing per element: world
 * points are mapped with plain arithmetic ([WorldMapping]) and strings come from the cache of [LabelTextFormatter].
 * The only allocations are per frame (the reference points of the coordinate mapping) and when the line unit changes
 * (dash patterns). Instances are not thread safe.
 *
 * Line weights are expressed in a line unit `u`: 1 dp in the editor and 0,18 mm on paper in exports. The rendering
 * of each [SceneStyle] is in [StyleLook].
 *
 * @property texts formatter of label text.
 * @property transform the conversion between world and screen of the editor (ARCHITECTURE section 8).
 */
class SceneRenderer(
    private val texts: LabelTextFormatter,
    private val transform: ViewportTransform = DefaultViewportTransform(),
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val oval = RectF()
    private val mapping = WorldMapping()

    private var palette: RenderPalette = RenderPalette.Light
    private var unit = 0f
    private var dashes: Array<PathEffect?> = emptyArray()
    private var editorTextReferencePx = 0f
    private var exportDenominator = 0
    private var roomReferencePx = 0f
    private var lineReferencePx = NO_REFERENCE

    /**
     * Draws [rooms] for the editor: world to screen through [transform], text in constant screen size, labels that do
     * not fit their element hidden (ADR 0008, CR-W2-04). Culling is the caller's job.
     */
    fun drawEditor(
        canvas: Canvas,
        rooms: List<RoomScene>,
        viewport: Viewport,
        appearance: EditorAppearance,
    ) {
        val center = viewport.centerWorld
        val origin = transform.worldToScreen(viewport, center)
        val step = transform.worldToScreen(viewport, Vec2(center.x + 1.0, center.y + 1.0))
        mapping.set(center, origin.x, origin.y, step.x, step.y)
        editorTextReferencePx = RenderTextRules.EDITOR_REFERENCE_SP * appearance.density * appearance.fontScale
        exportDenominator = 0
        drawRooms(canvas, rooms, appearance.palette, appearance.density)
    }

    /**
     * Draws [rooms] for a page of [plan] in page points, with the light palette (UX section 6) and the text factor of
     * DOMAIN section 12. No label is hidden. The caller sets up the page and the title block.
     */
    fun drawExport(
        canvas: Canvas,
        rooms: List<RoomScene>,
        plan: PlotPlan,
        layout: PlotLayout,
    ) {
        val bounds = plan.worldBounds
        val center = Vec2(bounds.minX + bounds.width / 2.0, bounds.minY + bounds.height / 2.0)
        val origin = layout.worldToPagePoints(plan, center)
        val step = layout.worldToPagePoints(plan, Vec2(center.x + 1.0, center.y + 1.0))
        mapping.set(center, origin.x, origin.y, step.x, step.y)
        exportDenominator = plan.scale.denominator
        drawRooms(canvas, rooms, RenderPalette.Light, EXPORT_UNIT_PT)
    }

    private fun drawRooms(
        canvas: Canvas,
        rooms: List<RoomScene>,
        palette: RenderPalette,
        lineUnit: Float,
    ) {
        this.palette = palette
        prepareUnit(lineUnit)
        for (r in rooms.indices) {
            val room = rooms[r]
            val bounds = room.bounds
            roomReferencePx =
                if (bounds == null) {
                    NO_REFERENCE
                } else {
                    min(bounds.width, bounds.height).toFloat() * mapping.unitsPerMm.toFloat()
                }
            lineReferencePx = NO_REFERENCE
            for (e in 0 until room.elements.size) draw(canvas, room.elements[e])
        }
    }

    private fun prepareUnit(lineUnit: Float) {
        if (lineUnit == unit) return
        unit = lineUnit
        dashes =
            Array(StyleLook.DASH_PATTERNS.size) { index ->
                StyleLook.DASH_PATTERNS[index]?.let {
                    DashPathEffect(
                        floatArrayOf(it.on * lineUnit, it.off * lineUnit),
                        0f,
                    )
                }
            }
    }

    private fun draw(
        canvas: Canvas,
        element: SceneElement,
    ) {
        when (element) {
            is SceneElement.Fill -> drawFill(canvas, element)
            is SceneElement.Polyline -> drawPolyline(canvas, element)
            is SceneElement.Arc -> drawArc(canvas, element)
            is SceneElement.Label -> drawLabel(canvas, element)
        }
    }

    private fun drawFill(
        canvas: Canvas,
        element: SceneElement.Fill,
    ) {
        val vertices = element.polygon.vertices
        path.rewind()
        for (i in vertices.indices) {
            val v = vertices[i]
            if (i == 0) path.moveTo(mapping.x(v.x), mapping.y(v.y)) else path.lineTo(mapping.x(v.x), mapping.y(v.y))
        }
        path.close()
        val fillColor = StyleLook.fillColor(element.style, palette)
        if (Color.alpha(fillColor) != 0) {
            fill.color = fillColor
            canvas.drawPath(path, fill)
        }
        if (StyleLook.outlined(element.style)) {
            applyStroke(element.style)
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawPolyline(
        canvas: Canvas,
        element: SceneElement.Polyline,
    ) {
        val points = element.points
        val style = element.style
        val first = points[0]
        if ((style == SceneStyle.DIMENSION || style == SceneStyle.DIAGONAL) && element.target != null) {
            lineReferencePx = mapping.length(first, points[points.size - 1])
        }
        applyStroke(style)
        if (points.size == 2 && first == points[1]) {
            stroke.strokeWidth = StyleLook.MARKER_UNITS * unit
            stroke.strokeCap = Paint.Cap.ROUND
            canvas.drawPoint(mapping.x(first.x), mapping.y(first.y), stroke)
            return
        }
        path.rewind()
        for (i in 0 until points.size) {
            val p = points[i]
            if (i == 0) path.moveTo(mapping.x(p.x), mapping.y(p.y)) else path.lineTo(mapping.x(p.x), mapping.y(p.y))
        }
        if (element.closed) path.close()
        canvas.drawPath(path, stroke)
    }

    private fun drawArc(
        canvas: Canvas,
        element: SceneElement.Arc,
    ) {
        applyStroke(element.style)
        val radius = (element.radiusMm * mapping.unitsPerMm).toFloat()
        val cx = mapping.x(element.center.x)
        val cy = mapping.y(element.center.y)
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val flip = if (mapping.mirrorsY) -1.0 else 1.0
        canvas.drawArc(
            oval,
            (Math.toDegrees(element.startRad) * flip).toFloat(),
            (Math.toDegrees(element.sweepRad) * flip).toFloat(),
            false,
            stroke,
        )
    }

    private fun drawLabel(
        canvas: Canvas,
        element: SceneElement.Label,
    ) {
        val content = texts.format(element.text)
        val inEditor = exportDenominator == 0
        textPaint.textSize =
            if (inEditor) {
                RenderTextRules.editorTextSizePx(element.heightMm, editorTextReferencePx)
            } else {
                RenderTextRules.exportTextSizePt(element.heightMm, exportDenominator)
            }
        val reference = if (element.text is LabelText.Dimension) lineReferencePx else roomReferencePx
        if (inEditor && RenderTextRules.isHidden(textPaint.measureText(content), reference)) return
        textPaint.color = StyleLook.textColor(element.style, palette)
        val flip = if (mapping.mirrorsY) -1.0 else 1.0
        val baseline = -(textPaint.ascent() + textPaint.descent()) / 2f
        canvas.save()
        canvas.translate(mapping.x(element.anchor.x), mapping.y(element.anchor.y))
        canvas.rotate((Math.toDegrees(element.rotationRad) * flip).toFloat())
        canvas.drawText(content, 0f, baseline, textPaint)
        canvas.restore()
    }

    private fun applyStroke(style: SceneStyle) {
        stroke.color = StyleLook.strokeColor(style, palette)
        stroke.strokeWidth = StyleLook.strokeUnits(style) * unit
        stroke.strokeCap = if (StyleLook.roundCap(style)) Paint.Cap.ROUND else Paint.Cap.BUTT
        stroke.pathEffect = dashes[StyleLook.dashIndex(style)]
    }

    private companion object {
        /** Line unit of exports: 0,18 mm in page points. */
        const val EXPORT_UNIT_PT = (0.18 * 72.0 / 25.4).toFloat()
        const val NO_REFERENCE = Float.POSITIVE_INFINITY
    }
}
