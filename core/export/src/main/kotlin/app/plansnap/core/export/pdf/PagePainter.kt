package app.plansnap.core.export.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.TitleBlockSpec
import app.plansnap.core.drawing.plot.TitleTextAlign
import app.plansnap.core.drawing.plot.TitleTextSlot
import app.plansnap.core.drawing.plot.titleBlockLayout
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.render.LabelTextFormatter
import app.plansnap.core.render.SceneRenderer
import app.plansnap.core.render.SceneStyleSheet
import app.plansnap.core.render.argb

/** Points per millimeter on a page, `72 / 25.4`. */
internal const val POINTS_PER_MM: Double = 72.0 / 25.4

/**
 * Draws one page of a plan on a canvas whose unit is the PDF point (1 pt = 1/72 in) with its origin at the top left
 * page corner: background, the scenes through the shared [SceneRenderer] and the title block as resolved by
 * [TitleBlockSpec] (ADR 0009, ADR 0022). PDF and PNG both use it, PNG with a scaled canvas, so both show the same
 * geometry. Instances are not thread safe.
 */
internal class PagePainter(
    private val plotLayout: PlotLayout,
    private val texts: LabelTextFormatter,
) {
    private val renderer = SceneRenderer(texts)
    private val ink = SceneStyleSheet.EXPORT_PALETTE.argb(SceneStyleSheet.TITLE_BLOCK_INK)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            typeface = Typeface.SANS_SERIF
            color = ink
        }

    /** Draws the page of [plan]. [checkActive] runs before each room and may throw to abort. */
    fun paint(
        canvas: Canvas,
        plan: PlotPlan,
        rooms: List<RoomScene>,
        projectName: String,
        checkActive: () -> Unit,
    ) {
        area.color = SceneStyleSheet.PAGE_BACKGROUND_ARGB
        canvas.drawRect(0f, 0f, pt(plan.pageWidthMm.toDouble()), pt(plan.pageHeightMm.toDouble()), area)
        for (room in rooms) {
            checkActive()
            renderer.drawExport(canvas, listOf(room), plan, plotLayout)
        }
        titleBlock(canvas, plan, projectName)
    }

    private fun titleBlock(
        canvas: Canvas,
        plan: PlotPlan,
        projectName: String,
    ) {
        val layout = plan.titleBlockLayout
        val frame = layout.frame
        line.color = ink
        line.strokeWidth = pt(layout.strokeMm)
        canvas.drawRect(
            pt(frame.leftMm.toDouble()),
            pt(frame.topMm.toDouble()),
            pt(frame.rightMm.toDouble()),
            pt(frame.bottomMm.toDouble()),
            line,
        )
        drawText(
            canvas,
            ellipsize(projectName, layout.projectName, pt(layout.projectNameMaxWidthMm)),
            layout.projectName,
        )
        drawText(canvas, TitleBlockSpec.scaleText(plan.scale), layout.scaleLabel)
        val bar = layout.scaleBar
        val top = pt(bar.topMm)
        val bottom = pt(bar.topMm + bar.heightMm)
        for (segment in bar.segments) {
            val left = pt(segment.leftMm)
            val right = pt(segment.leftMm + segment.widthMm)
            area.color = if (segment.filled) ink else SceneStyleSheet.PAGE_BACKGROUND_ARGB
            canvas.drawRect(left, top, right, bottom, area)
            canvas.drawRect(left, top, right, bottom, line)
        }
        drawText(canvas, "0", bar.startLabel)
        drawText(canvas, texts.format(LabelText.Dimension(bar.nominalLengthMm.toDouble())), bar.endLabel)
    }

    private fun drawText(
        canvas: Canvas,
        content: String,
        slot: TitleTextSlot,
    ) {
        text.textSize = pt(slot.sizeMm)
        text.textAlign =
            when (slot.align) {
                TitleTextAlign.START -> Paint.Align.LEFT
                TitleTextAlign.CENTER -> Paint.Align.CENTER
                TitleTextAlign.END -> Paint.Align.RIGHT
            }
        canvas.drawText(content, pt(slot.xMm), pt(slot.baselineMm), text)
    }

    /** Shortens [name] with an ellipsis so that it fits [maxWidthPt] at the size of [slot]. */
    private fun ellipsize(
        name: String,
        slot: TitleTextSlot,
        maxWidthPt: Float,
    ): String {
        text.textSize = pt(slot.sizeMm)
        if (text.measureText(name) <= maxWidthPt) return name
        val available = maxWidthPt - text.measureText(ELLIPSIS)
        var count = if (available > 0f) text.breakText(name, true, available, null) else 0
        if (count > 0 && Character.isHighSurrogate(name[count - 1])) count--
        return name.substring(0, count).trimEnd() + ELLIPSIS
    }

    private fun pt(mm: Double): Float = (mm * POINTS_PER_MM).toFloat()

    private companion object {
        const val ELLIPSIS = "…"
    }
}
